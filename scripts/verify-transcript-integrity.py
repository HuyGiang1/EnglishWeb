#!/usr/bin/env python3
"""
EnglishFlow Canonical Transcript & Audio Manifest Consistency Auditor
Section 6: Verify transcript SHA-256 integrity across:
1. PostgreSQL Flyway migration seeds (V6 & V8)
2. Authoritative content catalog (content/canonical-curated-catalog.json)
3. Audio manifest (backend/src/main/resources/static/media/audio/curated/audio-manifest.json)
"""

import sys
import os
import json
import re
import hashlib
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

CATALOG_PATH = REPO_ROOT / "content" / "canonical-curated-catalog.json"
MANIFEST_PATH = REPO_ROOT / "backend" / "src" / "main" / "resources" / "static" / "media" / "audio" / "curated" / "audio-manifest.json"
V6_MIGRATION = REPO_ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration" / "V6__create_learning_contents_and_dictation.sql"
V8_MIGRATION = REPO_ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration" / "V8__create_shadowing_takes_and_content.sql"

def compute_sha256(text: str) -> str:
    return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()

def extract_sql_seeded_transcripts() -> dict:
    """Extracts seeded contentId -> transcriptText mappings from Flyway DDL."""
    seeds = {}
    for path in [V6_MIGRATION, V8_MIGRATION]:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        for block in re.finditer(r"\(\s*'(?P<cid>[a-f0-9-]{36})'.*?\)\s*[,;]", text, re.DOTALL):
            block_text = block.group(0)
            literals = re.findall(r"'([^']*)'", block_text)
            if len(literals) >= 6:
                # 0: id, 1: content_type, 2: title, 3: cefr_level, 4: topic_tag, 5: transcript_text
                seeds[literals[0]] = literals[5]
    return seeds

def main():
    print("=" * 80)
    print("EnglishFlow — Canonical Transcript & Manifest Consistency Auditor")
    print("=" * 80)
    
    if not CATALOG_PATH.exists():
        print(f"ERROR: Catalog not found at {CATALOG_PATH}", file=sys.stderr)
        sys.exit(1)
        
    if not MANIFEST_PATH.exists():
        print(f"ERROR: Manifest not found at {MANIFEST_PATH}", file=sys.stderr)
        sys.exit(1)
        
    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        catalog = json.load(f)
        
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)
        
    sql_seeds = extract_sql_seeded_transcripts()
    
    manifest_assets = manifest.get("assets", {})
    if isinstance(manifest_assets, list):
        manifest_assets = {a["contentId"]: a for a in manifest_assets}
        
    violations = []
    
    print(f"Checking {len(catalog.get('exercises', []))} curated catalog entries...")
    
    for ex in catalog.get("exercises", []):
        cid = ex["contentId"]
        title = ex.get("title", "")
        text = ex.get("transcriptText", "").strip()
        expected_hash = compute_sha256(text)
        cat_hash = ex.get("transcriptSha256", "").strip()
        
        # 1. Catalog internal integrity
        if cat_hash != expected_hash:
            violations.append(
                f"[{cid}] '{title}' Catalog transcriptSha256 mismatch!\n"
                f"   Catalog value:  {cat_hash}\n"
                f"   Calculated SHA: {expected_hash}"
            )
            
        # 2. Manifest vs Catalog
        asset = manifest_assets.get(cid)
        if not asset:
            violations.append(f"[{cid}] '{title}' Missing from audio-manifest.json!")
        else:
            mf_hash = asset.get("transcriptSha256", "").strip()
            if mf_hash != expected_hash:
                violations.append(
                    f"[{cid}] '{title}' Manifest transcriptSha256 mismatch!\n"
                    f"   Manifest value: {mf_hash}\n"
                    f"   Calculated SHA: {expected_hash}"
                )
                
        # 3. SQL Seed vs Catalog
        if cid in sql_seeds:
            sql_text = sql_seeds[cid].strip()
            if sql_text != text:
                violations.append(
                    f"[{cid}] '{title}' SQL Flyway seed drift!\n"
                    f"   SQL text:     '{sql_text}'\n"
                    f"   Catalog text: '{text}'"
                )
        else:
            violations.append(f"[{cid}] '{title}' Not found in Flyway V6/V8 migration seeds!")
            
    if violations:
        print("\nDRIFT DETECTED:")
        for v in violations:
            print(f" - {v}")
        print("\nResult: FAILED — Transcript integrity drift detected across database/catalog/manifest.", file=sys.stderr)
        sys.exit(1)
        
    print(f"\nResult: PASSED — All {len(catalog['exercises'])} curated exercises have 100% cryptographic agreement across:")
    print("  1. PostgreSQL Flyway migration seeds (V6 & V8)")
    print("  2. Authoritative content catalog (canonical-curated-catalog.json)")
    print("  3. Audio manifest (audio-manifest.json)")
    print("=" * 80)
    sys.exit(0)

if __name__ == "__main__":
    main()
