#!/usr/bin/env bash
set -e

# ==============================================================================
# EnglishFlow — M3-T01B HTTP E2E Verification Script
# Full-Stack End-to-End Verification through Port 3000 (Next.js & Spring Boot)
# ==============================================================================

WORKSPACE="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND_PORT=8089
FRONTEND_PORT=3000
TMP_VOL="/tmp/engflow-prod-e2e-vol"

echo "=============================================================================="
echo "EnglishFlow — Milestone M3-T01B Full-Stack HTTP E2E Verification"
echo "Workspace:     ${WORKSPACE}"
echo "Frontend:      http://localhost:${FRONTEND_PORT}"
echo "Backend:       http://localhost:${BACKEND_PORT}"
echo "=============================================================================="

cleanup() {
    echo ""
    echo "--> Cleaning up background processes..."
    if [ -n "${BACKEND_PID:-}" ] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
        echo "Stopping backend (PID: ${BACKEND_PID})..."
        kill "${BACKEND_PID}" || true
        wait "${BACKEND_PID}" 2>/dev/null || true
    fi
    if [ -n "${FRONTEND_PID:-}" ] && kill -0 "${FRONTEND_PID}" 2>/dev/null; then
        echo "Stopping frontend (PID: ${FRONTEND_PID})..."
        kill "${FRONTEND_PID}" || true
        wait "${FRONTEND_PID}" 2>/dev/null || true
    fi
    rm -rf "${TMP_VOL}" || true
}
trap cleanup EXIT INT TERM

# Load database environment variables if present
if [ -f "${WORKSPACE}/infra/.env" ]; then
    set -a
    source "${WORKSPACE}/infra/.env"
    set +a
    export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-englishflow}"
    export SPRING_DATASOURCE_USERNAME="${POSTGRES_USER:-englishflow}"
    export SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD:-}"
fi

# ------------------------------------------------------------------------------
# Phase 1: Build Check & Start Frontend
# ------------------------------------------------------------------------------
cd "${WORKSPACE}/frontend"
echo "--> Building Next.js production bundle pointing to backend port ${BACKEND_PORT}..."
BACKEND_INTERNAL_URL="http://127.0.0.1:${BACKEND_PORT}" npm run build
echo "--> Starting Next.js Production Server on port ${FRONTEND_PORT} (pointing to backend on ${BACKEND_PORT})..."
BACKEND_INTERNAL_URL="http://127.0.0.1:${BACKEND_PORT}" npm run start -- -p ${FRONTEND_PORT} > /tmp/frontend-e2e.log 2>&1 &
FRONTEND_PID=$!

echo "Waiting for Next.js to start on port ${FRONTEND_PORT}..."
for i in $(seq 1 30); do
    if curl -s -o /dev/null "http://localhost:${FRONTEND_PORT}/login"; then
        echo "Next.js is UP on port ${FRONTEND_PORT}."
        break
    fi
    sleep 1
done

# ------------------------------------------------------------------------------
# Phase 2: Test Development Flow
# ------------------------------------------------------------------------------
echo ""
echo "=============================================================================="
echo "TEST SUITE 1: DEVELOPMENT FLOW (dev profile)"
echo "=============================================================================="

cd "${WORKSPACE}/backend"
echo "Starting Spring Boot in DEV profile on port ${BACKEND_PORT}..."
SPRING_PROFILES_ACTIVE=dev \
SERVER_PORT=${BACKEND_PORT} \
java -jar target/englishflow-backend-0.0.1-SNAPSHOT.jar > /tmp/backend-dev-e2e.log 2>&1 &
BACKEND_PID=$!

BACKEND_READY=false
for i in $(seq 1 45); do
    if curl -s "http://localhost:${BACKEND_PORT}/actuator/health" | grep -q "UP"; then
        echo "Backend is UP on port ${BACKEND_PORT} in DEV mode."
        BACKEND_READY=true
        break
    fi
    sleep 1
done

if [ "${BACKEND_READY}" != "true" ]; then
    echo "ERROR: Backend failed to start. Logs:"
    cat /tmp/backend-dev-e2e.log
    exit 1
fi


echo ""
echo "[Test 1.1] Dev mode GET local audio through port 3000 proxy:"
STATUS_1_1=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/dictation-b2-042.wav")
echo "  GET /api/v1/media/audio/curated/dictation-b2-042.wav -> HTTP ${STATUS_1_1}"
[ "${STATUS_1_1}" = "200" ] || (echo "FAIL: Expected 200" && exit 1)

echo "[Test 1.2] Dev mode HEAD local audio parity through port 3000:"
STATUS_1_2=$(curl -s -I -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/dictation-b2-042.wav")
echo "  HEAD /api/v1/media/audio/curated/dictation-b2-042.wav -> HTTP ${STATUS_1_2}"
[ "${STATUS_1_2}" = "200" ] || (echo "FAIL: Expected 200" && exit 1)

echo "[Test 1.3] Dev mode HTTP Range partial content request:"
RANGE_RESP=$(curl -s -i -H "Range: bytes=0-100" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/dictation-b2-042.wav" | head -n 5)
echo "  Range response header:"
echo "${RANGE_RESP}" | sed 's/^/    /'
echo "${RANGE_RESP}" | grep -E "200|206" > /dev/null || (echo "FAIL: Expected 200 or 206" && exit 1)

echo "[Test 1.4] Direct Next.js static /media bypass is blocked (404):"
STATUS_1_4=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/media/audio/curated/dictation-b2-042.wav")
echo "  GET /media/audio/curated/dictation-b2-042.wav -> HTTP ${STATUS_1_4}"
[ "${STATUS_1_4}" = "404" ] || (echo "FAIL: Expected 404" && exit 1)

echo "[Test 1.5] Path traversal attempt is rejected (400 or 404):"
STATUS_1_5=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/%2e%2e/application.yml")
echo "  GET /api/v1/media/audio/curated/%2e%2e/application.yml -> HTTP ${STATUS_1_5}"
[ "${STATUS_1_5}" = "400" ] || [ "${STATUS_1_5}" = "404" ] || (echo "FAIL: Expected 400 or 404" && exit 1)


echo "[Test 1.6] Non-audio file extension is rejected (404):"
STATUS_1_6=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/audio-manifest.json")
echo "  GET /api/v1/media/audio/curated/audio-manifest.json -> HTTP ${STATUS_1_6}"
[ "${STATUS_1_6}" = "404" ] || (echo "FAIL: Expected 404" && exit 1)

# Stop dev backend
kill "${BACKEND_PID}"
wait "${BACKEND_PID}" 2>/dev/null || true
BACKEND_PID=""
sleep 2

# ------------------------------------------------------------------------------
# Phase 3: Disposable Production-Like Flow with Approved Test Fixture Volume
# ------------------------------------------------------------------------------
echo ""
echo "=============================================================================="
echo "TEST SUITE 2: DISPOSABLE PRODUCTION FLOW (prod profile & runtime storage root)"
echo "=============================================================================="

rm -rf "${TMP_VOL}"
mkdir -p "${TMP_VOL}/curated"

# Generate 1-second dummy WAV fixture using python
FIXTURE_PATH="${TMP_VOL}/curated/fixture-approved.wav"
python3 -c "
import wave, hashlib
with wave.open('${FIXTURE_PATH}', 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(16000)
    w.writeframes(b'\x00\x00' * 16000)
with open('${FIXTURE_PATH}', 'rb') as f:
    sha = hashlib.sha256(f.read()).hexdigest()
print('FIXTURE_SHA=' + sha)
" > /tmp/fixture-sha.txt

FIXTURE_SHA=$(grep FIXTURE_SHA /tmp/fixture-sha.txt | cut -d= -f2)
FIXTURE_SIZE=$(wc -c < "${FIXTURE_PATH}" | tr -d ' ')

# Write production release manifest approving only fixture-approved.wav
cat <<EOF > "${TMP_VOL}/curated/audio-manifest.json"
{
  "manifestVersion": "1.0.0",
  "mode": "LICENSED_DISTRIBUTABLE",
  "assets": {
    "a1000000-0000-0000-0000-000000000001": {
      "contentType": "DICTATION",
      "title": "Daily Routine",
      "storageKey": "curated/fixture-approved.wav",
      "fileName": "fixture-approved.wav",
      "sha256": "${FIXTURE_SHA}",
      "mimeType": "audio/wav",
      "fileSizeBytes": ${FIXTURE_SIZE},
      "transcriptSha256": "0f1ba802b4ddbcf89dd65de2840845f5cc028a7cd21813c1a495d8834366b9bd",
      "accent": "en-US",
      "provider": "TEST_APPROVED_STUB",
      "voiceId": "StubVoice",
      "provenance": "TEST_CONTAINER_FIXTURE",
      "licenseEvidenceRef": "docs/licensing/TEST_FIXTURE_LICENSE.md",
      "distributionApproved": true,
      "humanQaVerified": true,
      "reviewedAt": "2026-10-22T00:00:00Z",
      "readinessStatus": "LICENSED_DISTRIBUTABLE"
    },
    "b2000000-0000-0000-0000-000000000042": {
      "contentType": "DICTATION",
      "title": "Economic Resilience",
      "storageKey": "curated/dictation-b2-042.wav",
      "fileName": "dictation-b2-042.wav",
      "sha256": "80ff899d453db4fe59325f190ea472e391caee13083321650b284e36d4f9f600",
      "mimeType": "audio/wav",
      "fileSizeBytes": 155668,
      "transcriptSha256": "43015fb291077ea7dd052d94cf213db8a109a25b3cf13ba6f5f92ce3709b4344",
      "accent": "en-US",
      "provider": "LOCAL_MACOS_TTS",
      "voiceId": "Samantha",
      "provenance": "Local macOS Samantha Speech Synthesis",
      "licenseEvidenceRef": "docs/licensing/LOCAL_DEV_RESTRICTIONS.md",
      "distributionApproved": false,
      "humanQaVerified": false,
      "reviewedAt": null,
      "readinessStatus": "LOCAL_SPOKEN"
    }
  }
}
EOF


# Create outside secret and symlink inside curated pointing to it
echo "SECRET_DATA_OUTSIDE" > /tmp/secret-outside.wav
ln -sf /tmp/secret-outside.wav "${TMP_VOL}/curated/symlink-escape.wav"

echo "Starting Spring Boot in PROD profile with storage-root=${TMP_VOL}..."
SPRING_PROFILES_ACTIVE=prod \
ENGLISHFLOW_MEDIA_STORAGE_ROOT="${TMP_VOL}" \
SERVER_PORT=${BACKEND_PORT} \
java -jar "${WORKSPACE}/backend/target/englishflow-backend-0.0.1-SNAPSHOT.jar" > /tmp/backend-prod-e2e.log 2>&1 &
BACKEND_PID=$!

PROD_BACKEND_READY=false
for i in $(seq 1 45); do
    if curl -s "http://localhost:${BACKEND_PORT}/actuator/health" | grep -q "UP"; then
        echo "Backend is UP on port ${BACKEND_PORT} in PROD mode with runtime storage root."
        PROD_BACKEND_READY=true
        break
    fi
    sleep 1
done

if [ "${PROD_BACKEND_READY}" != "true" ]; then
    echo "ERROR: Prod backend failed to start. Logs:"
    cat /tmp/backend-prod-e2e.log
    exit 1
fi


echo ""
echo "[Test 2.1] Production mode GET approved test fixture from runtime volume through port 3000:"
STATUS_2_1=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/fixture-approved.wav")
echo "  GET /api/v1/media/audio/curated/fixture-approved.wav -> HTTP ${STATUS_2_1}"
[ "${STATUS_2_1}" = "200" ] || (echo "FAIL: Expected 200" && exit 1)

echo "[Test 2.2] Production mode HEAD approved test fixture parity:"
STATUS_2_2=$(curl -s -I -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/fixture-approved.wav")
echo "  HEAD /api/v1/media/audio/curated/fixture-approved.wav -> HTTP ${STATUS_2_2}"
[ "${STATUS_2_2}" = "200" ] || (echo "FAIL: Expected 200" && exit 1)

echo "[Test 2.3] Production mode HTTP Range partial request on approved fixture:"
RANGE_RESP_PROD=$(curl -s -i -H "Range: bytes=0-15" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/fixture-approved.wav" | head -n 5)
echo "  Range response header:"
echo "${RANGE_RESP_PROD}" | sed 's/^/    /'
echo "${RANGE_RESP_PROD}" | grep -E "200|206" > /dev/null || (echo "FAIL: Expected 200 or 206" && exit 1)

echo "[Test 2.4] Production mode strictly denies unapproved local dev audio (403):"
STATUS_2_4=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/dictation-b2-042.wav")
echo "  GET /api/v1/media/audio/curated/dictation-b2-042.wav -> HTTP ${STATUS_2_4}"
[ "${STATUS_2_4}" = "403" ] || (echo "FAIL: Expected 403" && exit 1)

echo "[Test 2.5] Symlink escape outside runtime volume is rejected (403):"
STATUS_2_5=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/symlink-escape.wav")
echo "  GET /api/v1/media/audio/curated/symlink-escape.wav -> HTTP ${STATUS_2_5}"
[ "${STATUS_2_5}" = "403" ] || (echo "FAIL: Expected 403" && exit 1)

echo "[Test 2.6] Non-existent asset in runtime volume returns 404:"
STATUS_2_6=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${FRONTEND_PORT}/api/v1/media/audio/curated/missing-999.wav")
echo "  GET /api/v1/media/audio/curated/missing-999.wav -> HTTP ${STATUS_2_6}"
[ "${STATUS_2_6}" = "404" ] || (echo "FAIL: Expected 404" && exit 1)

echo "[Test 2.7] Unrelated module health check in production mode:"
STATUS_2_7=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${BACKEND_PORT}/actuator/health")
echo "  GET /actuator/health -> HTTP ${STATUS_2_7}"
[ "${STATUS_2_7}" = "200" ] || (echo "FAIL: Expected 200" && exit 1)

echo ""
echo "=============================================================================="
echo "ALL HTTP E2E SCENARIOS PASSED THROUGH PORT 3000!"
echo "=============================================================================="
