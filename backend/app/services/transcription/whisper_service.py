"""
Local faster-whisper transcription service.
Runs entirely on the backend server — no API key or upload required.
"""
import asyncio
from dataclasses import dataclass
from faster_whisper import WhisperModel
from app.services.transcription.audio_processor import normalize_audio
from app.config import settings

_model: WhisperModel | None = None


def _get_model() -> WhisperModel:
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


def _transcribe_sync(audio_path: str, language: str) -> list[TranscriptSegment]:
    model = _get_model()
    segments_iter, _ = model.transcribe(audio_path, language=language, beam_size=5)
    return [
        TranscriptSegment(int(seg.start * 1000), int(seg.end * 1000), seg.text.strip())
        for seg in segments_iter
    ]


async def transcribe_audio(
    audio_path: str,
    work_dir: str,
    language: str = "zh",
    progress_callback=None,
) -> TranscriptionResult:
    """
    Transcribe audio using local faster-whisper model.
    Supports arbitrary-length audio with no chunking required.
    """
    normalized_path = normalize_audio(audio_path, work_dir)

    if progress_callback:
        await progress_callback(0, 1)

    loop = asyncio.get_event_loop()
    segments = await loop.run_in_executor(None, _transcribe_sync, normalized_path, language)

    if progress_callback:
        await progress_callback(1, 1)

    full_text = " ".join(s.text for s in segments)
    word_count = len(full_text.replace(" ", ""))

    return TranscriptionResult(
        full_text=full_text,
        segments=segments,
        language=language,
        word_count=word_count,
    )
