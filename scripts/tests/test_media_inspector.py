#!/usr/bin/env python3
"""
Unit tests for EnglishFlow Media Inspector (WAV & MP3 deterministic inspection).
Covers CBR, VBR, ID3 tags, corrupt headers, truncated streams, and WAV regression.
"""

import hashlib
import os
import shutil
import struct
import tempfile
import unittest
import wave

import sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from media_inspector import inspect_wav, inspect_mp3, inspect_audio_file, compute_file_sha256


def make_wav_bytes(pcm_samples, sample_rate=16000, channels=1, sample_width=2):
    """Generates standard in-memory PCM WAV bytes for testing."""
    data_size = pcm_samples * channels * sample_width
    file_size = 36 + data_size

    header = bytearray(44)
    header[0:4] = b"RIFF"
    header[4:8] = struct.pack("<I", file_size)
    header[8:12] = b"WAVE"
    header[12:16] = b"fmt "
    header[16:20] = struct.pack("<I", 16)  # Subchunk1Size
    header[20:22] = struct.pack("<H", 1)   # AudioFormat (PCM)
    header[22:24] = struct.pack("<H", channels)
    header[24:28] = struct.pack("<I", sample_rate)
    header[28:32] = struct.pack("<I", sample_rate * channels * sample_width)
    header[32:34] = struct.pack("<H", channels * sample_width)
    header[34:36] = struct.pack("<H", sample_width * 8)
    header[36:40] = b"data"
    header[40:44] = struct.pack("<I", data_size)

    return bytes(header) + (b"\x00" * data_size)


def make_mpeg1_layer3_frame(bitrate_kbps=128, sample_rate=44100, stereo=True):
    """Generates a synthetic valid MPEG-1 Layer III frame."""
    b0 = 0xFF
    b1 = 0xFB  # MPEG 1.0, Layer III, no CRC
    b2 = 0x90  # 128 kbps, 44.1 kHz, no padding
    b3 = 0x48 if stereo else 0xC0  # Joint Stereo vs Mono

    frame_size = int(144 * bitrate_kbps * 1000 // sample_rate)
    header = bytes([b0, b1, b2, b3])
    payload = b"\x55" * (frame_size - 4)
    return header + payload


def make_mpeg2_layer3_frame(bitrate_kbps=64, sample_rate=24000, mono=True):
    """Generates a synthetic valid MPEG-2 Layer III frame (Polly 24kHz style)."""
    b0 = 0xFF
    b1 = 0xF3  # MPEG 2.0, Layer III, no CRC
    # 64 kbps (index 8 = 1000 in MPEG-2 Layer III table), 24 kHz (index 1 = 01), no padding
    b2 = (0x8 << 4) | (0x1 << 2)
    b3 = 0xC0 if mono else 0x48

    frame_size = int(72 * bitrate_kbps * 1000 // sample_rate)
    header = bytes([b0, b1, b2, b3])
    payload = b"\xAA" * (frame_size - 4)
    return header + payload


class TestMediaInspector(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="engflow_inspector_test_")

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_valid_cbr_mp3(self):
        """Test deterministic duration and metadata extraction on MPEG-1 Layer III CBR."""
        frame = make_mpeg1_layer3_frame(128, 44100)
        num_frames = 100
        data = frame * num_frames

        # 100 frames * 1152 samples / 44100 Hz = 2.612s -> 2.61s
        expected_duration = round(num_frames * 1152 / 44100.0, 2)

        mp3_path = os.path.join(self.test_dir, "test_cbr.mp3")
        with open(mp3_path, "wb") as f:
            f.write(data)

        meta = inspect_mp3(mp3_path)
        self.assertEqual(meta["format"], "mp3")
        self.assertEqual(meta["mime_type"], "audio/mpeg")
        self.assertEqual(meta["sample_rate"], 44100)
        self.assertEqual(meta["channels"], 2)
        self.assertEqual(meta["duration_seconds"], expected_duration)
        self.assertEqual(meta["size_bytes"], len(data))
        self.assertEqual(meta["sha256"], hashlib.sha256(data).hexdigest())
        self.assertEqual(meta["total_frames"], num_frames)
        self.assertFalse(meta["is_vbr"])

    def test_valid_neural_polly_style_mp3_24khz(self):
        """Test MPEG-2 Layer III 24kHz mono (Amazon Polly Neural default configuration)."""
        frame = make_mpeg2_layer3_frame(64, 24000, mono=True)
        num_frames = 120
        data = frame * num_frames

        # 120 frames * 576 samples / 24000 Hz = 2.88s
        expected_duration = round(num_frames * 576 / 24000.0, 2)

        mp3_path = os.path.join(self.test_dir, "test_polly.mp3")
        with open(mp3_path, "wb") as f:
            f.write(data)

        meta = inspect_mp3(mp3_path)
        self.assertEqual(meta["format"], "mp3")
        self.assertEqual(meta["sample_rate"], 24000)
        self.assertEqual(meta["channels"], 1)
        self.assertEqual(meta["duration_seconds"], expected_duration)
        self.assertEqual(meta["total_frames"], num_frames)

    def test_vbr_mp3_with_xing_header(self):
        """Test VBR MP3 containing a Xing header with frame count."""
        first_frame = make_mpeg1_layer3_frame(128, 44100, stereo=True)

        # In MPEG-1 stereo, Xing offset is 4 (header) + 32 (side info) = 36 bytes
        # Construct Xing payload
        vbr_total_frames = 250
        # Expected duration: 250 * 1152 / 44100 = 6.53s
        expected_duration = round(vbr_total_frames * 1152 / 44100.0, 2)

        xing_block = bytearray(16)
        xing_block[0:4] = b"Xing"
        xing_block[4:8] = struct.pack(">I", 0x0001)  # Flags: Frames field present
        xing_block[8:12] = struct.pack(">I", vbr_total_frames)

        frame1_mod = bytearray(first_frame)
        frame1_mod[36:52] = xing_block

        # Combine with second frame
        data = bytes(frame1_mod) + make_mpeg1_layer3_frame(128, 44100, stereo=True)

        mp3_path = os.path.join(self.test_dir, "test_vbr.mp3")
        with open(mp3_path, "wb") as f:
            f.write(data)

        meta = inspect_mp3(mp3_path)
        self.assertTrue(meta["is_vbr"])
        self.assertEqual(meta["duration_seconds"], expected_duration)
        self.assertEqual(meta["total_frames"], vbr_total_frames)

    def test_mp3_with_id3v2_tag(self):
        """Test skipping ID3v2 metadata header before parsing audio frames."""
        tag_payload = b"TIT2\x00\x00\x00\x08\x00\x00\x00EnglishFlow"
        tag_size = len(tag_payload)

        # ID3v2 10-byte header with syncsafe size
        id3_header = bytearray(10)
        id3_header[0:3] = b"ID3"
        id3_header[3] = 3  # ID3v2.3
        id3_header[4] = 0
        id3_header[5] = 0
        id3_header[6] = (tag_size >> 21) & 0x7F
        id3_header[7] = (tag_size >> 14) & 0x7F
        id3_header[8] = (tag_size >> 7) & 0x7F
        id3_header[9] = tag_size & 0x7F

        id3_block = bytes(id3_header) + tag_payload

        # Audio frames
        frame = make_mpeg1_layer3_frame(128, 44100)
        data = id3_block + (frame * 50)

        mp3_path = os.path.join(self.test_dir, "test_id3v2.mp3")
        with open(mp3_path, "wb") as f:
            f.write(data)

        meta = inspect_mp3(mp3_path)
        self.assertEqual(meta["format"], "mp3")
        self.assertEqual(meta["total_frames"], 50)
        self.assertEqual(meta["duration_seconds"], round(50 * 1152 / 44100.0, 2))

    def test_mp3_with_id3v1_trailer(self):
        """Test skipping 128-byte ID3v1 trailer at end of file."""
        frame = make_mpeg1_layer3_frame(128, 44100)
        audio_data = frame * 40

        id3v1_trailer = bytearray(128)
        id3v1_trailer[0:3] = b"TAG"
        id3v1_trailer[3:33] = b"EnglishFlow Lesson Title      "

        data = audio_data + bytes(id3v1_trailer)

        mp3_path = os.path.join(self.test_dir, "test_id3v1.mp3")
        with open(mp3_path, "wb") as f:
            f.write(data)

        meta = inspect_mp3(mp3_path)
        self.assertEqual(meta["total_frames"], 40)
        self.assertEqual(meta["duration_seconds"], round(40 * 1152 / 44100.0, 2))

    def test_corrupt_header_rejected(self):
        """Test that malformed/corrupted sync words fail closed."""
        corrupt_data = b"\x00\x00\x00\x00" * 50
        mp3_path = os.path.join(self.test_dir, "corrupt.mp3")
        with open(mp3_path, "wb") as f:
            f.write(corrupt_data)

        with self.assertRaises(ValueError) as ctx:
            inspect_mp3(mp3_path)
        self.assertIn("No valid MPEG Layer III audio frame sync found", str(ctx.exception))

    def test_truncated_mp3_rejected(self):
        """Test that an MP3 truncated mid-frame is rejected."""
        frame = make_mpeg1_layer3_frame(128, 44100)
        # 1 complete frame + 20 bytes of truncated second frame
        truncated_data = frame + frame[:20]

        mp3_path = os.path.join(self.test_dir, "truncated.mp3")
        with open(mp3_path, "wb") as f:
            f.write(truncated_data)

        with self.assertRaises(ValueError) as ctx:
            inspect_mp3(mp3_path)
        self.assertIn("Truncated MP3", str(ctx.exception))

    def test_empty_mp3_rejected(self):
        """Test that 0-byte file raises ValueError."""
        empty_path = os.path.join(self.test_dir, "empty.mp3")
        open(empty_path, "wb").close()

        with self.assertRaises(ValueError) as ctx:
            inspect_mp3(empty_path)
        self.assertIn("empty (0 bytes)", str(ctx.exception))

    def test_unsupported_format_rejected(self):
        """Test top-level inspector rejects unsupported file types."""
        txt_path = os.path.join(self.test_dir, "test.txt")
        with open(txt_path, "w") as f:
            f.write("text content")

        is_valid, duration, meta, err = inspect_audio_file(txt_path)
        self.assertFalse(is_valid)
        self.assertIn("Unsupported audio extension", err)

    def test_wav_regression(self):
        """Test that existing standard WAV files continue to parse accurately."""
        # 32000 samples at 16000 Hz = 2.00 seconds
        wav_bytes = make_wav_bytes(32000, sample_rate=16000, channels=1, sample_width=2)
        wav_path = os.path.join(self.test_dir, "test.wav")
        with open(wav_path, "wb") as f:
            f.write(wav_bytes)

        meta = inspect_wav(wav_path)
        self.assertEqual(meta["format"], "wav")
        self.assertEqual(meta["mime_type"], "audio/wav")
        self.assertEqual(meta["sample_rate"], 16000)
        self.assertEqual(meta["channels"], 1)
        self.assertEqual(meta["duration_seconds"], 2.0)
        self.assertEqual(meta["sha256"], hashlib.sha256(wav_bytes).hexdigest())

    def test_duration_accuracy(self):
        """Test duration calculation precision across different frame counts."""
        frame = make_mpeg1_layer3_frame(128, 44100)
        for frame_count in [1, 10, 37, 75, 150]:
            data = frame * frame_count
            p = os.path.join(self.test_dir, f"dur_{frame_count}.mp3")
            with open(p, "wb") as f:
                f.write(data)
            meta = inspect_mp3(p)
            expected = round(frame_count * 1152 / 44100.0, 2)
            self.assertAlmostEqual(meta["duration_seconds"], expected, delta=0.01)

    def test_sha256_integrity(self):
        """Test SHA-256 calculation matches hashlib standard."""
        frame = make_mpeg1_layer3_frame(128, 44100) * 10
        p = os.path.join(self.test_dir, "sha.mp3")
        with open(p, "wb") as f:
            f.write(frame)

        self.assertEqual(compute_file_sha256(p), hashlib.sha256(frame).hexdigest())


if __name__ == "__main__":
    unittest.main()
