#!/usr/bin/env python3
"""
Unit and integration tests verifying EnglishFlow Media Inspector against real,
standards-compliant encoded MP3 audio files generated via LAME / FFmpeg.

Covers:
1. Real encoded CBR MP3 (MPEG-1 Layer III, 128 kbps, 44.1 kHz).
2. Real encoded VBR MP3 (MPEG-1 Layer III with Xing/Info header).
3. Real encoded MP3 with ID3v2 metadata tags.
4. Real encoded 24 kHz mono speech-compatible MP3 (AWS Polly Neural profile).
5. Truncated MP3 (fails closed with ValueError).
6. Corrupted MP3 (fails closed with ValueError).
7. Duration agreement with reference decoder (macOS afinfo or ffprobe).

Distinguishes truly encoded playable MP3 files from synthetic frame fixtures.
All test audio is generated in temporary directories and cleanly discarded.
"""

import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from media_inspector import inspect_mp3, inspect_audio_file, compute_file_sha256, _parse_id3v2, _parse_frame_header


def get_reference_duration(file_path: str):
    """
    Extracts reference duration using an independent decoder:
    1. macOS /usr/bin/afinfo (CoreAudio reference decoder)
    2. ffprobe (if available)
    """
    if os.path.exists("/usr/bin/afinfo"):
        try:
            out = subprocess.check_output(["/usr/bin/afinfo", file_path], text=True, stderr=subprocess.DEVNULL)
            m = re.search(r"estimated duration:\s+([\d\.]+)\s+sec", out)
            if m:
                return round(float(m.group(1)), 2)
        except Exception:
            pass

    try:
        out = subprocess.check_output(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", file_path],
            text=True,
            stderr=subprocess.DEVNULL
        )
        return round(float(out.strip()), 2)
    except Exception:
        pass

    return None


class TestRealEncodedMp3(unittest.TestCase):

    test_dir = None
    fixtures_available = False

    @classmethod
    def setUpClass(cls):
        cls.test_dir = tempfile.mkdtemp(prefix="engflow_real_mp3_test_")

        # Check if local ffmpeg is available
        has_local_ffmpeg = shutil.which("ffmpeg") is not None

        # Check if docker is available
        has_docker = False
        if not has_local_ffmpeg and shutil.which("docker") is not None:
            try:
                res = subprocess.run(["docker", "info"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
                has_docker = (res.returncode == 0)
            except Exception:
                has_docker = False

        if not has_local_ffmpeg and not has_docker:
            return

        try:
            if has_local_ffmpeg:
                # Generate using local ffmpeg
                subprocess.run([
                    "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                    "-c:a", "libmp3lame", "-b:a", "128k", os.path.join(cls.test_dir, "cbr.mp3")
                ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run([
                    "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=2.5",
                    "-c:a", "libmp3lame", "-q:a", "2", os.path.join(cls.test_dir, "vbr.mp3")
                ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run([
                    "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=1.5",
                    "-c:a", "libmp3lame", "-b:a", "128k", "-metadata", "title=EnglishFlow Test",
                    "-metadata", "artist=EnglishFlow QA", os.path.join(cls.test_dir, "id3.mp3")
                ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run([
                    "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
                    "-ar", "24000", "-ac", "1", "-c:a", "libmp3lame", "-b:a", "64k",
                    os.path.join(cls.test_dir, "speech24k.mp3")
                ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                # Generate via alpine container with ffmpeg
                cmd = (
                    "apk add --no-cache ffmpeg >/dev/null 2>&1 && "
                    "ffmpeg -f lavfi -i 'sine=frequency=440:duration=2' -c:a libmp3lame -b:a 128k /tmp/out/cbr.mp3 >/dev/null 2>&1 && "
                    "ffmpeg -f lavfi -i 'sine=frequency=440:duration=2.5' -c:a libmp3lame -q:a 2 /tmp/out/vbr.mp3 >/dev/null 2>&1 && "
                    "ffmpeg -f lavfi -i 'sine=frequency=440:duration=1.5' -c:a libmp3lame -b:a 128k -metadata title='EnglishFlow Test' -metadata artist='EnglishFlow QA' /tmp/out/id3.mp3 >/dev/null 2>&1 && "
                    "ffmpeg -f lavfi -i 'sine=frequency=440:duration=3' -ar 24000 -ac 1 -c:a libmp3lame -b:a 64k /tmp/out/speech24k.mp3 >/dev/null 2>&1"
                )
                subprocess.run([
                    "docker", "run", "--rm", "-v", f"{cls.test_dir}:/tmp/out", "alpine:latest", "sh", "-c", cmd
                ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)

            # Create truncated test fixture
            cbr_path = os.path.join(cls.test_dir, "cbr.mp3")
            with open(cbr_path, "rb") as f:
                cbr_bytes = f.read()
            with open(os.path.join(cls.test_dir, "truncated.mp3"), "wb") as f:
                f.write(cbr_bytes[:-50])  # Chop off trailing 50 bytes mid-frame

            # Create corrupted test fixture (corrupt sync bytes in frame 2)
            corrupt_bytes = bytearray(cbr_bytes)
            with open(cbr_path, "rb") as f:
                id3_len = _parse_id3v2(f)
            info = _parse_frame_header(cbr_bytes[id3_len:id3_len+4])
            if info:
                f2_pos = id3_len + info[6]  # frame_size of frame 1
                corrupt_bytes[f2_pos:f2_pos+2] = b"\x00\x00"
            else:
                corrupt_bytes[id3_len+418:id3_len+420] = b"\x00\x00"
            with open(os.path.join(cls.test_dir, "corrupted.mp3"), "wb") as f:
                f.write(bytes(corrupt_bytes))

            cls.fixtures_available = True
        except Exception as e:
            import traceback
            traceback.print_exc()
            cls.fixtures_available = False

    @classmethod
    def tearDownClass(cls):
        if cls.test_dir and os.path.exists(cls.test_dir):
            shutil.rmtree(cls.test_dir, ignore_errors=True)

    def setUp(self):
        if not self.fixtures_available:
            self.skipTest("FFmpeg or Docker not available to encode real MP3 fixtures")

    def test_real_encoded_cbr_mp3(self):
        """Verifies real LAME-encoded CBR MP3 (128 kbps, 44.1 kHz)."""
        path = os.path.join(self.test_dir, "cbr.mp3")
        meta = inspect_mp3(path)

        self.assertEqual(meta["format"], "mp3")
        self.assertEqual(meta["mime_type"], "audio/mpeg")
        self.assertEqual(meta["sample_rate"], 44100)
        self.assertFalse(meta["is_vbr"])
        self.assertEqual(meta["bitrate_kbps"], 128)
        self.assertGreater(meta["total_frames"], 50)
        self.assertGreater(meta["duration_seconds"], 1.9)
        self.assertLess(meta["duration_seconds"], 2.2)

        # Cross-validate against independent reference decoder
        ref_dur = get_reference_duration(path)
        if ref_dur is not None:
            self.assertAlmostEqual(meta["duration_seconds"], ref_dur, delta=0.05)

    def test_real_encoded_vbr_mp3(self):
        """Verifies real LAME-encoded VBR MP3 with Xing/Info header."""
        path = os.path.join(self.test_dir, "vbr.mp3")
        meta = inspect_mp3(path)

        self.assertEqual(meta["format"], "mp3")
        self.assertEqual(meta["mime_type"], "audio/mpeg")
        self.assertEqual(meta["sample_rate"], 44100)
        self.assertTrue(meta["is_vbr"])
        self.assertGreater(meta["total_frames"], 50)
        self.assertGreater(meta["duration_seconds"], 2.4)
        self.assertLess(meta["duration_seconds"], 2.7)

        ref_dur = get_reference_duration(path)
        if ref_dur is not None:
            self.assertAlmostEqual(meta["duration_seconds"], ref_dur, delta=0.05)

    def test_real_encoded_id3_metadata_mp3(self):
        """Verifies real LAME-encoded MP3 with ID3v2 tags skips headers correctly."""
        path = os.path.join(self.test_dir, "id3.mp3")
        meta = inspect_mp3(path)

        self.assertEqual(meta["format"], "mp3")
        self.assertEqual(meta["mime_type"], "audio/mpeg")
        self.assertEqual(meta["sample_rate"], 44100)
        self.assertGreater(meta["duration_seconds"], 1.4)
        self.assertLess(meta["duration_seconds"], 1.7)

        ref_dur = get_reference_duration(path)
        if ref_dur is not None:
            self.assertAlmostEqual(meta["duration_seconds"], ref_dur, delta=0.05)

    def test_real_speech_compatible_24k_mono_mp3(self):
        """Verifies real encoded 24 kHz mono MP3 (AWS Polly Neural profile)."""
        path = os.path.join(self.test_dir, "speech24k.mp3")
        meta = inspect_mp3(path)

        self.assertEqual(meta["format"], "mp3")
        self.assertEqual(meta["mime_type"], "audio/mpeg")
        self.assertEqual(meta["sample_rate"], 24000)
        self.assertEqual(meta["channels"], 1)
        self.assertGreater(meta["duration_seconds"], 2.9)
        self.assertLess(meta["duration_seconds"], 3.2)

        ref_dur = get_reference_duration(path)
        if ref_dur is not None:
            self.assertAlmostEqual(meta["duration_seconds"], ref_dur, delta=0.05)

    def test_truncated_real_mp3_fails_closed(self):
        """Verifies truncated real MP3 fails closed with ValueError."""
        path = os.path.join(self.test_dir, "truncated.mp3")
        with self.assertRaises(ValueError) as ctx:
            inspect_mp3(path)
        self.assertIn("Truncated MP3", str(ctx.exception))

    def test_corrupted_real_mp3_fails_closed(self):
        """Verifies corrupted real MP3 fails closed with ValueError."""
        path = os.path.join(self.test_dir, "corrupted.mp3")
        with self.assertRaises(ValueError) as ctx:
            inspect_mp3(path)
        self.assertTrue(
            "Unsynchronized data" in str(ctx.exception)
            or "Truncated MP3" in str(ctx.exception)
            or "corrupted" in str(ctx.exception).lower()
        )

    def test_duration_agreement_across_all_real_fixtures(self):
        """Verifies duration agreement between media_inspector and reference decoder."""
        fixtures = ["cbr.mp3", "vbr.mp3", "id3.mp3", "speech24k.mp3"]
        for fixture_name in fixtures:
            path = os.path.join(self.test_dir, fixture_name)
            meta = inspect_mp3(path)
            ref_dur = get_reference_duration(path)
            if ref_dur is not None:
                self.assertAlmostEqual(
                    meta["duration_seconds"],
                    ref_dur,
                    delta=0.05,
                    msg=f"Duration mismatch for fixture {fixture_name}: inspector={meta['duration_seconds']}, ref={ref_dur}"
                )


if __name__ == "__main__":
    unittest.main()
