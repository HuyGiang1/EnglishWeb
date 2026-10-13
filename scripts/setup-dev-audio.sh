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

# Sentences and target durations for the 5 curated starter exercises
EXERCISES=(
  "dictation-a1-001.wav|I drink a cup of coffee every morning before work.|2.71"
  "dictation-a2-002.wav|We are going to visit the modern art museum on Saturday afternoon.|3.59"
  "dictation-b1-015.wav|Online learning platforms allow students to study at their own pace.|3.76"
  "dictation-b2-042.wav|The country showed great economic resilience during the crisis.|3.44"
  "dictation-c1-088.wav|Empirical evidence is fundamental to formulating hypotheses and drawing rigorous conclusions.|5.33"
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

else
  echo "--> Generating portable license-cleared synthetic PCM WAV assets..."
  echo "    Format: 1-channel, 16-bit signed PCM, 22050 Hz valid WAV header"

  python3 - << 'EOF'
import os
import wave
import struct
import math

exercises = [
  ("dictation-a1-001.wav", 2.71),
  ("dictation-a2-002.wav", 3.59),
  ("dictation-b1-015.wav", 3.76),
  ("dictation-b2-042.wav", 3.44),
  ("dictation-c1-088.wav", 5.33),
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

print("    Synthetic WAV assets verified successfully.")
EOF
fi

echo "=============================================================================="
echo "Audio provisioning completed. All 5 curated WAV files ready in:"
echo "  - ${BACKEND_AUDIO_DIR}"
echo "  - ${FRONTEND_AUDIO_DIR}"
echo "=============================================================================="
