#!/usr/bin/env python3
"""
Unit tests for EnglishFlow AWS Polly Offline Synthesis Adapter (synthesize_polly.py).

Verifies:
- Default dry-run produces zero API calls and zero output files.
- Exact canonical transcripts are passed unchanged.
- Request parameters (VoiceId, Engine, OutputFormat, SampleRate) match specifications.
- Character budget limits are strictly enforced.
- Invalid content IDs and missing voice configs are rejected.
- Paid API safety gate blocks unconfirmed execution.
- Duplicate files are protected against accidental overwrite (idempotency).
- Mocked execution writes valid MP3, verifies SHA-256, and builds provisional unapproved dossier.
- Malformed or empty audio streams fail closed and clean up temp files.
- API errors are handled safely.
- Secrets and credentials are scrubbed from all logs and error streams.
"""

import hashlib
import io
import os
import shutil
import struct
import tempfile
import unittest
from unittest.mock import MagicMock

import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../adapters")))

from adapters.synthesize_polly import (
    PollySynthesizer,
    CONFIRMATION_TOKEN,
    DEFAULT_CATALOG_PATH,
    DEFAULT_VOICE_CONFIG_PATH,
    PILOT_CONTENT_ID,
    estimate_cost_usd,
    sanitize_text,
)


def make_synthetic_24k_mono_mp3(num_frames: int = 15) -> bytes:
    """Generates synthetic valid MPEG-2 Layer III 24 kHz mono frames for testing."""
    b0 = 0xFF
    b1 = 0xF3  # MPEG 2.0, Layer III, no CRC
    # 64 kbps (index 8), 24 kHz (index 1), no padding
    b2 = (0x8 << 4) | (0x1 << 2)
    b3 = 0xC0  # Mono
    frame_size = int(72 * 64 * 1000 // 24000)
    header = bytes([b0, b1, b2, b3])
    payload = b"\xAA" * (frame_size - 4)
    return (header + payload) * num_frames


class TestPollySynthesizerDryRun(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="engflow_polly_dryrun_")

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_default_dry_run_produces_zero_api_calls_and_no_files(self):
        """Verifies dry-run never creates a client, touches network, or creates files."""
        mock_factory = MagicMock()
        synthesizer = PollySynthesizer(
            catalog_path=DEFAULT_CATALOG_PATH,
            voice_config_path=DEFAULT_VOICE_CONFIG_PATH,
            output_dir=self.test_dir,
            client_factory=mock_factory,
        )

        preview = synthesizer.dry_run(PILOT_CONTENT_ID)
        self.assertEqual(preview["contentId"], PILOT_CONTENT_ID)
        self.assertEqual(preview["mode"], "DRY_RUN")
        self.assertFalse(preview["awsClientCreated"])
        self.assertEqual(preview["networkCallsMade"], 0)
        self.assertEqual(preview["status"], "VALIDATED_READY_FOR_AUTHORIZATION")

        # Mock client factory must NEVER be called
        mock_factory.assert_not_called()

        # Output directory must remain completely empty
        self.assertEqual(len(os.listdir(self.test_dir)), 0)

    def test_exact_transcript_passed_unchanged(self):
        """Verifies canonical transcript text is preserved byte-for-byte in request."""
        synthesizer = PollySynthesizer(output_dir=self.test_dir)
        exercise = synthesizer.get_exercise(PILOT_CONTENT_ID)
        preview = synthesizer.dry_run(PILOT_CONTENT_ID)

        self.assertEqual(preview["canonicalTranscript"], exercise["transcriptText"])
        self.assertEqual(preview["requestPayload"]["Text"], exercise["transcriptText"])
        self.assertEqual(preview["characterCount"], len(exercise["transcriptText"]))
        self.assertEqual(preview["characterCount"], 50)

    def test_correct_request_parameters_generated(self):
        """Verifies Polly request parameters match specification."""
        synthesizer = PollySynthesizer(output_dir=self.test_dir)
        preview = synthesizer.dry_run(PILOT_CONTENT_ID)

        payload = preview["requestPayload"]
        self.assertEqual(payload["VoiceId"], "Joanna")
        self.assertEqual(payload["Engine"], "neural")
        self.assertEqual(payload["OutputFormat"], "mp3")
        self.assertEqual(payload["SampleRate"], "24000")
        self.assertEqual(payload["TextType"], "text")

    def test_character_budget_enforced(self):
        """Verifies requests exceeding max_chars budget are rejected before dispatch."""
        synthesizer = PollySynthesizer(output_dir=self.test_dir)
        with self.assertRaises(ValueError) as ctx:
            synthesizer.dry_run(PILOT_CONTENT_ID, max_chars=30)
        self.assertIn("Preflight budget violation", str(ctx.exception))

    def test_invalid_content_id_rejected(self):
        """Verifies unknown UUID raises ValueError."""
        synthesizer = PollySynthesizer(output_dir=self.test_dir)
        with self.assertRaises(ValueError) as ctx:
            synthesizer.dry_run("00000000-0000-0000-0000-000000000000")
        self.assertIn("not found in canonical catalog", str(ctx.exception))

    def test_strategy_b_multi_accent_selection(self):
        """Verifies Strategy B voice selection (e.g. Amy en-GB for A2 Dictation)."""
        synthesizer = PollySynthesizer(
            strategy="STRATEGY_B_MULTI_ACCENT",
            output_dir=self.test_dir,
        )
        preview = synthesizer.dry_run("a2000000-0000-0000-0000-000000000002")
        self.assertEqual(preview["voiceId"], "Amy")
        self.assertEqual(preview["accent"], "en-GB")
        self.assertEqual(preview["engine"], "neural")


class TestPollySynthesizerExecutionAndMocking(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="engflow_polly_exec_")
        self.mock_client = MagicMock()
        self.client_factory = lambda: self.mock_client

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_safety_gate_blocks_without_confirmation_token(self):
        """Verifies execute=True without confirmation token raises PermissionError."""
        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )
        with self.assertRaises(PermissionError) as ctx:
            synthesizer.synthesize(PILOT_CONTENT_ID, execute=True, confirmation_token=None)
        self.assertIn("PAID API SAFETY GATE", str(ctx.exception))
        self.mock_client.synthesize_speech.assert_not_called()

    def test_safety_gate_blocks_with_invalid_confirmation_token(self):
        """Verifies execute=True with incorrect confirmation token raises PermissionError."""
        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )
        with self.assertRaises(PermissionError) as ctx:
            synthesizer.synthesize(PILOT_CONTENT_ID, execute=True, confirmation_token="INVALID_TOKEN")
        self.assertIn("PAID API SAFETY GATE", str(ctx.exception))
        self.mock_client.synthesize_speech.assert_not_called()

    def test_idempotency_prevents_accidental_overwrite(self):
        """Verifies existing target file is not overwritten unless force_overwrite=True."""
        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )
        target_path = os.path.join(self.test_dir, "dictation-a1-001.mp3")
        with open(target_path, "wb") as f:
            f.write(b"EXISTING_DATA")

        with self.assertRaises(FileExistsError) as ctx:
            synthesizer.synthesize(
                PILOT_CONTENT_ID,
                execute=True,
                confirmation_token=CONFIRMATION_TOKEN,
                force_overwrite=False,
            )
        self.assertIn("already exists", str(ctx.exception))
        self.mock_client.synthesize_speech.assert_not_called()

    def test_successful_mocked_synthesis_workflow(self):
        """Verifies full execution pipeline with mocked valid MP3 response."""
        mp3_bytes = make_synthetic_24k_mono_mp3(num_frames=20)
        self.mock_client.synthesize_speech.return_value = {
            "AudioStream": io.BytesIO(mp3_bytes),
            "ContentType": "audio/mpeg",
        }

        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )

        result = synthesizer.synthesize(
            PILOT_CONTENT_ID,
            execute=True,
            confirmation_token=CONFIRMATION_TOKEN,
        )

        self.assertEqual(result["contentId"], PILOT_CONTENT_ID)
        self.assertEqual(result["mode"], "EXECUTED")
        self.assertFalse(result["distributionApproved"])
        self.assertEqual(result["fileSizeBytes"], len(mp3_bytes))
        self.assertEqual(result["audioSha256"], hashlib.sha256(mp3_bytes).hexdigest())

        # Verify target file was written to disk and has identical bytes
        target_path = result["targetFilePath"]
        self.assertTrue(os.path.isfile(target_path))
        with open(target_path, "rb") as f:
            written_bytes = f.read()
        self.assertEqual(written_bytes, mp3_bytes)

        # Verify provisional dossier keeps zero unverified approvals
        dossier = result["provisionalDossier"]
        self.assertFalse(dossier["distributionApproved"])
        self.assertFalse(dossier["humanQaVerified"])
        self.assertEqual(dossier["licenseReviewStatus"], "PENDING")
        self.assertEqual(dossier["humanSpeechQaDecision"], "PENDING")
        self.assertEqual(dossier["audioReadinessStatus"], "PENDING_LICENSE")
        self.assertEqual(dossier["authorizedDistributionScope"], "UNAUTHORIZED")

    def test_provisional_dossier_strictly_validates_against_schema(self):
        """Verifies synthesized provisional metadata strictly conforms to dossier.schema.json."""
        mp3_bytes = make_synthetic_24k_mono_mp3(num_frames=5)
        self.mock_client.synthesize_speech.return_value = {
            "AudioStream": io.BytesIO(mp3_bytes),
            "ContentType": "audio/mpeg",
        }
        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )

        result = synthesizer.synthesize(
            PILOT_CONTENT_ID,
            execute=True,
            confirmation_token=CONFIRMATION_TOKEN,
        )

        dossier = result["provisionalDossier"]
        schema_path = os.path.join(os.path.dirname(__file__), "../../docs/licensing/dossier.schema.json")
        self.assertTrue(os.path.isfile(schema_path))
        import json
        import jsonschema
        with open(schema_path, "r", encoding="utf-8") as sf:
            schema = json.load(sf)
        # Must not raise jsonschema.ValidationError
        jsonschema.validate(instance=dossier, schema=schema)

    def test_empty_audio_stream_rejected_and_cleaned_up(self):
        """Verifies 0-byte stream is rejected and no partial files linger."""
        self.mock_client.synthesize_speech.return_value = {
            "AudioStream": io.BytesIO(b""),
            "ContentType": "audio/mpeg",
        }
        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )

        with self.assertRaises(ValueError) as ctx:
            synthesizer.synthesize(
                PILOT_CONTENT_ID,
                execute=True,
                confirmation_token=CONFIRMATION_TOKEN,
            )
        self.assertIn("empty", str(ctx.exception).lower())
        self.assertEqual(len(os.listdir(self.test_dir)), 0)

    def test_malformed_mp3_rejected_and_cleaned_up(self):
        """Verifies non-MP3 garbage bytes fail media inspection and are cleaned up."""
        garbage_bytes = b"CORRUPTED_NOT_MP3_DATA_STREAM"
        self.mock_client.synthesize_speech.return_value = {
            "AudioStream": io.BytesIO(garbage_bytes),
            "ContentType": "audio/mpeg",
        }
        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )

        with self.assertRaises(Exception):
            synthesizer.synthesize(
                PILOT_CONTENT_ID,
                execute=True,
                confirmation_token=CONFIRMATION_TOKEN,
            )
        # Target file must not exist
        target_path = os.path.join(self.test_dir, "dictation-a1-001.mp3")
        self.assertFalse(os.path.exists(target_path))

    def test_api_client_error_handled_safely(self):
        """Verifies AWS client exceptions are safely caught and wrapped."""
        self.mock_client.synthesize_speech.side_effect = Exception("AWS ServiceThrottlingException")
        synthesizer = PollySynthesizer(
            output_dir=self.test_dir,
            client_factory=self.client_factory,
        )

        with self.assertRaises(RuntimeError) as ctx:
            synthesizer.synthesize(
                PILOT_CONTENT_ID,
                execute=True,
                confirmation_token=CONFIRMATION_TOKEN,
            )
        self.assertIn("AWS Polly SynthesizeSpeech failed", str(ctx.exception))
        self.assertEqual(len(os.listdir(self.test_dir)), 0)

    def test_secrets_scrubbed_from_error_messages(self):
        """Verifies sensitive AWS credentials in error strings are redacted."""
        leak_msg = "Error with AKIAIOSFODNN7EXAMPLE and secret=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        sanitized = sanitize_text(leak_msg)
        self.assertNotIn("AKIAIOSFODNN7EXAMPLE", sanitized)
        self.assertIn("[REDACTED_AWS_ACCESS_KEY]", sanitized)


if __name__ == "__main__":
    unittest.main()
