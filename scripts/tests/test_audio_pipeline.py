"""
EnglishFlow — Audio Pipeline Offline Test Suite
==============================================================================
Validates the offline ingestion and validation CLI scripts across 10 safety
invariants and error conditions.
"""

import hashlib
import json
import math
import os
import shutil
import struct
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

val_mod = _load_module("validate_audio_manifest", "validate-audio-manifest.py")
validate_manifest = val_mod.validate_manifest

ingest_mod = _load_module("ingest_audio", "ingest-audio.py")
ingest_audio = ingest_mod.ingest_audio



class TestAudioPipelineOffline(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="engflow_test_")
        self.audio_dir = os.path.join(self.test_dir, "audio")
        self.staging_dir = os.path.join(self.test_dir, "staging")
        os.makedirs(self.audio_dir, exist_ok=True)
        os.makedirs(self.staging_dir, exist_ok=True)

        self.content_id = "a1000000-0000-0000-0000-000000000001"
        self.transcript = "I drink a cup of coffee every morning before work."
        self.transcript_sha = hashlib.sha256(self.transcript.encode("utf-8")).hexdigest()

        # Create dummy canonical catalog
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
                    "audioStorageKey": "/api/v1/media/audio/curated/test-a1.wav",
                    "expectedDurationSeconds": 1.0
                }
            ]
        }
        with open(self.catalog_path, "w", encoding="utf-8") as f:
            json.dump(catalog_data, f)

        # Create a valid test WAV file
        self.valid_wav_path = os.path.join(self.audio_dir, "test-a1.wav")
        self._write_wav(self.valid_wav_path, duration_seconds=1.0)
        self.valid_wav_size = os.path.getsize(self.valid_wav_path)
        with open(self.valid_wav_path, "rb") as f:
            self.valid_wav_sha256 = hashlib.sha256(f.read()).hexdigest()

        # Create dummy license doc
        self.license_doc = os.path.join(self.test_dir, "LICENSE.md")
        with open(self.license_doc, "w") as f:
            f.write("# License evidence\n")

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def _write_wav(self, path, duration_seconds=1.0, framerate=22050):
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(framerate)
            num_frames = int(framerate * duration_seconds)
            for i in range(num_frames):
                val = int(1000 * math.sin(2 * math.pi * 440 * (i / framerate)))
                w.writeframesraw(struct.pack("<h", val))

    def _create_manifest(self, assets_dict, manifest_version="2.0.0", mode="LOCAL_SPOKEN"):
        manifest_data = {
            "manifestVersion": manifest_version,
            "generatedAt": "2026-10-17T08:00:00Z",
            "environment": "development",
            "mode": mode,
            "readiness": mode,
            "isSpokenSpeech": mode == "LOCAL_SPOKEN",
            "description": "Test manifest",
            "assets": assets_dict
        }
        manifest_path = os.path.join(self.test_dir, "audio-manifest.json")
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump(manifest_data, f, indent=2)
        return manifest_path

    # 1. Valid non-production audio fixture
    def test_01_valid_non_production_fixture(self):
        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "contentType": "DICTATION",
                "title": "Daily Routine",
                "storageKey": "/api/v1/media/audio/curated/test-a1.wav",
                "fileName": "test-a1.wav",
                "sha256": self.valid_wav_sha256,
                "mimeType": "audio/wav",
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "voiceId": "TestVoice",
                "provenance": "Local test fixture",
                "licenseEvidenceRef": self.license_doc,
                "distributionApproved": False,
                "humanQaVerified": False,
                "reviewedAt": None,
                "readinessStatus": "LOCAL_SPOKEN"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path, production_mode=False)
        self.assertTrue(is_valid, f"Expected valid dev fixture but got errors: {errors}")

    # 2. Empty file rejection
    def test_02_empty_file_rejected(self):
        empty_wav = os.path.join(self.audio_dir, "empty.wav")
        open(empty_wav, "wb").close()

        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/empty.wav",
                "fileName": "empty.wav",
                "sha256": hashlib.sha256(b"").hexdigest(),
                "mimeType": "audio/wav",
                "fileSizeBytes": 0,
                "durationSeconds": 0.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "licenseEvidenceRef": self.license_doc,
                "readinessStatus": "LOCAL_SPOKEN"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path)
        self.assertFalse(is_valid)
        self.assertTrue(any("empty (0 bytes)" in e for e in errors))

    # 3. Corrupted audio bytes
    def test_03_corrupted_audio_bytes_rejected(self):
        corrupt_wav = os.path.join(self.audio_dir, "corrupt.wav")
        with open(corrupt_wav, "wb") as f:
            f.write(b"RIFFjunkjunkjunkWAVEfmt notarealwavfilegarbagebytes")
        corrupt_sha = hashlib.sha256(b"RIFFjunkjunkjunkWAVEfmt notarealwavfilegarbagebytes").hexdigest()

        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/corrupt.wav",
                "fileName": "corrupt.wav",
                "sha256": corrupt_sha,
                "mimeType": "audio/wav",
                "fileSizeBytes": os.path.getsize(corrupt_wav),
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "licenseEvidenceRef": self.license_doc,
                "readinessStatus": "LOCAL_SPOKEN"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path)
        self.assertFalse(is_valid)
        self.assertTrue(any("decoding integrity failed" in e for e in errors))

    # 4. Mismatched MIME and file extension
    def test_04_mismatched_extension_rejected(self):
        mismatched_file = os.path.join(self.audio_dir, "test-a1.mp3")
        shutil.copyfile(self.valid_wav_path, mismatched_file)

        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/test-a1.mp3",
                "fileName": "test-a1.mp3",
                "sha256": self.valid_wav_sha256,
                "mimeType": "audio/wav", # Mismatched: mime is wav, extension is mp3
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "licenseEvidenceRef": self.license_doc,
                "readinessStatus": "LOCAL_SPOKEN"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path)
        self.assertFalse(is_valid)
        self.assertTrue(any("Extension '.mp3' does not match mimeType 'audio/wav'" in e for e in errors))

    # 5. Duplicate asset mapping
    def test_05_duplicate_asset_mapping_rejected(self):
        cid2 = "b2000000-0000-0000-0000-000000000042"
        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/test-a1.wav",
                "fileName": "test-a1.wav",
                "sha256": self.valid_wav_sha256,
                "mimeType": "audio/wav",
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "licenseEvidenceRef": self.license_doc,
                "readinessStatus": "LOCAL_SPOKEN"
            },
            cid2: {
                "contentId": cid2,
                "storageKey": "/api/v1/media/audio/curated/test-a1.wav", # DUPLICATE storageKey and file
                "fileName": "test-a1.wav",
                "sha256": self.valid_wav_sha256,
                "mimeType": "audio/wav",
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "licenseEvidenceRef": self.license_doc,
                "readinessStatus": "LOCAL_SPOKEN"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path)
        self.assertFalse(is_valid)
        self.assertTrue(any("Duplicate storageKey" in e for e in errors))

    # 6. Stale transcript hash
    def test_06_stale_transcript_hash_rejected(self):
        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/test-a1.wav",
                "fileName": "test-a1.wav",
                "sha256": self.valid_wav_sha256,
                "mimeType": "audio/wav",
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": "1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff", # STALE HASH
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "licenseEvidenceRef": self.license_doc,
                "readinessStatus": "LOCAL_SPOKEN"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path)
        self.assertFalse(is_valid)
        self.assertTrue(any("Transcript SHA-256 mismatch" in e for e in errors))

    # 7. Missing license record
    def test_07_missing_license_evidence_rejected(self):
        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/test-a1.wav",
                "fileName": "test-a1.wav",
                "sha256": self.valid_wav_sha256,
                "mimeType": "audio/wav",
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "LOCAL_DEV_TTS",
                "licenseEvidenceRef": "", # MISSING
                "readinessStatus": "LOCAL_SPOKEN"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path)
        self.assertFalse(is_valid)
        self.assertTrue(any("Missing 'licenseEvidenceRef'" in e for e in errors))

    # 8. Previously approved asset protection during ingestion
    def test_08_previously_approved_asset_protected(self):
        mf_path = os.path.join(self.test_dir, "test_approved_mf.json")
        approved_data = {
            "manifestVersion": "2.0.0",
            "assets": {
                self.content_id: {
                    "contentId": self.content_id,
                    "storageKey": "/api/v1/media/audio/curated/test-a1.wav",
                    "distributionApproved": True,
                    "humanQaVerified": True,
                    "readinessStatus": "LICENSED_DISTRIBUTABLE"
                }
            }
        }
        with open(mf_path, "w") as f:
            json.dump(approved_data, f)

        # Ingestion without --force must raise PermissionError
        with self.assertRaises(PermissionError):
            ingest_audio(
                content_id=self.content_id,
                source_file=self.valid_wav_path,
                provider="TEST_PROVIDER",
                accent="en-US",
                license_evidence=self.license_doc,
                manifest_path=mf_path,
                catalog_path=self.catalog_path,
                force=False
            )

    # 9. Forged approval metadata with placeholder empty file hash
    def test_09_forged_approval_metadata_rejected(self):
        EMPTY_FILE_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/test-a1.wav",
                "fileName": "test-a1.wav",
                "sha256": EMPTY_FILE_HASH, # FAKE EMPTY DIGEST
                "mimeType": "audio/wav",
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "AWS_POLLY",
                "licenseEvidenceRef": self.license_doc,
                "distributionApproved": True,
                "humanQaVerified": True,
                "reviewedAt": "2026-10-17T00:00:00Z",
                "readinessStatus": "LICENSED_DISTRIBUTABLE"
            }
        }
        mf_path = self._create_manifest(assets)
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path, production_mode=True)
        self.assertFalse(is_valid)
        self.assertTrue(any("placeholder empty-file SHA-256" in e or "SHA-256 mismatch" in e for e in errors))

    # 10. Synthetic fixture rejected in production mode
    def test_10_synthetic_fixture_rejected_in_production(self):
        assets = {
            self.content_id: {
                "contentId": self.content_id,
                "storageKey": "/api/v1/media/audio/curated/test-a1.wav",
                "fileName": "test-a1.wav",
                "sha256": self.valid_wav_sha256,
                "mimeType": "audio/wav",
                "fileSizeBytes": self.valid_wav_size,
                "durationSeconds": 1.0,
                "transcriptSha256": self.transcript_sha,
                "accent": "en-US",
                "provider": "SYNTHETIC_PCM",
                "licenseEvidenceRef": self.license_doc,
                "distributionApproved": False,
                "humanQaVerified": False,
                "readinessStatus": "SYNTHETIC_FIXTURE"
            }
        }
        mf_path = self._create_manifest(assets, mode="SYNTHETIC_TEST_FIXTURE")
        is_valid, errors, _ = validate_manifest(mf_path, self.audio_dir, self.catalog_path, production_mode=True)
        self.assertFalse(is_valid)
        self.assertTrue(any("[PROD GATE]" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
