"""
Local Whisper transcription service using faster-whisper.
Handles long-form audio (1-2 hours) without chunking limits.
"""
import os
from dataclasses import dataclass
from faster_whisper import WhisperModel
from app.config import settings

_model: WhisperModel | None = None


def _get_model() -> WhisperModel:
    """Load model once and reuse across tasks."""
    global _model
    if _model is None:
        _model = WhisperModel(
            settings.whisper_model,
            device=settings.whisper_device,
            compute_type=settings.whisper_compute_type,
        )
    return _model


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
    Transcribe audio file using local faster-whisper model.
    faster-whisper handles long files internally, no manual chunking needed.
    """
    from app.services.transcription.audio_processor import normalize_audio

    normalized_path = normalize_audio(audio_path, work_dir)

    if progress_callback:
        await progress_callback(0, 1)

    model = _get_model()
    segments_iter, info = model.transcribe(
        normalized_path,
        language=language,
        beam_size=5,
        vad_filter=True,           # skip silence automatically
        vad_parameters={"min_silence_duration_ms": 500},
    )

    result_segments: list[TranscriptSegment] = []
    for seg in segments_iter:
        result_segments.append(TranscriptSegment(
            start_ms=int(seg.start * 1000),
            end_ms=int(seg.end * 1000),
            text=seg.text.strip(),
        ))

    if progress_callback:
        await progress_callback(1, 1)

    full_text = " ".join(s.text for s in result_segments)
    word_count = len(full_text.replace(" ", ""))

    return TranscriptionResult(
        full_text=full_text,
        segments=result_segments,
        language=info.language,
        word_count=word_count,
    )
