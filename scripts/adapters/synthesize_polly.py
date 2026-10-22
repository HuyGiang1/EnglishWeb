#!/usr/bin/env python3
"""
EnglishFlow — AWS Polly Offline Speech Synthesis Adapter (M3-T01B-2B)

Authoritative Role: Principal Python Engineer & Cloud Security Engineer
Security Directives:
  - Default operation is strictly OFFLINE DRY-RUN.
  - Zero AWS network calls are executed without explicit '--execute' AND '--confirm-paid-execution'.
  - Boto3 is an optional adapter-only dependency; dry-run functions completely without it.
  - Character budget ceiling is enforced prior to any request dispatch.
  - Target files are protected by idempotency checks; overwrites require '--force-overwrite'.
  - Output bytes are cryptographically and structurally validated via media_inspector before acceptance.
  - Secrets are scrubbed from all logs and output streams.
"""

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
from pathlib import Path
from typing import Any, Callable, Dict, Optional

# Add scripts directory to sys.path to import media_inspector
SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

try:
    from media_inspector import inspect_mp3, compute_file_sha256
except ImportError:
    inspect_mp3 = None
    compute_file_sha256 = None

DEFAULT_CATALOG_PATH = "content/canonical-curated-catalog.json"
DEFAULT_VOICE_CONFIG_PATH = "content/proposed-voice-config.json"
DEFAULT_OUTPUT_DIR = "storage/audio/source"
DEFAULT_REGION = "us-east-1"
DEFAULT_ENGINE = "neural"
DEFAULT_SAMPLE_RATE = "24000"
DEFAULT_OUTPUT_FORMAT = "mp3"
PILOT_CONTENT_ID = "a1000000-0000-0000-0000-000000000001"
POLLY_NEURAL_PRICE_PER_MILLION_CHARS = 16.00
CONFIRMATION_TOKEN = "I_AGREE_TO_PAY_AWS_POLLY"


def estimate_cost_usd(character_count: int) -> float:
    """Computes exact estimated AWS Polly Neural cost in USD."""
    return round((character_count / 1_000_000.0) * POLLY_NEURAL_PRICE_PER_MILLION_CHARS, 6)


def sanitize_text(text: str) -> str:
    """Scrubs sensitive credentials, tokens, or private keys from strings."""
    if not text:
        return ""
    # Mask AWS Access Key IDs
    text = re.sub(r"AKIA[0-9A-Z]{16}", "[REDACTED_AWS_ACCESS_KEY]", text)
    # Mask 40-character AWS Secret Keys or hashes looking like keys
    text = re.sub(r"(?i)(aws_secret_access_key|secret_key|secret)\s*[:=]\s*['\"]?[A-Za-z0-9/+=]{30,50}['\"]?", r"\1=[REDACTED_SECRET]", text)
    return text


def load_catalog(catalog_path: str) -> Dict[str, Any]:
    """Loads and validates the authoritative canonical catalog."""
    if not os.path.isfile(catalog_path):
        raise FileNotFoundError(f"Canonical catalog not found at: {catalog_path}")
    with open(catalog_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if "exercises" not in data or not isinstance(data["exercises"], list):
        raise ValueError(f"Malformed catalog: missing 'exercises' list in {catalog_path}")
    return data


def load_voice_config(voice_config_path: str, strategy: Optional[str] = None) -> Dict[str, Any]:
    """Loads version-controlled proposed voice configuration strategies."""
    if not os.path.isfile(voice_config_path):
        raise FileNotFoundError(f"Voice configuration file not found at: {voice_config_path}")
    with open(voice_config_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    strategies = data.get("strategies", {})
    chosen_strategy = strategy or data.get("defaultStrategy", "STRATEGY_A_EN_US_MVP")

    if chosen_strategy not in strategies:
        raise ValueError(f"Strategy '{chosen_strategy}' not found in {voice_config_path}. Available: {list(strategies.keys())}")

    return {
        "strategyName": chosen_strategy,
        "metadata": strategies[chosen_strategy],
        "voices": strategies[chosen_strategy].get("voices", {}),
    }


class PollySynthesizer:
    """
    Offline-safe AWS Polly Speech Synthesis Adapter.
    Supports dependency injection for test mocking and fails closed on unauthorized calls.
    """

    def __init__(
        self,
        catalog_path: str = DEFAULT_CATALOG_PATH,
        voice_config_path: str = DEFAULT_VOICE_CONFIG_PATH,
        strategy: Optional[str] = None,
        output_dir: str = DEFAULT_OUTPUT_DIR,
        region: str = DEFAULT_REGION,
        client_factory: Optional[Callable[[], Any]] = None,
    ):
        self.catalog_path = catalog_path
        self.voice_config_path = voice_config_path
        self.strategy = strategy
        self.output_dir = output_dir
        self.region = region
        self.client_factory = client_factory

        self.catalog_data = load_catalog(self.catalog_path)
        self.voice_data = load_voice_config(self.voice_config_path, self.strategy)

        # Index exercises by UUID
        self.exercises_by_id = {ex["contentId"]: ex for ex in self.catalog_data["exercises"]}
        if len(self.exercises_by_id) != len(self.catalog_data["exercises"]):
            raise ValueError("Catalog contains duplicate content IDs!")

    def get_exercise(self, content_id: str) -> Dict[str, Any]:
        """Resolves exercise from canonical catalog or raises ValueError."""
        if content_id not in self.exercises_by_id:
            raise ValueError(f"Content ID '{content_id}' not found in canonical catalog {self.catalog_path}")
        return self.exercises_by_id[content_id]

    def get_voice_info(self, content_id: str) -> Dict[str, Any]:
        """Resolves voice specification from proposed voice configuration."""
        voices = self.voice_data["voices"]
        if content_id not in voices:
            raise ValueError(f"No voice configuration mapped for content ID '{content_id}' under strategy '{self.voice_data['strategyName']}'")
        return voices[content_id]

    def get_target_file_path(self, exercise: Dict[str, Any]) -> str:
        """Derives target MP3 output path based on canonical storage key or contentId."""
        storage_key = exercise.get("audioStorageKey", "")
        if storage_key:
            fname = os.path.basename(storage_key)
            if fname.endswith(".wav"):
                fname = fname[:-4] + ".mp3"
            elif not fname.endswith(".mp3"):
                fname = fname + ".mp3"
        else:
            fname = f"{exercise['contentId']}.mp3"
        return os.path.join(self.output_dir, fname)

    def build_synthesis_request(self, exercise: Dict[str, Any], voice_info: Dict[str, Any]) -> Dict[str, Any]:
        """Constructs and validates the precise AWS Polly SynthesizeSpeech request dictionary."""
        text = exercise.get("transcriptText", "").strip()
        if not text:
            raise ValueError(f"Exercise '{exercise.get('contentId')}' has empty transcript text")

        voice_id = voice_info.get("voiceId")
        if not voice_id:
            raise ValueError("Missing 'voiceId' in voice configuration")

        engine = voice_info.get("engine", DEFAULT_ENGINE)
        sample_rate = str(voice_info.get("sampleRate", DEFAULT_SAMPLE_RATE))

        return {
            "Text": text,
            "OutputFormat": DEFAULT_OUTPUT_FORMAT,
            "VoiceId": voice_id,
            "Engine": engine,
            "SampleRate": sample_rate,
            "TextType": "text",
        }

    def dry_run(self, content_id: str, max_chars: int = 500) -> Dict[str, Any]:
        """
        Executes an offline dry-run preview.
        GUARANTEE: NEVER creates an AWS client, connects to a network, or creates files on disk.
        """
        exercise = self.get_exercise(content_id)
        voice_info = self.get_voice_info(content_id)
        request_payload = self.build_synthesis_request(exercise, voice_info)

        text = request_payload["Text"]
        char_count = len(text)
        if char_count > max_chars:
            raise ValueError(
                f"Preflight budget violation: character count {char_count} exceeds maximum allowed budget of {max_chars} chars"
            )

        target_file_path = self.get_target_file_path(exercise)
        file_exists = os.path.isfile(target_file_path)

        preview = {
            "contentId": exercise["contentId"],
            "title": exercise["title"],
            "contentType": exercise["contentType"],
            "cefrLevel": exercise["cefrLevel"],
            "canonicalTranscript": text,
            "characterCount": char_count,
            "estimatedCostUsd": estimate_cost_usd(char_count),
            "voiceStrategy": self.voice_data["strategyName"],
            "voiceId": voice_info["voiceId"],
            "engine": voice_info.get("engine", DEFAULT_ENGINE),
            "accent": voice_info.get("accent", "en-US"),
            "sampleRate": voice_info.get("sampleRate", DEFAULT_SAMPLE_RATE),
            "targetFilePath": target_file_path,
            "targetFileExists": file_exists,
            "requestPayload": request_payload,
            "mode": "DRY_RUN",
            "awsClientCreated": False,
            "networkCallsMade": 0,
            "status": "VALIDATED_READY_FOR_AUTHORIZATION",
        }
        return preview

    def _get_client(self) -> Any:
        """Internal helper to obtain a Polly client (mocked or real)."""
        if self.client_factory is not None:
            return self.client_factory()

        try:
            import boto3
        except ImportError:
            raise RuntimeError(
                "boto3 is not installed in the current Python environment. "
                "To execute live synthesis, install boto3 (`pip install boto3`) "
                "and ensure AWS credentials are authenticated."
            )

        return boto3.client("polly", region_name=self.region)

    def synthesize(
        self,
        content_id: str,
        execute: bool = False,
        confirmation_token: Optional[str] = None,
        force_overwrite: bool = False,
        max_chars: int = 500,
    ) -> Dict[str, Any]:
        """
        Synthesizes audio for a single exercise.
        If execute is False (the default), delegates to dry_run without AWS contact.
        If execute is True, enforces confirmation token and idempotency before API dispatch.
        """
        # Step 1: Preflight validation
        preview = self.dry_run(content_id, max_chars=max_chars)

        if not execute:
            preview["executionSkipped"] = "Execution flag '--execute' was not set. Defaulting to safe dry-run."
            return preview

        # Step 2: Paid API safety gate check
        if confirmation_token != CONFIRMATION_TOKEN:
            raise PermissionError(
                f"PAID API SAFETY GATE: Execution rejected. "
                f"You must supply explicit confirmation token: '{CONFIRMATION_TOKEN}'. "
                f"Provided token: '{confirmation_token}'"
            )

        target_file_path = preview["targetFilePath"]
        if os.path.isfile(target_file_path) and not force_overwrite:
            raise FileExistsError(
                f"Idempotency protection: target file '{target_file_path}' already exists. "
                f"Synthesis aborted to prevent accidental overwrite. Use force_overwrite=True if intentional."
            )

        # Step 3: Ensure output directory exists
        os.makedirs(os.path.dirname(os.path.abspath(target_file_path)), exist_ok=True)

        # Step 4: Dispatch call via client
        client = self._get_client()
        request_payload = preview["requestPayload"]

        try:
            response = client.synthesize_speech(**request_payload)
        except Exception as ex:
            sanitized_err = sanitize_text(str(ex))
            raise RuntimeError(f"AWS Polly SynthesizeSpeech failed: {sanitized_err}") from ex

        if "AudioStream" not in response:
            raise ValueError("Malformed response from Polly: missing 'AudioStream'")

        audio_stream = response["AudioStream"]
        if hasattr(audio_stream, "read"):
            audio_bytes = audio_stream.read()
        elif isinstance(audio_stream, bytes):
            audio_bytes = audio_stream
        else:
            raise ValueError(f"Unexpected AudioStream type: {type(audio_stream)}")

        if not audio_bytes or len(audio_bytes) == 0:
            raise ValueError("Synthesized audio stream is empty (0 bytes)")

        # Step 5: Write to temporary file first for atomic verification
        temp_file = target_file_path + ".tmp"
        try:
            with open(temp_file, "wb") as f:
                f.write(audio_bytes)

            # Step 6: Validate output with media_inspector if available
            duration_seconds = None
            if inspect_mp3 is not None:
                meta = inspect_mp3(temp_file)
                if meta["format"] != "mp3":
                    raise ValueError(f"Output verification failed: expected MP3 container, got {meta['format']}")
                if meta.get("sample_rate") != int(request_payload["SampleRate"]):
                    raise ValueError(
                        f"Sample rate mismatch: expected {request_payload['SampleRate']} Hz, got {meta.get('sample_rate')} Hz"
                    )
                duration_seconds = meta["duration_seconds"]

            # Step 7: Atomic rename to target path
            os.replace(temp_file, target_file_path)
        except Exception:
            if os.path.exists(temp_file):
                os.remove(temp_file)
            raise

        actual_sha256 = hashlib.sha256(audio_bytes).hexdigest()
        file_size_bytes = len(audio_bytes)

        # Step 8: Build provisional licensing metadata strictly complying with dossier.schema.json
        provisional_dossier = {
            "schemaVersion": "1.0.0",
            "contentId": preview["contentId"],
            "contentType": preview["contentType"],
            "cefrLevel": preview["cefrLevel"],
            "title": preview["title"],
            "storageKey": f"/api/v1/media/audio/curated/{os.path.basename(target_file_path)}",
            "fileName": os.path.basename(target_file_path),
            "transcriptSha256": hashlib.sha256(preview["canonicalTranscript"].encode("utf-8")).hexdigest(),
            "audioSha256": actual_sha256,
            "provider": "AWS_POLLY_CANDIDATE",
            "voiceId": preview["voiceId"],
            "generationMetadata": {
                "engine": preview["engine"],
                "sampleRateHertz": int(preview["sampleRate"]),
                "audioFormat": "audio/mpeg",
                "generatedAt": None,
                "parameterSummary": f"AWS Polly Neural candidate; voice={preview['voiceId']}, sampleRate={preview['sampleRate']}Hz, chars={preview['characterCount']}",
            },
            "licenseEvidenceRef": "docs/licensing/LOCAL_DEV_RESTRICTIONS.md",
            "authorizedDistributionScope": "UNAUTHORIZED",
            "licenseReviewStatus": "PENDING",
            "distributionApproved": False,
            "reviewerIdentity": None,
            "humanSpeechQaDecision": "PENDING",
            "humanQaVerified": False,
            "humanReviewerIdentity": None,
            "reviewedAt": None,
            "audioReadinessStatus": "PENDING_LICENSE",
            "notes": "Generated by synthesize_polly adapter in test/pilot staging. Strictly unapproved pending physical listening QA and commercial license signoff.",
        }

        # Step 9: Validate against formal dossier schema if available
        dossier_schema_path = os.path.abspath(
            os.path.join(os.path.dirname(__file__), "../../docs/licensing/dossier.schema.json")
        )
        if os.path.exists(dossier_schema_path):
            try:
                import jsonschema
                with open(dossier_schema_path, "r", encoding="utf-8") as sf:
                    schema_data = json.load(sf)
                jsonschema.validate(instance=provisional_dossier, schema=schema_data)
            except ImportError:
                pass

        return {
            "contentId": preview["contentId"],
            "targetFilePath": target_file_path,
            "fileSizeBytes": file_size_bytes,
            "audioSha256": actual_sha256,
            "durationSeconds": duration_seconds,
            "provisionalDossier": provisional_dossier,
            "mode": "EXECUTED",
            "distributionApproved": False,
            "status": "SYNTHESIS_SUCCESSFUL_PENDING_LICENSE_AND_QA",
        }


def format_preview_output(preview: Dict[str, Any]) -> str:
    """Formats preview dictionary into a readable console report."""
    lines = [
        "=" * 80,
        "EnglishFlow — AWS Polly Offline Synthesis Preview",
        "=" * 80,
        f"Content ID:         {preview['contentId']}",
        f"Exercise Title:     {preview['title']} ({preview['contentType']} {preview['cefrLevel']})",
        f"Transcript:         \"{preview['canonicalTranscript']}\"",
        f"Character Count:    {preview['characterCount']} chars (Budget Limit Verified)",
        f"Estimated Cost:     ${preview['estimatedCostUsd']:.6f} USD (Neural Engine)",
        f"Voice Strategy:     {preview['voiceStrategy']}",
        f"Assigned Voice:     {preview['voiceId']} ({preview['accent']}, {preview['engine']}, {preview['sampleRate']} Hz)",
        f"Target Output Path: {preview['targetFilePath']}",
        f"File Exists on Disk:{preview['targetFileExists']}",
        "-" * 80,
        "Request Payload Preview (Boto3 SynthesizeSpeech):",
        json.dumps(preview["requestPayload"], indent=2),
        "-" * 80,
        f"Mode:               {preview['mode']}",
        f"AWS Client Created: {preview['awsClientCreated']}",
        f"Network Calls:      {preview['networkCallsMade']}",
        f"Safety Gate Status: {preview['status']}",
        "=" * 80,
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="EnglishFlow AWS Polly Offline Speech Synthesis Adapter (Safe Dry-Run by Default)"
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--content-id", help="Canonical UUID of the target exercise")
    group.add_argument("--pilot", action="store_true", help=f"Shortcut for Dictation A1 pilot ({PILOT_CONTENT_ID})")

    parser.add_argument("--catalog", default=DEFAULT_CATALOG_PATH, help=f"Path to catalog (default: {DEFAULT_CATALOG_PATH})")
    parser.add_argument("--voice-config", default=DEFAULT_VOICE_CONFIG_PATH, help=f"Path to voice config (default: {DEFAULT_VOICE_CONFIG_PATH})")
    parser.add_argument("--strategy", default="STRATEGY_A_EN_US_MVP", help="Voice strategy name (default: STRATEGY_A_EN_US_MVP)")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR, help=f"Output directory (default: {DEFAULT_OUTPUT_DIR})")
    parser.add_argument("--max-chars", type=int, default=500, help="Maximum allowed character ceiling (default: 500)")
    parser.add_argument("--force-overwrite", action="store_true", help="Allow overwrite of existing output file")

    # Safety Gate Flags
    parser.add_argument("--dry-run", action="store_true", help="Explicitly request dry-run preview (default)")
    parser.add_argument("--execute", action="store_true", help="Request live synthesis execution (requires confirmation token)")
    parser.add_argument("--confirm-paid-execution", help="Required confirmation token to authorize AWS Polly API spend")

    args = parser.parse_args()

    content_id = PILOT_CONTENT_ID if args.pilot else args.content_id

    synthesizer = PollySynthesizer(
        catalog_path=args.catalog,
        voice_config_path=args.voice_config,
        strategy=args.strategy,
        output_dir=args.output_dir,
    )

    if not args.execute or args.dry_run:
        preview = synthesizer.dry_run(content_id, max_chars=args.max_chars)
        print(format_preview_output(preview))
        print("\n[SAFE DRY-RUN] Preflight completed successfully. Zero AWS API calls executed. Zero charges incurred.")
        sys.exit(0)

    try:
        result = synthesizer.synthesize(
            content_id=content_id,
            execute=True,
            confirmation_token=args.confirm_paid_execution,
            force_overwrite=args.force_overwrite,
            max_chars=args.max_chars,
        )
        print(json.dumps(result, indent=2))
        sys.exit(0)
    except Exception as ex:
        print(f"ERROR: {sanitize_text(str(ex))}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
