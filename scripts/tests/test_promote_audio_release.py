"""
EnglishFlow — Versioned Media Promotion & Licensing Dossier Test Suite
==============================================================================
Validates schema compliance, unapproved baseline refusal, approved fixture
staging, dry-run safety, cryptographic checksum verification, atomic activation,
and coherent release rollback.
"""

import hashlib
import json
import os
import shutil
import tempfile
import unittest
import wave
import importlib.util

scripts_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def _load_module(name, filename):
    filepath = os.path.join(scripts_dir, filename)
    spec = importlib.util.spec_from_file_location(name, filepath)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

promote_mod = _load_module("promote_audio_release", "promote-audio-release.py")
stage_release = promote_mod.stage_release
activate_release = promote_mod.activate_release
rollback_release = promote_mod.rollback_release


def create_pcm_wav(filepath, duration_sec=1.0, framerate=16000):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    num_frames = int(duration_sec * framerate)
    with wave.open(filepath, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(framerate)
        w.writeframes(b"\x00\x00" * num_frames)


class TestPromoteAudioRelease(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="engflow_promote_test_")
        self.source_dir = os.path.join(self.test_dir, "source")
        self.storage_root = os.path.join(self.test_dir, "storage_root")
        self.dossiers_dir = os.path.join(self.test_dir, "dossiers")
        os.makedirs(self.source_dir, exist_ok=True)
        os.makedirs(self.storage_root, exist_ok=True)
        os.makedirs(self.dossiers_dir, exist_ok=True)

        self.content_id = "a1000000-0000-0000-0000-000000000001"
        self.transcript = "I drink a cup of coffee every morning before work."
        self.transcript_sha = hashlib.sha256(self.transcript.encode("utf-8")).hexdigest()

        # Dummy canonical catalog
        self.catalog_path = os.path.join(self.test_dir, "catalog.json")
        catalog_data = {
            "catalogVersion": "1.0.0",
            "exercises": [
                {
                    "contentId": self.content_id,
                    "contentType": "DICTATION",
                    "cefrLevel": "A1",
                    "title": "Daily Routine",
                    "transcriptText": self.transcript,
                    "transcriptSha256": self.transcript_sha,
                    "audioStorageKey": "/api/v1/media/audio/curated/dictation-a1-001.wav",
                    "expectedDurationSeconds": 1.0
                }
            ]
        }
        with open(self.catalog_path, "w", encoding="utf-8") as f:
            json.dump(catalog_data, f)

        # Create audio file
        self.audio_file = os.path.join(self.source_dir, "dictation-a1-001.wav")
        create_pcm_wav(self.audio_file, duration_sec=1.0)
        with open(self.audio_file, "rb") as f:
            self.audio_sha = hashlib.sha256(f.read()).hexdigest()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def _write_dossier(self, approved=True, corrupt_audio_sha=False, corrupt_transcript_sha=False):
        dossier = {
            "schemaVersion": "1.0.0",
            "contentId": self.content_id,
            "contentType": "DICTATION",
            "cefrLevel": "A1",
            "title": "Daily Routine",
            "storageKey": "/api/v1/media/audio/curated/dictation-a1-001.wav",
            "fileName": "dictation-a1-001.wav",
            "transcriptSha256": "badtranscriptsha" if corrupt_transcript_sha else self.transcript_sha,
            "audioSha256": "badaudiosha" if corrupt_audio_sha else self.audio_sha,
            "provider": "AWS_POLLY_APPROVED_FIXTURE",
            "voiceId": "Joanna",
            "generationMetadata": {
                "engine": "neural",
                "sampleRateHertz": 16000,
                "audioFormat": "audio/wav",
                "generatedAt": "2026-10-22T00:00:00Z",
                "parameterSummary": "Test approved fixture"
            },
            "licenseEvidenceRef": "docs/licensing/TEST_FIXTURE_LICENSE.md",
            "authorizedDistributionScope": "PUBLIC_PRODUCTION",
            "licenseReviewStatus": "APPROVED" if approved else "PENDING",
            "distributionApproved": approved,
            "reviewerIdentity": "legal.lead@example.com" if approved else None,
            "humanSpeechQaDecision": "PASSED" if approved else "PENDING",
            "humanQaVerified": approved,
            "humanReviewerIdentity": "qa.lead@example.com" if approved else None,
            "reviewedAt": "2026-10-22T12:00:00Z" if approved else None,
            "audioReadinessStatus": "LICENSED_DISTRIBUTABLE" if approved else "PENDING_LICENSE",
            "notes": "Test fixture dossier"
        }
        d_path = os.path.join(self.dossiers_dir, f"{self.content_id}.json")
        with open(d_path, "w", encoding="utf-8") as f:
            json.dump(dossier, f, indent=2)
        return d_path

    def test_unapproved_baseline_refuses_promotion(self):
        self._write_dossier(approved=False)
        ok = stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertFalse(ok, "Unapproved dossier must be rejected from promotion")

    def test_approved_fixture_promotion_success(self):
        self._write_dossier(approved=True)
        ok = stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertTrue(ok, "Approved dossier must successfully stage")

        staged_manifest = os.path.join(self.storage_root, "releases", "v1.0.0", "curated", "audio-manifest.json")
        self.assertTrue(os.path.exists(staged_manifest))

        with open(staged_manifest, "r") as f:
            m = json.load(f)
        self.assertEqual(m["mode"], "LICENSED_DISTRIBUTABLE")
        self.assertIn(self.content_id, m["assets"])
        self.assertTrue(m["assets"][self.content_id]["distributionApproved"])

    def test_dry_run_leaves_disk_unmodified(self):
        self._write_dossier(approved=True)
        ok = stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root, dry_run=True)
        self.assertTrue(ok)
        release_dir = os.path.join(self.storage_root, "releases", "v1.0.0")
        self.assertFalse(os.path.exists(release_dir), "Dry run must not create release directory on disk")

    def test_checksum_mismatch_rejected(self):
        self._write_dossier(approved=True, corrupt_audio_sha=True)
        ok = stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertFalse(ok, "Checksum mismatch must be rejected")

    def test_transcript_mismatch_rejected(self):
        self._write_dossier(approved=True, corrupt_transcript_sha=True)
        ok = stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertFalse(ok, "Transcript hash mismatch must be rejected")

    def test_atomic_activation_and_rollback(self):
        # 1. Stage v1.0.0
        self._write_dossier(approved=True)
        ok1 = stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertTrue(ok1)
        self.assertTrue(activate_release("v1.0.0", self.storage_root))

        curated_link = os.path.join(self.storage_root, "curated")
        self.assertTrue(os.path.islink(curated_link))
        self.assertTrue(os.readlink(curated_link).endswith(os.path.join("releases", "v1.0.0", "curated")))

        # 2. Stage v1.1.0
        ok2 = stage_release("v1.1.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertTrue(ok2)
        self.assertTrue(activate_release("v1.1.0", self.storage_root))
        self.assertTrue(os.readlink(curated_link).endswith(os.path.join("releases", "v1.1.0", "curated")))

        # 3. Rollback to v1.0.0
        self.assertTrue(rollback_release(self.storage_root))
        self.assertTrue(os.readlink(curated_link).endswith(os.path.join("releases", "v1.0.0", "curated")))

    def test_path_traversal_in_release_tag_rejected(self):
        self._write_dossier(approved=True)
        # Attempt traversal tag
        ok = stage_release("../../etc", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertFalse(ok, "Path traversal in release tag must be rejected")
        self.assertFalse(activate_release("../../etc", self.storage_root), "Path traversal in activation must be rejected")

    def test_incomplete_or_corrupt_release_activation_rejected(self):
        self._write_dossier(approved=True)
        ok = stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root)
        self.assertTrue(ok)

        # Corrupt manifest file
        manifest_file = os.path.join(self.storage_root, "releases", "v1.0.0", "curated", "audio-manifest.json")
        with open(manifest_file, "w") as f:
            f.write("corrupted manifest content")

        # Activation must fail due to checksum mismatch
        self.assertFalse(activate_release("v1.0.0", self.storage_root), "Activation of corrupted release must fail")

    def test_rollback_refuses_corrupted_previous_release(self):
        self._write_dossier(approved=True)
        # 1. Stage and activate v1.0.0
        self.assertTrue(stage_release("v1.0.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root))
        self.assertTrue(activate_release("v1.0.0", self.storage_root))

        # 2. Stage and activate v1.1.0
        self.assertTrue(stage_release("v1.1.0", self.dossiers_dir, self.catalog_path, self.source_dir, self.storage_root))
        self.assertTrue(activate_release("v1.1.0", self.storage_root))

        # Corrupt v1.0.0 audio file
        v1_audio = os.path.join(self.storage_root, "releases", "v1.0.0", "curated", "dictation-a1-001.wav")
        os.remove(v1_audio)

        # 3. Rollback must refuse to activate corrupted v1.0.0
        self.assertFalse(rollback_release(self.storage_root), "Rollback must refuse corrupted previous release")

        # Active link must remain on v1.1.0
        curated_link = os.path.join(self.storage_root, "curated")
        self.assertTrue(os.readlink(curated_link).endswith(os.path.join("releases", "v1.1.0", "curated")))


if __name__ == "__main__":
    unittest.main()
