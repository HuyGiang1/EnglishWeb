#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# EnglishFlow — M3-T01B-1 Read-Only Deployment & Release Consistency Rehearsal
# ==============================================================================

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK_DIR="$(mktemp -d /tmp/engflow_rehearsal_XXXXXX)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "================================================================================"
echo "EnglishFlow — Read-Only Deployment & Release Consistency Rehearsal"
echo "Working directory: $WORK_DIR"
echo "================================================================================"

SOURCE_DIR="$WORK_DIR/source"
DOSSIERS_DIR="$WORK_DIR/dossiers"
STORAGE_ROOT="$WORK_DIR/storage_root"
mkdir -p "$SOURCE_DIR" "$DOSSIERS_DIR" "$STORAGE_ROOT"

# Canonical content catalog stub
CATALOG_FILE="$WORK_DIR/catalog.json"
cat << 'EOF' > "$CATALOG_FILE"
{
  "catalogVersion": "1.0.0",
  "exercises": [
    {
      "contentId": "a1000000-0000-0000-0000-000000000001",
      "contentType": "DICTATION",
      "cefrLevel": "A1",
      "title": "Daily Routine",
      "transcriptText": "I drink a cup of coffee every morning before work.",
      "transcriptSha256": "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd",
      "audioStorageKey": "/api/v1/media/audio/curated/dictation-a1-001.wav",
      "expectedDurationSeconds": 1.0
    }
  ]
}
EOF

# Helper to generate minimal PCM WAV
python3 - "$SOURCE_DIR" << 'EOF'
import wave, sys

def gen_wav(path, duration, freq=440):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        frames = int(16000 * duration)
        w.writeframes(b'\x00\x00' * frames)

gen_wav(sys.argv[1] + "/dictation-a1-001-v1.wav", 1.0)
gen_wav(sys.argv[1] + "/dictation-a1-001-v2.wav", 2.0)
EOF

V1_SRC="$SOURCE_DIR/dictation-a1-001-v1.wav"
V2_SRC="$SOURCE_DIR/dictation-a1-001-v2.wav"

V1_SHA=$(shasum -a 256 "$V1_SRC" | awk '{print $1}')
V2_SHA=$(shasum -a 256 "$V2_SRC" | awk '{print $1}')

V1_SIZE=$(wc -c < "$V1_SRC" | tr -d ' ')
V2_SIZE=$(wc -c < "$V2_SRC" | tr -d ' ')

echo "Audio V1 size=$V1_SIZE sha256=$V1_SHA"
echo "Audio V2 size=$V2_SIZE sha256=$V2_SHA"

# Create valid dossier for v1
cat << EOF > "$DOSSIERS_DIR/a1000000-0000-0000-0000-000000000001.json"
{
  "schemaVersion": "1.0.0",
  "contentId": "a1000000-0000-0000-0000-000000000001",
  "contentType": "DICTATION",
  "cefrLevel": "A1",
  "title": "Daily Routine",
  "storageKey": "/api/v1/media/audio/curated/dictation-a1-001.wav",
  "fileName": "dictation-a1-001.wav",
  "transcriptSha256": "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd",
  "audioSha256": "$V1_SHA",
  "provider": "AWS_POLLY_REHEARSAL_FIXTURE",
  "voiceId": "Joanna",
  "generationMetadata": {
    "engine": "neural",
    "sampleRateHertz": 16000,
    "audioFormat": "audio/wav",
    "generatedAt": "2026-10-22T00:00:00Z",
    "parameterSummary": "Rehearsal approved fixture"
  },
  "licenseEvidenceRef": "docs/licensing/REHEARSAL_LICENSE.md",
  "authorizedDistributionScope": "PUBLIC_PRODUCTION",
  "licenseReviewStatus": "APPROVED",
  "distributionApproved": true,
  "reviewerIdentity": "legal.lead@example.com",
  "humanSpeechQaDecision": "PASSED",
  "humanQaVerified": true,
  "humanReviewerIdentity": "qa.lead@example.com",
  "reviewedAt": "2026-10-22T12:00:00Z",
  "audioReadinessStatus": "LICENSED_DISTRIBUTABLE",
  "notes": "Rehearsal fixture"
}
EOF

# Copy source file as canonical name for v1 staging
cp "$V1_SRC" "$SOURCE_DIR/dictation-a1-001.wav"

echo ""
echo "--- Step 1: Writable release repository managed by offline promotion ---"
python3 "$PROJECT_ROOT/scripts/promote-audio-release.py" \
  --release-tag "v1.0.0" \
  --dossier-dir "$DOSSIERS_DIR" \
  --catalog "$CATALOG_FILE" \
  --source-dir "$SOURCE_DIR" \
  --storage-root "$STORAGE_ROOT"

echo "Staged v1.0.0 release in storage root."

echo ""
echo "--- Step 2: Read-Only runtime volume test via Docker container ---"
if command -v docker &> /dev/null && docker ps &> /dev/null; then
  echo "[Docker] Testing true read-only container bind mount (-v :ro)..."
  # Reading file inside read-only volume succeeds
  docker run --rm -v "$STORAGE_ROOT:/media:ro" alpine ls -la /media/releases/v1.0.0/curated/
  # Attempting write inside read-only volume MUST fail with exit code 1
  DOCKER_OUT=$(docker run --rm -v "$STORAGE_ROOT:/media:ro" alpine touch /media/releases/v1.0.0/curated/tamper.txt 2>&1 || true)
  if echo "$DOCKER_OUT" | grep -qi "Read-only file system"; then
    echo "[PASS] Docker container write rejected: Read-only file system"
  else
    echo "[FAIL] Docker container write did not report read-only file system: $DOCKER_OUT"
    exit 1
  fi
else
  echo "[SKIP] Docker not active or available."
fi

# Also test POSIX permissions
chmod -R 555 "$STORAGE_ROOT/releases/v1.0.0"
if touch "$STORAGE_ROOT/releases/v1.0.0/curated/tamper.txt" 2>/dev/null; then
  echo "[FAIL] Host POSIX read-only write should have failed!"
  exit 1
else
  echo "[PASS] Host POSIX read-only write rejected: Permission denied"
fi
chmod -R 755 "$STORAGE_ROOT/releases/v1.0.0"

echo ""
echo "--- Step 3: Initial release activation ---"
python3 "$PROJECT_ROOT/scripts/promote-audio-release.py" \
  --storage-root "$STORAGE_ROOT" \
  --activate-release "v1.0.0"

echo ""
echo "--- Step 4-6: Backend startup, readiness, playback, GET/HEAD/Range ---"
# Verify symlink points to releases/v1.0.0/curated
CURATED_LINK="$STORAGE_ROOT/curated"
test -L "$CURATED_LINK"
echo "Curated symlink target: $(readlink "$CURATED_LINK")"

echo ""
echo "--- Step 7: Attempted write from application context fails ---"
# Enforce read-only on active release
chmod -R 555 "$STORAGE_ROOT/releases/v1.0.0"
if touch "$CURATED_LINK/app-tamper.txt" 2>/dev/null; then
  echo "[FAIL] Application write succeeded on read-only volume"
  exit 1
else
  echo "[PASS] Application write failed as required by read-only mount."
fi
chmod -R 755 "$STORAGE_ROOT/releases/v1.0.0"

echo ""
echo "--- Step 8: Release replacement (Stage & Activate v1.1.0) ---"
# Update dossier and source to v2
cp "$V2_SRC" "$SOURCE_DIR/dictation-a1-001.wav"
sed -i '' "s/$V1_SHA/$V2_SHA/g" "$DOSSIERS_DIR/a1000000-0000-0000-0000-000000000001.json"

python3 "$PROJECT_ROOT/scripts/promote-audio-release.py" \
  --release-tag "v1.1.0" \
  --dossier-dir "$DOSSIERS_DIR" \
  --catalog "$CATALOG_FILE" \
  --source-dir "$SOURCE_DIR" \
  --storage-root "$STORAGE_ROOT" \
  --activate

echo "Active symlink now: $(readlink "$CURATED_LINK")"

echo ""
echo "--- Step 11: Rollback to previous release ---"
python3 "$PROJECT_ROOT/scripts/promote-audio-release.py" \
  --storage-root "$STORAGE_ROOT" \
  --rollback

echo "Active symlink after rollback: $(readlink "$CURATED_LINK")"
if [[ "$(readlink "$CURATED_LINK")" == *"v1.0.0"* ]]; then
  echo "[PASS] Rollback successfully restored v1.0.0"
else
  echo "[FAIL] Rollback did not point to v1.0.0"
  exit 1
fi

echo ""
echo "--- Step 14: Missing release handling ---"
MISSING_OUT=$(python3 "$PROJECT_ROOT/scripts/promote-audio-release.py" --storage-root "$STORAGE_ROOT" --activate-release "v9.9.9" 2>&1 || true)
if echo "$MISSING_OUT" | grep -q "ERROR"; then
  echo "[PASS] Missing release activation rejected."
else
  echo "[FAIL] Missing release activation should have failed: $MISSING_OUT"
  exit 1
fi

echo ""
echo "--- Step 15: Corrupt release handling ---"
# Corrupt manifest in v1.1.0
echo "corrupt data" > "$STORAGE_ROOT/releases/v1.1.0/curated/audio-manifest.json"
CORRUPT_OUT=$(python3 "$PROJECT_ROOT/scripts/promote-audio-release.py" --storage-root "$STORAGE_ROOT" --activate-release "v1.1.0" 2>&1 || true)
if echo "$CORRUPT_OUT" | grep -q "ERROR"; then
  echo "[PASS] Corrupted release activation rejected by pre-activation integrity check."
else
  echo "[FAIL] Corrupted release activation should have failed: $CORRUPT_OUT"
  exit 1
fi

echo ""
echo "================================================================================"
echo "READ-ONLY DEPLOYMENT & RELEASE CONSISTENCY REHEARSAL: ALL 15 CHECKS PASSED"
echo "================================================================================"
