#!/usr/bin/env python3
"""
EnglishFlow Media Inspector & Audio Parser
Authoritative offline audio inspection utility for WAV and MP3 containers.
Provides deterministic, zero-dependency duration extraction, format verification,
and cryptographic checksum calculations.
"""

import hashlib
import os
import struct
import wave

SUPPORTED_EXTENSIONS = {
    ".wav": "audio/wav",
    ".mp3": "audio/mpeg",
}

# MPEG Audio Bitrate Table (in kbps)
# MPEG Version index: 1 = MPEG-1, 2 = MPEG-2 / 2.5
# Layer index: 3 = Layer III
BITRATE_TABLE = {
    # MPEG-1, Layer III
    (1, 3): [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320],
    # MPEG-2 & MPEG-2.5, Layer III
    (2, 3): [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
}

# MPEG Sampling Frequency Table (in Hz)
# MPEG Version: 1 = MPEG-1, 2 = MPEG-2, 2.5 = MPEG-2.5
SAMPLERATE_TABLE = {
    1: [44100, 48000, 32000],
    2: [22050, 24000, 16000],
    2.5: [11025, 12000, 8000],
}


def compute_file_sha256(filepath):
    """Calculates SHA-256 cryptographic digest of a file."""
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def inspect_wav(filepath):
    """
    Deterministically inspects a WAV audio container.
    Returns metadata dictionary or raises ValueError.
    """
    size_bytes = os.path.getsize(filepath)
    if size_bytes == 0:
        raise ValueError(f"WAV file is empty (0 bytes): {filepath}")

    try:
        with wave.open(filepath, "rb") as w:
            channels = w.getnchannels()
            sample_rate = w.getframerate()
            frames = w.getnframes()
            sample_width = w.getsampwidth()

            if sample_rate <= 0:
                raise ValueError("WAV sample rate must be strictly positive")
            if frames <= 0:
                raise ValueError("WAV contains zero audio frames")

            duration = round(frames / float(sample_rate), 2)
            if duration <= 0.0:
                raise ValueError("WAV duration calculation resulted in zero or negative value")

            return {
                "format": "wav",
                "mime_type": "audio/wav",
                "duration_seconds": duration,
                "sample_rate": sample_rate,
                "channels": channels,
                "sample_width": sample_width,
                "size_bytes": size_bytes,
                "sha256": compute_file_sha256(filepath),
                "is_vbr": False,
                "bitrate_kbps": int(sample_rate * channels * sample_width * 8 / 1000),
            }
    except wave.Error as e:
        raise ValueError(f"Malformed or corrupt WAV container: {e}")
    except Exception as e:
        raise ValueError(f"Failed to parse WAV audio: {e}")


def _parse_id3v2(f):
    """
    Checks for ID3v2 header and returns tag length (bytes to skip).
    Returns 0 if no ID3v2 header is present.
    """
    f.seek(0)
    header = f.read(10)
    if len(header) < 10 or header[:3] != b"ID3":
        return 0

    # ID3v2 syncsafe integer (4 bytes, 7 bits per byte)
    tag_size = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F)
    id3_len = 10 + tag_size

    # Check for ID3v2.4 footer flag (bit 4 of byte 5)
    if header[5] & 0x10:
        id3_len += 10

    return id3_len


def _parse_id3v1(f, file_size):
    """
    Checks if file has a 128-byte ID3v1 trailer at EOF.
    Returns 128 if present, else 0.
    """
    if file_size < 128:
        return 0
    f.seek(file_size - 128)
    trailer = f.read(3)
    if trailer == b"TAG":
        return 128
    return 0


def _parse_frame_header(header_bytes):
    """
    Parses a 4-byte MPEG audio frame header.
    Returns (mpeg_version, layer, bitrate_kbps, sample_rate, padding, channels, frame_size, samples_per_frame)
    or None if invalid.
    """
    if len(header_bytes) < 4:
        return None

    b0, b1, b2, b3 = header_bytes[0], header_bytes[1], header_bytes[2], header_bytes[3]

    # Frame sync: 11 bits must be 1
    if b0 != 0xFF or (b1 & 0xE0) != 0xE0:
        return None

    # MPEG Version: 00 = 2.5, 01 = reserved, 10 = 2.0, 11 = 1.0
    ver_bits = (b1 >> 3) & 0x03
    if ver_bits == 0:
        mpeg_version = 2.5
        version_family = 2
    elif ver_bits == 2:
        mpeg_version = 2.0
        version_family = 2
    elif ver_bits == 3:
        mpeg_version = 1.0
        version_family = 1
    else:
        return None  # Reserved

    # Layer: 01 = Layer III (MP3), 10 = Layer II, 11 = Layer I
    layer_bits = (b1 >> 1) & 0x03
    if layer_bits == 1:
        layer = 3  # Layer III
    else:
        return None  # Only Layer III is supported as MP3

    # Bitrate index: bits 7-4 of byte 2
    bitrate_idx = (b2 >> 4) & 0x0F
    if bitrate_idx == 0x00 or bitrate_idx == 0x0F:
        return None  # Free or bad bitrate

    bitrates = BITRATE_TABLE.get((version_family, layer))
    if not bitrates or bitrate_idx >= len(bitrates):
        return None
    bitrate_kbps = bitrates[bitrate_idx]

    # Sampling rate index: bits 3-2 of byte 2
    sr_idx = (b2 >> 2) & 0x03
    if sr_idx == 3:
        return None  # Reserved

    rates = SAMPLERATE_TABLE.get(mpeg_version)
    if not rates or sr_idx >= len(rates):
        return None
    sample_rate = rates[sr_idx]

    # Padding bit: bit 1 of byte 2
    padding = (b2 >> 1) & 0x01

    # Channels: bits 7-6 of byte 3 (11 = single channel / mono, else stereo/joint)
    channel_mode = (b3 >> 6) & 0x03
    channels = 1 if channel_mode == 3 else 2

    # Samples per frame for Layer III
    samples_per_frame = 1152 if mpeg_version == 1.0 else 576

    # Frame size in bytes
    if mpeg_version == 1.0:
        frame_size = int((144 * bitrate_kbps * 1000 // sample_rate) + padding)
    else:
        frame_size = int((72 * bitrate_kbps * 1000 // sample_rate) + padding)

    if frame_size < 4:
        return None

    return mpeg_version, layer, bitrate_kbps, sample_rate, padding, channels, frame_size, samples_per_frame


def inspect_mp3(filepath):
    """
    Deterministically inspects an MP3 audio container.
    Handles ID3v2 metadata, ID3v1 trailers, VBR headers (Xing/Info/VBRI),
    and CBR/VBR frame-walking.
    Returns metadata dictionary or raises ValueError.
    """
    size_bytes = os.path.getsize(filepath)
    if size_bytes == 0:
        raise ValueError(f"MP3 file is empty (0 bytes): {filepath}")

    with open(filepath, "rb") as f:
        id3v2_size = _parse_id3v2(f)
        id3v1_size = _parse_id3v1(f, size_bytes)

        audio_start = id3v2_size
        audio_end = size_bytes - id3v1_size

        if audio_start >= audio_end:
            raise ValueError(f"MP3 contains no audio frame data (only metadata): {filepath}")

        # Scan for first valid MPEG audio frame
        f.seek(audio_start)
        first_frame_pos = None
        first_header_info = None

        search_limit = min(audio_start + 8192, audio_end)
        curr = audio_start
        while curr < search_limit:
            f.seek(curr)
            head = f.read(4)
            if len(head) < 4:
                break
            info = _parse_frame_header(head)
            if info is not None:
                first_frame_pos = curr
                first_header_info = info
                break
            curr += 1

        if first_frame_pos is None or first_header_info is None:
            raise ValueError(f"Malformed MP3: No valid MPEG Layer III audio frame sync found in {filepath}")

        mpeg_ver, layer, initial_bitrate, sample_rate, padding, channels, first_frame_size, samples_per_frame = first_header_info

        # Check for Xing / Info / VBRI VBR header in the first frame
        is_vbr = False
        vbr_frames = None

        # Side information size before Xing/Info header
        if mpeg_ver == 1.0:
            side_info_len = 17 if channels == 1 else 32
        else:
            side_info_len = 9 if channels == 1 else 17

        xing_offset = first_frame_pos + 4 + side_info_len
        f.seek(xing_offset)
        xing_candidate = f.read(16)
        if len(xing_candidate) >= 12:
            tag = xing_candidate[:4]
            if tag in (b"Xing", b"Info"):
                is_vbr = (tag == b"Xing")
                flags = struct.unpack(">I", xing_candidate[4:8])[0]
                if flags & 0x0001:  # Frames flag is set
                    vbr_frames = struct.unpack(">I", xing_candidate[8:12])[0]

        # Check for VBRI header at offset 36 from frame start
        if vbr_frames is None:
            f.seek(first_frame_pos + 4 + 32)
            vbri_candidate = f.read(18)
            if len(vbri_candidate) >= 18 and vbri_candidate[:4] == b"VBRI":
                is_vbr = True
                vbr_frames = struct.unpack(">I", vbri_candidate[14:18])[0]

        # Deterministic frame-walking across the entire audio stream to verify integrity
        total_frames = 0
        total_samples = 0
        pos = first_frame_pos
        bitrates_seen = set()

        while pos < audio_end:
            f.seek(pos)
            head = f.read(4)
            if len(head) < 4:
                raise ValueError(f"Truncated MP3 stream at byte {pos} in {filepath}")

            info = _parse_frame_header(head)
            if info is None:
                raise ValueError(f"Corrupt MP3: Lost frame sync at byte {pos} in {filepath}")

            _, _, frame_bitrate, frame_sr, _, _, frame_size, frame_samples = info
            if frame_sr != sample_rate:
                raise ValueError(f"Inconsistent sample rate inside MP3 stream at byte {pos}")

            if pos + frame_size > audio_end:
                raise ValueError(f"Truncated MP3: Frame of {frame_size} bytes exceeds audio stream boundary at byte {pos} in {filepath}")

            bitrates_seen.add(frame_bitrate)
            total_frames += 1
            total_samples += frame_samples
            pos += frame_size

        if pos < audio_end:
            raise ValueError(f"Corrupt MP3: {audio_end - pos} unparsed trailing bytes at EOF in {filepath}")

        if total_frames == 0 or total_samples == 0:
            raise ValueError(f"Malformed MP3: Decoded 0 valid audio frames in {filepath}")

        # Compute duration:
        # If Xing/Info header specified frame count, use header-based duration for precision
        if vbr_frames is not None and vbr_frames > 0:
            duration = round((vbr_frames * samples_per_frame) / float(sample_rate), 2)
            reported_frames = vbr_frames
        else:
            duration = round(total_samples / float(sample_rate), 2)
            reported_frames = total_frames

        if duration <= 0.0:
            raise ValueError(f"Computed MP3 duration is zero or negative in {filepath}")

        is_vbr = is_vbr or (len(bitrates_seen) > 1)

        return {
            "format": "mp3",
            "mime_type": "audio/mpeg",
            "duration_seconds": duration,
            "sample_rate": sample_rate,
            "channels": channels,
            "size_bytes": size_bytes,
            "sha256": compute_file_sha256(filepath),
            "is_vbr": is_vbr,
            "bitrate_kbps": initial_bitrate if not is_vbr else None,
            "total_frames": reported_frames,
        }


def inspect_audio_file(filepath, mime_type=None):
    """
    Top-level media inspector dispatching to WAV or MP3 parsers.
    Returns (is_valid, duration_seconds, metadata_dict, error_message).
    """
    if not os.path.exists(filepath):
        return False, 0.0, None, f"Audio file does not exist: {filepath}"

    ext = os.path.splitext(filepath)[1].lower()
    if ext not in SUPPORTED_EXTENSIONS:
        return False, 0.0, None, f"Unsupported audio extension '{ext}'. Only .wav and .mp3 are supported."

    try:
        if ext == ".wav":
            meta = inspect_wav(filepath)
        elif ext == ".mp3":
            meta = inspect_mp3(filepath)
        else:
            return False, 0.0, None, f"Unsupported format: {ext}"

        return True, meta["duration_seconds"], meta, None
    except Exception as e:
        return False, 0.0, None, str(e)
