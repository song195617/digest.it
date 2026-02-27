"""
Audio processing: normalize and split into chunks for Whisper API (25MB limit).
Uses FFmpeg via subprocess.
"""
import subprocess
import os
from pathlib import Path


def normalize_audio(input_path: str, output_dir: str) -> str:
    """Convert audio to mono 16kHz MP3 64kbps to minimize file size."""
    output_path = os.path.join(output_dir, "normalized.mp3")
    cmd = [
        "ffmpeg", "-y",
        "-i", input_path,
        "-ac", "1",         # Mono
        "-ar", "16000",     # 16kHz sample rate
        "-b:a", "64k",      # 64kbps bitrate
        "-codec:a", "libmp3lame",
        output_path,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    if result.returncode != 0:
        raise RuntimeError(f"FFmpeg normalization failed: {result.stderr}")
    return output_path


def split_audio(audio_path: str, output_dir: str, chunk_minutes: int = 10) -> list[str]:
    """
    Split audio into fixed-length chunks with 15-second overlap.
    Returns list of chunk file paths in order.
    """
    chunk_seconds = chunk_minutes * 60
    overlap_seconds = 15
    step_seconds = chunk_seconds - overlap_seconds

    # Get total duration
    duration = get_duration(audio_path)
    chunks = []

    chunk_index = 0
    start = 0
    while start < duration:
        end = min(start + chunk_seconds, duration)
        chunk_path = os.path.join(output_dir, f"chunk_{chunk_index:03d}.mp3")
        cmd = [
            "ffmpeg", "-y",
            "-i", audio_path,
            "-ss", str(start),
            "-t", str(end - start),
            "-c", "copy",
            chunk_path,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if result.returncode != 0:
            raise RuntimeError(f"FFmpeg chunk split failed: {result.stderr}")
        chunks.append(chunk_path)
        chunk_index += 1
        start += step_seconds
        if end >= duration:
            break

    return chunks


def get_duration(audio_path: str) -> float:
    """Get audio duration in seconds using ffprobe."""
    cmd = [
        "ffprobe", "-v", "quiet",
        "-show_entries", "format=duration",
        "-of", "json",
        audio_path,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError(f"ffprobe failed: {result.stderr}")
    import json
    data = json.loads(result.stdout)
    return float(data["format"]["duration"])
