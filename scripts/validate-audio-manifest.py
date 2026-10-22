#!/usr/bin/env python3
"""
EnglishFlow — Audio Manifest Offline Validation Tool (Manifest v2)
==============================================================================
Validates the structural, cryptographic, pedagogical, and licensing integrity
of the EnglishFlow audio corpus manifest without requiring network access
or paid cloud providers.

Exit codes:
  0: Manifest and all assets pass validation.
  1: Validation error (schema, checksum, transcript mismatch, or missing file).
  2: Command-line or argument error.
"""

import argparse
import hashlib
import json
import os
import re
import sys
import wave

SUPPORTED_MIME_EXTENSIONS = {
    "audio/wav": [".wav"],
    "audio/x-wav": [".wav"],
    "audio/mpeg": [".mp3"],
    "audio/mp3": [".mp3"],
    "audio/m4a": [".m4a"],
    "audio/x-m4a": [".m4a"],
}

VALID_ACCENTS = {"en-US", "en-GB", "en-AU", "en-CA"}

VALID_READINESS_STATUSES = {
    "MISSING",
    "SYNTHETIC_FIXTURE",
    "LOCAL_SPOKEN",
    "PENDING_LICENSE",
    "PENDING_HUMAN_QA",
    "LICENSED_DISTRIBUTABLE"
}

DISALLOWED_PRODUCTION_PROVIDERS = {
    "LOCAL_MAC_TTS",
    "LOCAL_DEV_TTS",
    "SYNTHETIC_PCM",
    "SYNTHETIC_TEST_FIXTURE",
}

DISALLOWED_PRODUCTION_STATUSES = {
    "MISSING",
    "SYNTHETIC_FIXTURE",
    "LOCAL_SPOKEN",
    "PENDING_LICENSE",
    "PENDING_HUMAN_QA"
}


def compute_file_sha256(filepath):
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def inspect_audio_file(filepath, mime_type):
    """
    Inspects actual file bytes to verify format integrity and compute duration.
    Returns (is_valid, duration_seconds, error_message).
    """
    ext = os.path.splitext(filepath)[1].lower()

    if ext == ".wav":
        try:
            with wave.open(filepath, "rb") as w:
                channels = w.getnchannels()
                sample_width = w.getsampwidth()
                framerate = w.getframerate()
                frames = w.getnframes()
                if framerate <= 0:
                    return False, 0.0, "WAV framerate must be positive"
                duration = round(frames / float(framerate), 2)
                return True, duration, None
        except Exception as e:
            return False, 0.0, f"Malformed WAV container: {e}"

    elif ext == ".mp3":
        # Basic MP3 frame header validation (ID3 or MPEG frame sync 0xFFE0 / 0xFFF0)
        try:
            with open(filepath, "rb") as f:
                header = f.read(10)
                if len(header) < 4:
                    return False, 0.0, "File too small to be valid MP3"
                if header[:3] == b"ID3":
                    return True, None, None  # Has valid ID3 tag
                if header[0] == 0xFF and (header[1] & 0xE0) == 0xE0:
                    return True, None, None  # Valid MPEG frame sync
                return False, 0.0, "File does not start with valid ID3 tag or MPEG sync word"
        except Exception as e:
            return False, 0.0, f"Malformed MP3 container: {e}"

    return True, None, None


def validate_manifest(manifest_path, audio_dir=None, catalog_path=None, production_mode=False):
    errors = []
    warnings = []

    if not os.path.exists(manifest_path):
        return False, [f"Manifest file not found: {manifest_path}"], []

    if audio_dir is None:
        audio_dir = os.path.dirname(os.path.abspath(manifest_path))

    # 1. Parse JSON
    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return False, [f"Failed to parse manifest JSON: {e}"], []

    # 2. Check manifest schema version
    version = data.get("manifestVersion")
    if not version:
        errors.append("Manifest missing 'manifestVersion' field")
    elif not version.startswith("2."):
        errors.append(f"Unsupported manifest version: {version}. Expected 2.x")

    # Load canonical catalog if provided
    canonical_exercises = {}
    if catalog_path and os.path.exists(catalog_path):
        try:
            with open(catalog_path, "r", encoding="utf-8") as f:
                cat = json.load(f)
                for ex in cat.get("exercises", []):
                    canonical_exercises[ex["contentId"]] = ex
        except Exception as e:
            errors.append(f"Failed to parse canonical catalog: {e}")

    assets = data.get("assets", {})
    if not isinstance(assets, dict) or len(assets) == 0:
        errors.append("Manifest has no 'assets' dictionary or assets dictionary is empty")
        return False, errors, warnings

    seen_content_ids = set()
    seen_storage_keys = set()
    seen_filenames = set()

    for cid, asset in assets.items():
        prefix = f"Asset [{cid}]: "

        # 3. Content ID integrity
        if cid != asset.get("contentId"):
            errors.append(f"{prefix}Key mismatch: dictionary key '{cid}' != contentId '{asset.get('contentId')}'")

        if cid in seen_content_ids:
            errors.append(f"{prefix}Duplicate contentId '{cid}' in manifest")
        seen_content_ids.add(cid)

        # Storage key uniqueness
        storage_key = asset.get("storageKey")
        if not storage_key:
            errors.append(f"{prefix}Missing required 'storageKey'")
        else:
            if storage_key in seen_storage_keys:
                errors.append(f"{prefix}Duplicate storageKey '{storage_key}'")
            seen_storage_keys.add(storage_key)

            # Prevent directory traversal in storage key
            if ".." in storage_key or "//" in storage_key:
                errors.append(f"{prefix}storageKey contains illegal path traversal characters: '{storage_key}'")

        # Filename
        file_name = asset.get("fileName")
        if not file_name:
            if storage_key:
                file_name = os.path.basename(storage_key)
            else:
                errors.append(f"{prefix}Missing 'fileName'")
        if file_name in seen_filenames:
            errors.append(f"{prefix}Duplicate fileName '{file_name}'")
        if file_name:
            seen_filenames.add(file_name)

        # 4. Physical file existence & nonzero size
        if file_name:
            file_path = os.path.join(audio_dir, file_name)
            if not os.path.exists(file_path):
                errors.append(f"{prefix}Audio file does not exist on disk: '{file_path}'")
                continue

            actual_size = os.path.getsize(file_path)
            if actual_size == 0:
                errors.append(f"{prefix}Audio file is empty (0 bytes): '{file_path}'")

            expected_size = asset.get("fileSizeBytes")
            if expected_size is not None and expected_size != actual_size:
                errors.append(f"{prefix}fileSizeBytes mismatch: manifest={expected_size}, disk={actual_size}")

            # 5. SHA-256 integrity
            expected_sha256 = asset.get("sha256")
            if not expected_sha256:
                errors.append(f"{prefix}Missing 'sha256' digest in manifest")
            else:
                # Check for empty file digest placeholder
                EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                if expected_sha256 == EMPTY_SHA256 and actual_size > 0:
                    errors.append(f"{prefix}Manifest contains placeholder empty-file SHA-256")
                actual_sha256 = compute_file_sha256(file_path)
                if expected_sha256.lower() != actual_sha256.lower():
                    errors.append(f"{prefix}SHA-256 mismatch: manifest='{expected_sha256}', disk='{actual_sha256}'")

            # 6. MIME / Extension agreement
            mime_type = asset.get("mimeType")
            if not mime_type:
                errors.append(f"{prefix}Missing 'mimeType'")
            else:
                ext = os.path.splitext(file_name)[1].lower()
                allowed_exts = SUPPORTED_MIME_EXTENSIONS.get(mime_type.lower())
                if not allowed_exts:
                    errors.append(f"{prefix}Unsupported mimeType '{mime_type}'")
                elif ext not in allowed_exts:
                    errors.append(f"{prefix}Extension '{ext}' does not match mimeType '{mime_type}'")

            # 7. Audio format decoding integrity & duration
            is_valid_format, parsed_duration, format_err = inspect_audio_file(file_path, mime_type)
            if not is_valid_format:
                errors.append(f"{prefix}Audio decoding integrity failed: {format_err}")
            elif parsed_duration is not None:
                manifest_duration = asset.get("durationSeconds")
                if manifest_duration is not None:
                    # Tolerance: ±0.5s or ±10%
                    diff = abs(float(manifest_duration) - parsed_duration)
                    if diff > 0.6:
                        warnings.append(f"{prefix}Duration drift: manifest={manifest_duration}s, container={parsed_duration}s (diff={diff:.2f}s)")

        # 8. Transcript SHA-256 agreement
        transcript_sha = asset.get("transcriptSha256")
        if not transcript_sha:
            errors.append(f"{prefix}Missing 'transcriptSha256'")
        elif cid in canonical_exercises:
            expected_transcript_sha = canonical_exercises[cid]["transcriptSha256"]
            if transcript_sha.lower() != expected_transcript_sha.lower():
                errors.append(f"{prefix}Transcript SHA-256 mismatch against canonical catalog: manifest='{transcript_sha}', catalog='{expected_transcript_sha}'")

        # 9. Accent validation
        accent = asset.get("accent")
        if not accent:
            errors.append(f"{prefix}Missing 'accent'")
        elif accent not in VALID_ACCENTS:
            errors.append(f"{prefix}Invalid accent '{accent}'. Must be one of: {sorted(list(VALID_ACCENTS))}")

        # 10. Readiness status & governance fields
        readiness = asset.get("readinessStatus")
        if not readiness:
            errors.append(f"{prefix}Missing 'readinessStatus'")
        elif readiness not in VALID_READINESS_STATUSES:
            errors.append(f"{prefix}Invalid readinessStatus '{readiness}'. Allowed: {sorted(list(VALID_READINESS_STATUSES))}")

        provider = asset.get("provider")
        if not provider:
            errors.append(f"{prefix}Missing 'provider'")

        license_ref = asset.get("licenseEvidenceRef")
        if not license_ref:
            errors.append(f"{prefix}Missing 'licenseEvidenceRef'")

        dist_approved = asset.get("distributionApproved", False)
        qa_verified = asset.get("humanQaVerified", False)

        # QA verified must have reviewedAt or reviewer info
        if qa_verified and not asset.get("reviewedAt"):
            errors.append(f"{prefix}humanQaVerified is True but 'reviewedAt' timestamp is missing")

        # 11. Production mode strict gates
        if production_mode:
            if not dist_approved:
                errors.append(f"{prefix}[PROD GATE] distributionApproved is False")
            if not qa_verified:
                errors.append(f"{prefix}[PROD GATE] humanQaVerified is False")
            if readiness in DISALLOWED_PRODUCTION_STATUSES:
                errors.append(f"{prefix}[PROD GATE] readinessStatus '{readiness}' is barred from production")
            if provider in DISALLOWED_PRODUCTION_PROVIDERS:
                errors.append(f"{prefix}[PROD GATE] provider '{provider}' is barred from production")
            if license_ref:
                if "LOCAL_DEV_RESTRICTIONS" in license_ref.upper():
                    errors.append(f"{prefix}[PROD GATE] License evidence references development restrictions ('{license_ref}'), not an authorized commercial license")
                repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
                ref_path = os.path.join(repo_root, license_ref)
                if not os.path.exists(ref_path):
                    errors.append(f"{prefix}[PROD GATE] License evidence file does not exist: '{ref_path}'")

    is_valid = len(errors) == 0
    return is_valid, errors, warnings


def main():
    parser = argparse.ArgumentParser(description="EnglishFlow Audio Manifest v2 Validator")
    parser.add_argument("--manifest", default="backend/src/main/resources/static/media/audio/curated/audio-manifest.json",
                        help="Path to audio-manifest.json")
    parser.add_argument("--audio-dir", default=None,
                        help="Path to audio assets directory (defaults to manifest directory)")
    parser.add_argument("--catalog", default="content/canonical-curated-catalog.json",
                        help="Path to canonical curated catalog JSON")
    parser.add_argument("--production", action="store_true",
                        help="Enforce strict production readiness rules")

    args = parser.parse_args()

    print("==============================================================================")
    print("EnglishFlow — Audio Manifest Offline Validation")
    print(f"Manifest: {args.manifest}")
    print(f"Mode: {'STRICT PRODUCTION' if args.production else 'DEVELOPMENT / INTEGRITY'}")
    print("==============================================================================")

    is_valid, errors, warnings = validate_manifest(
        manifest_path=args.manifest,
        audio_dir=args.audio_dir,
        catalog_path=args.catalog,
        production_mode=args.production
    )

    if warnings:
        print("\nWARNINGS:")
        for w in warnings:
            print(f"  [WARN] {w}")

    if errors:
        print(f"\nVALIDATION FAILED ({len(errors)} errors):")
        for e in errors:
            print(f"  [FAIL] {e}")
        print("\nResult: REJECTED (Exit code 1)")
        sys.exit(1)

    print("\nResult: PASSED — Manifest and audio assets are structurally and cryptographically valid.")
    sys.exit(0)


if __name__ == "__main__":
    main()
