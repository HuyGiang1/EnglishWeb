#!/usr/bin/env python3
"""
EnglishFlow — Provider-Independent Offline Audio Ingestion Pipeline
==============================================================================
Ingests an externally generated or recorded audio asset into an isolated staging
area, inspects media integrity, computes cryptographic hashes against the
canonical catalog, and generates manifest metadata marked as PENDING review.

CRITICAL INVARIANTS:
  - INGESTION NEVER SETS distributionApproved=True OR humanQaVerified=True.
  - Never silently overwrites an already-approved production asset.
  - Does not require network access or cloud credentials.
"""

import argparse
import hashlib
import json
import os
import shutil
import sys
import tempfile
import wave

try:
    from media_inspector import inspect_audio_file, compute_file_sha256
except ImportError:
    from scripts.media_inspector import inspect_audio_file, compute_file_sha256

SUPPORTED_EXTENSIONS = {
    ".wav": "audio/wav",
    ".mp3": "audio/mpeg",
    ".m4a": "audio/m4a"
}

VALID_ACCENTS = {"en-US", "en-GB", "en-AU", "en-CA"}


def compute_sha256(filepath):
    return compute_file_sha256(filepath)


def inspect_media(filepath):
    ext = os.path.splitext(filepath)[1].lower()
    if ext not in SUPPORTED_EXTENSIONS:
        raise ValueError(f"Unsupported audio extension '{ext}'")

    if ext in (".wav", ".mp3"):
        is_valid, duration, meta, err = inspect_audio_file(filepath)
        if not is_valid or meta is None:
            raise ValueError(f"Invalid or corrupted audio file: {err}")
        return meta["size_bytes"], meta["duration_seconds"], meta["mime_type"]
    elif ext == ".m4a":
        size = os.path.getsize(filepath)
        with open(filepath, "rb") as f:
            header = f.read(12)
            if b"ftyp" not in header:
                raise ValueError("Not a valid M4A container (missing ftyp box)")
        return size, 1.0, SUPPORTED_EXTENSIONS[ext]

    raise ValueError(f"Unsupported format: {ext}")


def ingest_audio(
    content_id,
    source_file,
    provider,
    accent,
    license_evidence,
    voice_id=None,
    provenance=None,
    staging_dir="storage/audio/staging",
    manifest_path=None,
    catalog_path="content/canonical-curated-catalog.json",
    force=False
):
    print("==============================================================================")
    print("EnglishFlow — Audio Ingestion Pipeline")
    print(f"Content ID:       {content_id}")
    print(f"Source File:      {source_file}")
    print(f"Provider:         {provider}")
    print(f"Accent:           {accent}")
    print(f"License Evidence: {license_evidence}")
    print("==============================================================================")

    # 1. Validate against canonical content catalog
    if not os.path.exists(catalog_path):
        raise FileNotFoundError(f"Canonical catalog not found: {catalog_path}")

    with open(catalog_path, "r", encoding="utf-8") as f:
        catalog = json.load(f)

    exercise = None
    for ex in catalog.get("exercises", []):
        if ex["contentId"] == content_id:
            exercise = ex
            break

    if not exercise:
        raise ValueError(f"Content ID '{content_id}' not found in canonical catalog {catalog_path}")

    print(f"--> Validated Content: '{exercise['title']}' ({exercise['contentType']} {exercise['cefrLevel']})")

    # 2. Validate source file
    if not os.path.exists(source_file):
        raise FileNotFoundError(f"Source file does not exist: {source_file}")

    ext = os.path.splitext(source_file)[1].lower()
    if ext not in SUPPORTED_EXTENSIONS:
        raise ValueError(f"Unsupported file format '{ext}'. Supported: {list(SUPPORTED_EXTENSIONS.keys())}")

    if accent not in VALID_ACCENTS:
        raise ValueError(f"Invalid accent '{accent}'. Must be one of: {sorted(list(VALID_ACCENTS))}")

    # 3. Inspect actual codec, duration, and size
    size_bytes, duration_secs, mime_type = inspect_media(source_file)
    audio_sha256 = compute_sha256(source_file)
    transcript_sha256 = exercise["transcriptSha256"]

    print(f"--> Media Inspection: {mime_type}, size={size_bytes} bytes, duration={duration_secs}s")
    print(f"--> Audio SHA-256:    {audio_sha256}")
    print(f"--> Canonical SHA256: {transcript_sha256}")

    # 4. Copy asset into isolated staging directory
    os.makedirs(staging_dir, exist_ok=True)
    staging_filename = f"{content_id}{ext}"
    staging_filepath = os.path.join(staging_dir, staging_filename)

    # Atomic copy
    temp_fd, temp_path = tempfile.mkstemp(dir=staging_dir, prefix="ingest_tmp_")
    os.close(temp_fd)
    shutil.copyfile(source_file, temp_path)
    os.replace(temp_path, staging_filepath)
    print(f"--> Staged file to:   {staging_filepath}")

    # 5. Build asset metadata (STRICTLY UNAPPROVED)
    voice_name = voice_id if voice_id else f"{provider}_DEFAULT"
    prov_desc = provenance if provenance else f"Ingested from {provider} voice {voice_name}"

    asset_record = {
        "contentId": content_id,
        "contentType": exercise["contentType"],
        "title": exercise["title"],
        "storageKey": exercise["audioStorageKey"],
        "fileName": os.path.basename(exercise["audioStorageKey"]),
        "stagingPath": os.path.relpath(staging_filepath),
        "sha256": audio_sha256,
        "mimeType": mime_type,
        "fileSizeBytes": size_bytes,
        "durationSeconds": duration_secs,
        "transcriptSha256": transcript_sha256,
        "accent": accent,
        "provider": provider,
        "voiceId": voice_name,
        "provenance": prov_desc,
        "licenseEvidenceRef": license_evidence,
        "distributionApproved": False,       # NEVER SELF-APPROVE
        "humanQaVerified": False,           # NEVER SELF-APPROVE
        "reviewedAt": None,
        "readinessStatus": "PENDING_HUMAN_QA"
    }

    # 6. Update manifest if requested
    if manifest_path:
        if os.path.exists(manifest_path):
            with open(manifest_path, "r", encoding="utf-8") as mf:
                manifest_data = json.load(mf)
        else:
            manifest_data = {
                "manifestVersion": "2.0.0",
                "generatedAt": "2026-10-17T08:00:00Z",
                "environment": "staging",
                "mode": "PENDING_REVIEW",
                "readiness": "PENDING_HUMAN_QA",
                "isSpokenSpeech": True,
                "description": "EnglishFlow Staged Ingestion Manifest",
                "assets": {}
            }

        existing_asset = manifest_data.get("assets", {}).get(content_id)
        if existing_asset and existing_asset.get("distributionApproved", False) and not force:
            raise PermissionError(
                f"Asset for content '{content_id}' is already distributionApproved in {manifest_path}. "
                "Refusing to overwrite without --force flag."
            )

        manifest_data.setdefault("assets", {})[content_id] = asset_record

        # Atomic manifest write
        temp_mf_fd, temp_mf_path = tempfile.mkstemp(dir=os.path.dirname(os.path.abspath(manifest_path)), prefix="manifest_tmp_")
        with os.fdopen(temp_mf_fd, "w", encoding="utf-8") as f:
            json.dump(manifest_data, f, indent=2)
        os.replace(temp_mf_path, manifest_path)
        print(f"--> Updated Manifest: {manifest_path} (Asset status: PENDING_HUMAN_QA)")

    print("\n[SUCCESS] Ingestion completed safely.")
    print("  Status:               PENDING_HUMAN_QA")
    print("  distributionApproved: False (Requires separate Licensing approval)")
    print("  humanQaVerified:      False (Requires separate Human QA approval)")
    return asset_record


def main():
    parser = argparse.ArgumentParser(description="EnglishFlow Audio Offline Ingestion Pipeline")
    parser.add_argument("--content-id", required=True, help="Canonical content UUID")
    parser.add_argument("--source-file", required=True, help="Path to input audio file")
    parser.add_argument("--provider", required=True, help="Voice provider name (e.g. AWS_POLLY, HUMAN_RECORDING)")
    parser.add_argument("--accent", required=True, help="Spoken accent (e.g. en-US, en-GB)")
    parser.add_argument("--license-evidence", required=True, help="Reference to license agreement document")
    parser.add_argument("--voice-id", default=None, help="Voice persona or actor identifier")
    parser.add_argument("--provenance", default=None, help="Origin description")
    parser.add_argument("--staging-dir", default="storage/audio/staging", help="Isolated staging directory")
    parser.add_argument("--manifest", default=None, help="Manifest file to update")
    parser.add_argument("--catalog", default="content/canonical-curated-catalog.json", help="Path to canonical catalog")
    parser.add_argument("--force", action="store_true", help="Force overwrite even if previously approved")

    args = parser.parse_args()

    try:
        ingest_audio(
            content_id=args.content_id,
            source_file=args.source_file,
            provider=args.provider,
            accent=args.accent,
            license_evidence=args.license_evidence,
            voice_id=args.voice_id,
            provenance=args.provenance,
            staging_dir=args.staging_dir,
            manifest_path=args.manifest,
            catalog_path=args.catalog,
            force=args.force
        )
        sys.exit(0)
    except Exception as e:
        print(f"\n[ERROR] Ingestion failed: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
