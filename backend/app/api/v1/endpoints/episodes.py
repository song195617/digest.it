import json
import shutil
import uuid
from pathlib import Path
from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.api.v1.endpoints.jobs import JobStatusResponse, STATUS_PROGRESS, _job_to_response
from app.config import settings
from app.core.celery_app import celery_app
from app.core.database import get_db
from app.models.episode import ChatMessage, Episode, ProcessingJob, ProcessingStatus, Summary, Transcript
from app.services.ai.provider_config import ProviderConfig
from app.tasks.helpers import dispatch_extract_job

router = APIRouter(prefix="/v1/episodes", tags=["episodes"])


class EpisodeResponse(BaseModel):
    id: str
    platform: str
    original_url: str
    title: str
    author: str
    cover_url: str | None
    duration_seconds: int
    created_at: str
    processing_status: str
    error_message: str | None


class SegmentDto(BaseModel):
    start_ms: int
    end_ms: int
    text: str


class TranscriptResponse(BaseModel):
    episode_id: str
    full_text: str
    segments: list[SegmentDto]
    language: str
    word_count: int


class HighlightDto(BaseModel):
    quote: str
    timestamp_ms: int
    context: str


class SummaryResponse(BaseModel):
    episode_id: str
    one_liner: str
    key_points: list[str]
    topics: list[str]
    highlights: list[HighlightDto]
    full_summary: str


def _episode_to_response(ep: Episode) -> EpisodeResponse:
    return EpisodeResponse(
        id=ep.id,
        platform=ep.platform.value,
        original_url=ep.original_url,
        title=ep.title,
        author=ep.author,
        cover_url=ep.cover_url,
        duration_seconds=ep.duration_seconds,
        created_at=ep.created_at.isoformat(),
        processing_status=ep.processing_status.value,
        error_message=ep.error_message,
    )


@router.get("", response_model=list[EpisodeResponse])
async def list_episodes(db: Session = Depends(get_db)):
    episodes = db.query(Episode).order_by(Episode.created_at.desc()).all()
    return [_episode_to_response(ep) for ep in episodes]


@router.get("/{episode_id}", response_model=EpisodeResponse)
async def get_episode(episode_id: str, db: Session = Depends(get_db)):
    ep = db.query(Episode).filter(Episode.id == episode_id).first()
    if not ep:
        raise HTTPException(status_code=404, detail="Episode not found")
    return _episode_to_response(ep)


@router.post("/{episode_id}/retry", response_model=JobStatusResponse)
async def retry_episode(
    episode_id: str,
    db: Session = Depends(get_db),
    x_ai_provider: str | None = Header(default=None, alias="X-AI-Provider"),
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    x_ai_model: str | None = Header(default=None, alias="X-AI-Model"),
    x_ai_base_url: str | None = Header(default=None, alias="X-AI-Base-URL"),
):
    episode = db.query(Episode).filter(Episode.id == episode_id).first()
    if not episode:
        raise HTTPException(status_code=404, detail="Episode not found")
    if episode.processing_status != ProcessingStatus.FAILED:
        raise HTTPException(status_code=409, detail="Only failed episodes can be retried")

    provider_config = ProviderConfig.from_headers(
        provider=x_ai_provider,
        api_key=x_api_key,
        model=x_ai_model,
        base_url=x_ai_base_url,
        fallback_claude_key=settings.claude_api_key,
    )

    db.query(Transcript).filter(Transcript.episode_id == episode_id).delete(synchronize_session=False)
    db.query(Summary).filter(Summary.episode_id == episode_id).delete(synchronize_session=False)
    db.query(ChatMessage).filter(ChatMessage.episode_id == episode_id).delete(synchronize_session=False)
    shutil.rmtree(Path(settings.audio_tmp_dir) / episode_id, ignore_errors=True)

    episode.processing_status = ProcessingStatus.QUEUED
    episode.error_message = None

    job = ProcessingJob(
        id=str(uuid.uuid4()),
        episode_id=episode_id,
        status=ProcessingStatus.QUEUED,
        progress=STATUS_PROGRESS[ProcessingStatus.QUEUED],
        current_step="等待重新处理...",
        error_message=None,
    )
    db.add(job)
    db.commit()
    db.refresh(episode)
    db.refresh(job)

    try:
        dispatch_extract_job(db, job, episode, episode.original_url, provider_config.to_dict())
    except Exception as exc:
        job.status = ProcessingStatus.FAILED
        job.error_message = str(exc)
        episode.processing_status = ProcessingStatus.FAILED
        episode.error_message = str(exc)
        db.commit()
        return _job_to_response(job)

    return _job_to_response(job)


@router.delete("/{episode_id}")
async def delete_episode(episode_id: str, db: Session = Depends(get_db)):
    ep = db.query(Episode).filter(Episode.id == episode_id).first()
    if not ep:
        raise HTTPException(status_code=404, detail="Episode not found")

    for job in db.query(ProcessingJob).filter(ProcessingJob.episode_id == episode_id).all():
        if job.celery_task_id and job.status not in {ProcessingStatus.COMPLETED, ProcessingStatus.FAILED}:
            try:
                celery_app.control.revoke(job.celery_task_id, terminate=False)
            except Exception:
                pass

    db.query(Transcript).filter(Transcript.episode_id == episode_id).delete(synchronize_session=False)
    db.query(Summary).filter(Summary.episode_id == episode_id).delete(synchronize_session=False)
    db.query(ChatMessage).filter(ChatMessage.episode_id == episode_id).delete(synchronize_session=False)
    db.query(ProcessingJob).filter(ProcessingJob.episode_id == episode_id).delete(synchronize_session=False)
    db.delete(ep)
    db.commit()

    shutil.rmtree(Path(settings.audio_tmp_dir) / episode_id, ignore_errors=True)
    return {"ok": True}


@router.get("/{episode_id}/transcript", response_model=TranscriptResponse)
async def get_transcript(episode_id: str, db: Session = Depends(get_db)):
    transcript = db.query(Transcript).filter(Transcript.episode_id == episode_id).first()
    if not transcript:
        raise HTTPException(status_code=404, detail="Transcript not ready yet")
    segments = json.loads(transcript.segments_json) if transcript.segments_json else []
    return TranscriptResponse(
        episode_id=transcript.episode_id,
        full_text=transcript.full_text,
        segments=[SegmentDto(**segment) for segment in segments],
        language=transcript.language,
        word_count=transcript.word_count,
    )


@router.get("/{episode_id}/summary", response_model=SummaryResponse)
async def get_summary(episode_id: str, db: Session = Depends(get_db)):
    summary = db.query(Summary).filter(Summary.episode_id == episode_id).first()
    if not summary:
        raise HTTPException(status_code=404, detail="Summary not ready yet")
    return SummaryResponse(
        episode_id=summary.episode_id,
        one_liner=summary.one_liner,
        key_points=json.loads(summary.key_points_json) if summary.key_points_json else [],
        topics=json.loads(summary.topics_json) if summary.topics_json else [],
        highlights=[
            HighlightDto(**highlight)
            for highlight in (json.loads(summary.highlights_json) if summary.highlights_json else [])
        ],
        full_summary=summary.full_summary,
    )
