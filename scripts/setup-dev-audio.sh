#!/usr/bin/env bash
# ==============================================================================
# EnglishFlow — Developer Audio Corpus Setup Script
# Task: M2-T03 Daily Dictation Fresh-Clone Reproducibility & Audio Provisioning
# ==============================================================================
# Notice: Audio generated using macOS 'say' system voices (e.g., Samantha)
# is strictly restricted by Apple EULA to local private use and cannot be
# redistributed in public Git repositories.
# This script enables fresh clones to generate local reproducible audio
# assets without committing proprietary binaries to source control.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

BACKEND_AUDIO_DIR="${REPO_ROOT}/backend/src/main/resources/static/media/audio/curated"
FRONTEND_AUDIO_DIR="${REPO_ROOT}/frontend/public/media/audio/curated"

mkdir -p "${BACKEND_AUDIO_DIR}"
mkdir -p "${FRONTEND_AUDIO_DIR}"

MODE="${1:---synthetic}"

echo "=============================================================================="
echo "EnglishFlow — Dictation Audio Provisioning (Mode: ${MODE})"
echo "=============================================================================="

# Sentences and target durations for curated starter exercises (Dictation & Shadowing)
EXERCISES=(
  "dictation-a1-001.wav|I drink a cup of coffee every morning before work.|2.71"
  "dictation-a2-002.wav|We are going to visit the modern art museum on Saturday afternoon.|3.59"
  "dictation-b1-015.wav|Online learning platforms allow students to study at their own pace.|3.76"
  "dictation-b2-042.wav|The country showed great economic resilience during the crisis.|3.44"
  "dictation-c1-088.wav|Empirical evidence is fundamental to formulating hypotheses and drawing rigorous conclusions.|5.33"
  "shadowing-a1-001.wav|Good morning, how are you doing today?|2.50"
  "shadowing-a2-002.wav|Could you please tell me the way to the train station?|3.10"
  "shadowing-b1-015.wav|Practicing every day helps build natural speaking rhythm and confidence.|4.20"
  "shadowing-b2-042.wav|Fluency is not about perfection, but connection and rhythm.|3.80"
  "shadowing-c1-088.wav|Articulating complex ideas clearly requires deliberate practice and thoughtful cadence.|5.10"
)

if [ "${MODE}" = "--spoken" ] && command -v say >/dev/null 2>&1 && command -v afconvert >/dev/null 2>&1; then
  echo "--> Generating local offline spoken WAV assets using macOS speech synthesis..."
  echo "    NOTE: Generated assets are restricted to private local development."
  echo "    They must NOT be committed or pushed to public Git remotes."

  for item in "${EXERCISES[@]}"; do
    FILENAME=$(echo "${item}" | cut -d'|' -f1)
    TEXT=$(echo "${item}" | cut -d'|' -f2)
    TEMP_AIFF="/tmp/${FILENAME%.wav}.aiff"

    say -v Samantha -r 160 -o "${TEMP_AIFF}" "${TEXT}"
    afconvert -f WAVE -d LEI16@22050 "${TEMP_AIFF}" "${BACKEND_AUDIO_DIR}/${FILENAME}"
    rm -f "${TEMP_AIFF}"
    cp "${BACKEND_AUDIO_DIR}/${FILENAME}" "${FRONTEND_AUDIO_DIR}/${FILENAME}"
    echo "    Generated: ${FILENAME} (Spoken)"
  done

  # Write provenance manifest
  cat << 'EOF' > "${BACKEND_AUDIO_DIR}/audio-manifest.json"
{
  "mode": "LOCAL_SPOKEN",
  "readiness": "LOCAL_SPOKEN",
  "isSpokenSpeech": true,
  "provenance": "Local macOS Samantha Speech Synthesis (Private Dev Only)",
  "description": "Spoken English speech audio generated for private local development and verification. Not approved for public redistribution."
}
EOF
  cp "${BACKEND_AUDIO_DIR}/audio-manifest.json" "${FRONTEND_AUDIO_DIR}/audio-manifest.json"
  echo "    Manifest created: audio-manifest.json (LOCAL_SPOKEN)"

else
  echo "--> Generating portable license-cleared synthetic PCM WAV assets..."
  echo "    Format: 1-channel, 16-bit signed PCM, 22050 Hz valid WAV header"

  python3 - << 'EOF'
import os
import wave
import struct
import math
import json

exercises = [
  ("dictation-a1-001.wav", 2.71),
  ("dictation-a2-002.wav", 3.59),
  ("dictation-b1-015.wav", 3.76),
  ("dictation-b2-042.wav", 3.44),
  ("dictation-c1-088.wav", 5.33),
  ("shadowing-a1-001.wav", 2.50),
  ("shadowing-a2-002.wav", 3.10),
  ("shadowing-b1-015.wav", 4.20),
  ("shadowing-b2-042.wav", 3.80),
  ("shadowing-c1-088.wav", 5.10),
]

backend_dir = os.environ.get("BACKEND_AUDIO_DIR")
frontend_dir = os.environ.get("FRONTEND_AUDIO_DIR")
sample_rate = 22050

for filename, duration in exercises:
    b_path = os.path.join(backend_dir, filename)
    # If file already exists and is non-empty, keep existing
    if os.path.exists(b_path) and os.path.getsize(b_path) > 1000:
        continue

    total_samples = int(sample_rate * duration)
    with wave.open(b_path, 'wb') as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        # Generate clean low-amplitude 440 Hz test tone
        for i in range(total_samples):
            value = int(1200 * math.sin(2.0 * math.pi * 440.0 * (i / sample_rate)))
            data = struct.pack('<h', value)
            wav_file.writeframesraw(data)

    f_path = os.path.join(frontend_dir, filename)
    with open(b_path, 'rb') as src, open(f_path, 'wb') as dst:
        dst.write(src.read())

manifest = {
    "mode": "SYNTHETIC_TEST_FIXTURE",
    "readiness": "SYNTHETIC_FIXTURE",
    "isSpokenSpeech": False,
    "provenance": "Synthetic 440 Hz PCM Tone Generator (Transport & Decoder Verification Only)",
    "description": "Synthetic sine wave audio fixture strictly for browser media transport, HTTP 206 range requests, and audio decoder tests. Does NOT contain spoken English speech."
}
with open(os.path.join(backend_dir, "audio-manifest.json"), "w") as mf:
    json.dump(manifest, mf, indent=2)
with open(os.path.join(frontend_dir, "audio-manifest.json"), "w") as mf:
    json.dump(manifest, mf, indent=2)

print("    Synthetic WAV assets and audio-manifest.json verified successfully.")
EOF
fi

echo "=============================================================================="
echo "Audio provisioning completed. All 10 curated WAV files ready in:"
echo "  - ${BACKEND_AUDIO_DIR}"
echo "  - ${FRONTEND_AUDIO_DIR}"
echo "=============================================================================="
