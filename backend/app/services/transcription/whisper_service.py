"""
Whisper API transcription service.
Handles chunked transcription of long-form audio (1-2 hours).
"""
import asyncio
import os
from dataclasses import dataclass
from openai import AsyncOpenAI
from app.services.transcription.audio_processor import normalize_audio, split_audio, get_duration
from app.config import settings

WHISPER_MAX_MB = 24  # Keep below 25MB limit


@dataclass
class TranscriptSegment:
    start_ms: int
    end_ms: int
    text: str


@dataclass
class TranscriptionResult:
    full_text: str
    segments: list[TranscriptSegment]
    language: str
    word_count: int


async def transcribe_audio(
    audio_path: str,
    work_dir: str,
    language: str = "zh",
    progress_callback=None,
) -> TranscriptionResult:
    """
    Transcribe long-form audio file using OpenAI Whisper API.
    Automatically splits large files into chunks.
    """
    client = AsyncOpenAI(api_key=settings.openai_api_key)

    # Step 1: Normalize audio
    normalized_path = normalize_audio(audio_path, work_dir)
    file_size_mb = os.path.getsize(normalized_path) / (1024 * 1024)

    if file_size_mb <= WHISPER_MAX_MB:
        # Small enough - transcribe directly
        chunks = [normalized_path]
        chunk_offsets = [0.0]
    else:
        # Split into chunks
        chunks = split_audio(normalized_path, work_dir, chunk_minutes=10)
        # Calculate time offset for each chunk (accounting for overlap)
        step_seconds = (10 * 60) - 15  # 10 min chunks with 15-sec overlap
        chunk_offsets = [i * step_seconds for i in range(len(chunks))]

    if progress_callback:
        await progress_callback(0, len(chunks))

    all_segments: list[TranscriptSegment] = []
    previous_context = ""
    MAX_CONCURRENT = 3

    # Process chunks (max 3 concurrent to respect rate limits)
    for batch_start in range(0, len(chunks), MAX_CONCURRENT):
        batch = list(zip(
            chunks[batch_start:batch_start + MAX_CONCURRENT],
            chunk_offsets[batch_start:batch_start + MAX_CONCURRENT]
        ))
        tasks = [
            _transcribe_chunk(client, chunk_path, offset, language, previous_context)
            for chunk_path, offset in batch
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        for i, result in enumerate(results):
            if isinstance(result, Exception):
                raise result
            segments = result
            all_segments.extend(segments)
            if segments:
                # Use last 200 chars as context for next chunk
                previous_context = " ".join(s.text for s in segments[-3:])[-200:]

        if progress_callback:
            done = min(batch_start + MAX_CONCURRENT, len(chunks))
            await progress_callback(done, len(chunks))

    # Deduplicate segments from overlap regions
    deduped_segments = _deduplicate_segments(all_segments)

    full_text = " ".join(s.text for s in deduped_segments)
    word_count = len(full_text.replace(" ", ""))  # Chinese character count

    return TranscriptionResult(
        full_text=full_text,
        segments=deduped_segments,
        language=language,
        word_count=word_count,
    )


async def _transcribe_chunk(
    client: AsyncOpenAI,
    chunk_path: str,
    offset_seconds: float,
    language: str,
    prompt: str,
) -> list[TranscriptSegment]:
    """Transcribe a single audio chunk and return segments with corrected timestamps."""
    with open(chunk_path, "rb") as f:
        response = await client.audio.transcriptions.create(
            model="whisper-1",
            file=f,
            language=language,
            response_format="verbose_json",
            prompt=prompt or None,
            timestamp_granularities=["segment"],
        )

    segments = []
    for seg in (response.segments or []):
        segments.append(TranscriptSegment(
            start_ms=int((seg.start + offset_seconds) * 1000),
            end_ms=int((seg.end + offset_seconds) * 1000),
            text=seg.text.strip(),
        ))
    return segments


def _deduplicate_segments(segments: list[TranscriptSegment]) -> list[TranscriptSegment]:
    """Remove duplicate segments caused by overlap at chunk boundaries."""
    if not segments:
        return segments

    result = [segments[0]]
    for seg in segments[1:]:
        prev = result[-1]
        # Skip if this segment significantly overlaps with previous
        if seg.start_ms < prev.end_ms - 5000:  # >5s overlap = duplicate
            continue
        result.append(seg)
    return result
