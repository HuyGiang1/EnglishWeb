#!/usr/bin/env python3
"""
EnglishFlow — Offline Versioned Media Promotion CLI
==============================================================================
Validates approved licensing dossiers, verifies source audio bytes, enforces
canonical transcript consistency, stages approved media into versioned releases,
and provides atomic activation and coherent rollback.

Usage:
  # Dry-run promotion:
  python3 scripts/promote-audio-release.py --release-tag v1.0.0 --source-dir storage/audio/source --storage-root /var/englishflow/media/audio --dry-run

  # Stage and verify versioned release:
  python3 scripts/promote-audio-release.py --release-tag v1.0.0 --source-dir storage/audio/source --storage-root /var/englishflow/media/audio

  # Atomically activate staged release:
  python3 scripts/promote-audio-release.py --storage-root /var/englishflow/media/audio --activate-release v1.0.0

  # Rollback active release to previous coherent release:
  python3 scripts/promote-audio-release.py --storage-root /var/englishflow/media/audio --rollback
"""

import argparse
import datetime
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path


def calculate_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def load_canonical_catalog(catalog_path):
    with open(catalog_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    catalog = {}
    for ex in data.get("exercises", []):
        catalog[ex["contentId"]] = ex
    return catalog


def validate_dossier_and_source(dossier_path, catalog, source_dir):
    with open(dossier_path, "r", encoding="utf-8") as f:
        dossier = json.load(f)

    cid = dossier.get("contentId")
    if not cid or cid not in catalog:
        return False, f"Dossier contentId '{cid}' not found in canonical catalog", None

    cat_entry = catalog[cid]

    # 1. Transcript hash consistency
    if dossier.get("transcriptSha256") != cat_entry.get("transcriptSha256"):
        return False, f"Transcript SHA-256 mismatch in dossier {cid} against canonical catalog", None

    # 2. Strict approval flags
    if not dossier.get("distributionApproved", False):
        return False, f"Asset {cid} has distributionApproved=false", None

    if not dossier.get("humanQaVerified", False):
        return False, f"Asset {cid} has humanQaVerified=false", None

    if dossier.get("licenseReviewStatus") != "APPROVED":
        return False, f"Asset {cid} licenseReviewStatus is not APPROVED (status={dossier.get('licenseReviewStatus')})", None

    if dossier.get("humanSpeechQaDecision") != "PASSED":
        return False, f"Asset {cid} humanSpeechQaDecision is not PASSED", None

    if dossier.get("audioReadinessStatus") != "LICENSED_DISTRIBUTABLE":
        return False, f"Asset {cid} audioReadinessStatus is not LICENSED_DISTRIBUTABLE", None

    # 3. Source audio presence and checksum
    file_name = dossier.get("fileName")
    if not file_name:
        return False, f"Asset {cid} missing fileName", None

    source_path = os.path.join(source_dir, file_name)
    if not os.path.exists(source_path) or os.path.getsize(source_path) == 0:
        return False, f"Source audio file '{source_path}' does not exist or is empty", None

    actual_audio_sha = calculate_sha256(source_path)
    expected_audio_sha = dossier.get("audioSha256")

    if not expected_audio_sha:
        return False, f"Asset {cid} dossier audioSha256 is null or empty", None

    if actual_audio_sha.lower() != expected_audio_sha.lower():
        return False, f"Audio SHA-256 mismatch for {cid}: actual={actual_audio_sha} != expected={expected_audio_sha}", None

    return True, "Valid", (dossier, source_path, actual_audio_sha, os.path.getsize(source_path))


import re

RELEASE_TAG_PATTERN = re.compile(r"^v[0-9]+(\.[0-9]+)*(-[a-zA-Z0-9.]+)?$")


def validate_release_tag(release_tag, storage_root):
    if not release_tag or not RELEASE_TAG_PATTERN.match(release_tag):
        return False, f"Invalid release tag format '{release_tag}'. Must match semantic version tag (e.g. v1.0.0, v0.2.0-rc1)"
    if ".." in release_tag or "/" in release_tag or "\\" in release_tag:
        return False, f"Release tag contains invalid path traversal characters"
    target_path = os.path.abspath(os.path.join(storage_root, "releases", release_tag))
    releases_root = os.path.abspath(os.path.join(storage_root, "releases"))
    if not target_path.startswith(releases_root + os.sep) and target_path != releases_root:
        return False, f"Release path escapes repository root"
    return True, "Valid"


def stage_release(release_tag, dossier_dir, catalog_path, source_dir, storage_root, dry_run=False):
    print("=" * 80)
    print(f"EnglishFlow — Versioned Media Promotion Pipeline")
    print(f"Release Tag:  {release_tag}")
    print(f"Dossiers:     {dossier_dir}")
    print(f"Source Dir:   {source_dir}")
    print(f"Storage Root: {storage_root}")
    print(f"Dry Run:      {dry_run}")
    print("=" * 80)

    is_valid_tag, tag_err = validate_release_tag(release_tag, storage_root)
    if not is_valid_tag:
        print(f"ERROR: {tag_err}")
        return False

    catalog = load_canonical_catalog(catalog_path)
    dossier_files = [f for f in os.listdir(dossier_dir) if f.endswith(".json")]

    if not dossier_files:
        print(f"ERROR: No licensing dossiers found in {dossier_dir}")
        return False

    approved_items = []
    rejected_reasons = []

    for df in sorted(dossier_files):
        dp = os.path.join(dossier_dir, df)
        ok, reason, item = validate_dossier_and_source(dp, catalog, source_dir)
        if ok:
            approved_items.append(item)
        else:
            rejected_reasons.append(f"{df}: {reason}")

    if not approved_items:
        print("ERROR: No dossiers satisfied production approval criteria:")
        for r in rejected_reasons:
            print(f" - {r}")
        return False

    print(f"\nValidated {len(approved_items)} approved asset(s) ready for staging.")
    if rejected_reasons:
        print(f"Note: {len(rejected_reasons)} dossier(s) skipped (unapproved / missing bytes):")
        for r in rejected_reasons[:5]:
            print(f" - {r}")

    release_dir = os.path.join(storage_root, "releases", release_tag)

    if dry_run:
        print("\n[DRY RUN] Verification succeeded. Staging directory would be:", release_dir)
        for d, sp, sha, size in approved_items:
            print(f" - Would stage {d['fileName']} ({size} bytes, sha256={sha[:12]}...)")
        return True

    if os.path.exists(release_dir):
        print(f"ERROR: Release directory '{release_dir}' already exists. Immutable release policy prohibits overwriting in-place.")
        return False

    tmp_release_dir = os.path.join(storage_root, "releases", f".tmp.{release_tag}.{os.getpid()}")
    tmp_curated_dir = os.path.join(tmp_release_dir, "curated")
    os.makedirs(tmp_curated_dir, exist_ok=True)

    try:
        # 1. Stage audio files
        manifest_assets = {}
        for dossier, source_path, sha, size in approved_items:
            dest_path = os.path.join(tmp_curated_dir, dossier["fileName"])
            shutil.copy2(source_path, dest_path)

            manifest_assets[dossier["contentId"]] = {
                "contentType": dossier["contentType"],
                "title": dossier["title"],
                "storageKey": f"curated/{dossier['fileName']}",
                "fileName": dossier["fileName"],
                "sha256": sha,
                "mimeType": dossier["generationMetadata"].get("audioFormat", "audio/wav"),
                "fileSizeBytes": size,
                "durationSeconds": None,
                "transcriptSha256": dossier["transcriptSha256"],
                "accent": "en-US",
                "provider": dossier["provider"],
                "voiceId": dossier["voiceId"],
                "provenance": "PROMOTED_PRODUCTION_RELEASE",
                "licenseEvidenceRef": dossier["licenseEvidenceRef"],
                "distributionApproved": True,
                "humanQaVerified": True,
                "reviewedAt": dossier.get("reviewedAt") or datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "readinessStatus": "LICENSED_DISTRIBUTABLE"
            }

        # 2. Produce deployment manifest
        manifest_doc = {
            "manifestVersion": "1.0.0",
            "releaseTag": release_tag,
            "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "mode": "LICENSED_DISTRIBUTABLE",
            "description": f"EnglishFlow Production Audio Release {release_tag}",
            "assets": manifest_assets
        }

        manifest_path = os.path.join(tmp_curated_dir, "audio-manifest.json")
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump(manifest_doc, f, indent=2)

        manifest_sha = calculate_sha256(manifest_path)

        # 3. Produce release metadata
        meta_doc = {
            "releaseTag": release_tag,
            "stagedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "assetCount": len(approved_items),
            "manifestSha256": manifest_sha,
            "stagedFiles": [d["fileName"] for d, _, _, _ in approved_items]
        }
        with open(os.path.join(tmp_release_dir, "release-metadata.json"), "w", encoding="utf-8") as f:
            json.dump(meta_doc, f, indent=2)

        # 4. Atomic move from tmp to final release dir
        os.replace(tmp_release_dir, release_dir)

        print(f"\nSUCCESS: Release {release_tag} successfully staged and verified at {release_dir}")
        print(f"Manifest SHA-256: {manifest_sha}")
        print(f"Release remains UNLINKED until explicit activation.")
        return True
    except Exception as ex:
        print(f"ERROR: Promotion failed during staging: {ex}")
        shutil.rmtree(tmp_release_dir, ignore_errors=True)
        return False


def activate_release(release_tag, storage_root):
    is_valid_tag, tag_err = validate_release_tag(release_tag, storage_root)
    if not is_valid_tag:
        print(f"ERROR: {tag_err}")
        return False

    release_dir = os.path.join(storage_root, "releases", release_tag)
    curated_target = os.path.join(release_dir, "curated")

    if not os.path.isdir(curated_target):
        print(f"ERROR: Cannot activate release '{release_tag}'. Staged curated dir not found: {curated_target}")
        return False

    meta_file = os.path.join(release_dir, "release-metadata.json")
    if not os.path.isfile(meta_file):
        print(f"ERROR: Cannot activate release '{release_tag}'. Missing release-metadata.json")
        return False

    try:
        with open(meta_file, "r", encoding="utf-8") as f:
            meta = json.load(f)
    except Exception as ex:
        print(f"ERROR: Cannot activate release '{release_tag}'. Corrupt release-metadata.json: {ex}")
        return False

    manifest_file = os.path.join(curated_target, "audio-manifest.json")
    if not os.path.isfile(manifest_file):
        print(f"ERROR: Cannot activate release '{release_tag}'. Missing audio-manifest.json")
        return False

    actual_manifest_sha = calculate_sha256(manifest_file)
    if actual_manifest_sha != meta.get("manifestSha256"):
        print(f"ERROR: Cannot activate release '{release_tag}'. Manifest SHA-256 mismatch against metadata (actual={actual_manifest_sha}, expected={meta.get('manifestSha256')})")
        return False

    for staged_file in meta.get("stagedFiles", []):
        fpath = os.path.join(curated_target, staged_file)
        if not os.path.isfile(fpath) or os.path.getsize(fpath) == 0:
            print(f"ERROR: Cannot activate release '{release_tag}'. Missing or empty staged audio file: {staged_file}")
            return False

    history_path = os.path.join(storage_root, "release-history.json")
    history_data = {"activeRelease": None, "history": []}
    if os.path.exists(history_path):
        try:
            with open(history_path, "r", encoding="utf-8") as f:
                history_data = json.load(f)
        except Exception as ex:
            print(f"WARNING: Could not parse history file, initializing fresh: {ex}")

    previous_active = history_data.get("activeRelease")

    # Atomic symlink activation
    active_symlink = os.path.join(storage_root, "curated")
    temp_symlink = os.path.join(storage_root, f"curated.tmp.{os.getpid()}")

    rel_target = os.path.join("releases", release_tag, "curated")

    try:
        if os.path.islink(temp_symlink) or os.path.exists(temp_symlink):
            os.remove(temp_symlink)
        os.symlink(rel_target, temp_symlink)
        os.replace(temp_symlink, active_symlink)
    except Exception as ex:
        print(f"ERROR: Failed to atomically switch symlink: {ex}")
        if os.path.exists(temp_symlink):
            os.remove(temp_symlink)
        return False

    # Update history
    history_data["activeRelease"] = release_tag
    history_data["history"].append({
        "releaseTag": release_tag,
        "activatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "previousRelease": previous_active
    })

    with open(history_path, "w", encoding="utf-8") as f:
        json.dump(history_data, f, indent=2)

    print(f"SUCCESS: Release '{release_tag}' is now active on storage root.")
    print(f"Active curated symlink: {active_symlink} -> {rel_target}")
    if previous_active:
        print(f"Previous coherent release: {previous_active}")
    return True


def rollback_release(storage_root):
    history_path = os.path.join(storage_root, "release-history.json")
    if not os.path.exists(history_path):
        print(f"ERROR: No release history file found at {history_path}")
        return False

    with open(history_path, "r", encoding="utf-8") as f:
        history_data = json.load(f)

    active = history_data.get("activeRelease")
    history_list = history_data.get("history", [])

    if not history_list or len(history_list) < 2:
        print(f"ERROR: Cannot rollback. No previous release recorded in history (current={active})")
        return False

    # Find the last distinct previous release
    previous_tag = None
    for item in reversed(history_list[:-1]):
        if item.get("releaseTag") != active:
            previous_tag = item.get("releaseTag")
            break

    if not previous_tag:
        print(f"ERROR: No coherent distinct prior release found to rollback to.")
        return False

    print(f"Rolling back active release '{active}' -> prior coherent release '{previous_tag}'...")
    return activate_release(previous_tag, storage_root)


def print_status(storage_root):
    print("=" * 80)
    print(f"EnglishFlow Media Release Status: {storage_root}")
    print("=" * 80)
    history_path = os.path.join(storage_root, "release-history.json")
    if os.path.exists(history_path):
        with open(history_path, "r") as f:
            h = json.load(f)
        print(f"Active Release: {h.get('activeRelease')}")
        print("Release History:")
        for item in h.get("history", []):
            print(f" - {item.get('releaseTag')} at {item.get('activatedAt')}")
    else:
        print("No active release history recorded.")

    curated_link = os.path.join(storage_root, "curated")
    if os.path.islink(curated_link):
        print(f"Curated symlink points to: {os.readlink(curated_link)}")
    elif os.path.exists(curated_link):
        print(f"Curated is a standard directory (unversioned legacy).")
    else:
        print("No curated symlink or directory exists.")


def main():
    parser = argparse.ArgumentParser(description="EnglishFlow Offline Versioned Media Promotion CLI")
    parser.add_argument("--release-tag", help="Unique semantic version tag (e.g. v1.0.0)")
    parser.add_argument("--dossier-dir", default="docs/licensing/dossiers", help="Path to licensing dossiers directory")
    parser.add_argument("--catalog", default="content/canonical-curated-catalog.json", help="Path to canonical curated catalog")
    parser.add_argument("--source-dir", help="Path to directory containing approved source audio bytes")
    parser.add_argument("--storage-root", required=True, help="Runtime media storage root directory containing curated/")
    parser.add_argument("--dry-run", action="store_true", help="Perform full validation without staging files")
    parser.add_argument("--activate", action="store_true", help="Atomically activate release immediately after staging")
    parser.add_argument("--activate-release", help="Atomically activate an already-staged release tag")
    parser.add_argument("--rollback", action="store_true", help="Atomically rollback active release to prior coherent release")
    parser.add_argument("--status", action="store_true", help="Show active release status and history")

    args = parser.parse_args()

    storage_root = os.path.abspath(args.storage_root)

    if args.status:
        print_status(storage_root)
        return

    if args.rollback:
        success = rollback_release(storage_root)
        sys.exit(0 if success else 1)

    if args.activate_release:
        success = activate_release(args.activate_release, storage_root)
        sys.exit(0 if success else 1)

    if not args.release_tag:
        parser.error("--release-tag is required when staging a release")

    if not args.source_dir:
        parser.error("--source-dir is required when staging a release")

    source_dir = os.path.abspath(args.source_dir)
    dossier_dir = os.path.abspath(args.dossier_dir)
    catalog_path = os.path.abspath(args.catalog)

    ok = stage_release(args.release_tag, dossier_dir, catalog_path, source_dir, storage_root, dry_run=args.dry_run)
    if not ok:
        sys.exit(1)

    if args.activate and not args.dry_run:
        act_ok = activate_release(args.release_tag, storage_root)
        sys.exit(0 if act_ok else 1)

    sys.exit(0)


if __name__ == "__main__":
    main()
