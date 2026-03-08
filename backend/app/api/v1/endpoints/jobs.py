import uuid
from fastapi import APIRouter, Depends, HTTPException, Header
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.config import settings
from app.core.database import get_db
from app.models.episode import Episode, ProcessingJob, ProcessingStatus, Platform
from app.services.ai.provider_config import ProviderConfig
from app.services.url_parser import detect_platform
from app.tasks.extract_task import extract_task

router = APIRouter(prefix="/v1/jobs", tags=["jobs"])


class SubmitUrlRequest(BaseModel):
    url: str


class JobStatusResponse(BaseModel):
    job_id: str
    episode_id: str | None
    status: str
    progress: float
    current_step: str
    error_message: str | None


STATUS_PROGRESS = {
    ProcessingStatus.QUEUED: 0.0,
    ProcessingStatus.EXTRACTING: 0.1,
    ProcessingStatus.TRANSCRIBING: 0.3,
    ProcessingStatus.SUMMARIZING: 0.75,
    ProcessingStatus.COMPLETED: 1.0,
    ProcessingStatus.FAILED: 0.0,
}


def _job_to_response(job: ProcessingJob) -> JobStatusResponse:
    return JobStatusResponse(
        job_id=job.id,
        episode_id=job.episode_id,
        status=job.status.value,
        progress=job.progress,
        current_step=job.current_step,
        error_message=job.error_message,
    )


def _reuse_existing_job(db: Session, normalized_url: str) -> ProcessingJob | None:
    episode = (
        db.query(Episode)
        .filter(Episode.original_url == normalized_url, Episode.processing_status != ProcessingStatus.FAILED)
        .order_by(Episode.created_at.desc())
        .first()
    )
    if not episode:
        return None

    existing_job = (
        db.query(ProcessingJob)
        .filter(ProcessingJob.episode_id == episode.id)
        .order_by(ProcessingJob.created_at.desc())
        .first()
    )
    if existing_job and existing_job.status != ProcessingStatus.FAILED:
        return existing_job

    job = ProcessingJob(
        id=str(uuid.uuid4()),
        episode_id=episode.id,
        status=episode.processing_status,
        progress=STATUS_PROGRESS.get(episode.processing_status, 0.0),
        current_step="复用已存在结果" if episode.processing_status == ProcessingStatus.COMPLETED else "继续处理已存在任务",
        error_message=episode.error_message,
    )
    db.add(job)
    db.commit()
    db.refresh(job)
    return job


@router.post("", response_model=JobStatusResponse)
async def submit_url(
    request: SubmitUrlRequest,
    db: Session = Depends(get_db),
    x_ai_provider: str | None = Header(default=None, alias="X-AI-Provider"),
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    x_ai_model: str | None = Header(default=None, alias="X-AI-Model"),
    x_ai_base_url: str | None = Header(default=None, alias="X-AI-Base-URL"),
):
    parsed = detect_platform(request.url)
    if parsed.platform == Platform.UNKNOWN:
        raise HTTPException(status_code=422, detail="Unsupported platform. Only Bilibili and Xiaoyuzhou are supported.")

    existing_job = _reuse_existing_job(db, parsed.normalized_url)
    if existing_job:
        return _job_to_response(existing_job)

    provider_config = ProviderConfig.from_headers(
        provider=x_ai_provider,
        api_key=x_api_key,
        model=x_ai_model,
        base_url=x_ai_base_url,
        fallback_claude_key=settings.claude_api_key,
    )

    episode_id = str(uuid.uuid4())
    job_id = str(uuid.uuid4())

    episode = Episode(
        id=episode_id,
        platform=parsed.platform,
        original_url=parsed.normalized_url,
        title="处理中…",
        processing_status=ProcessingStatus.QUEUED,
    )
    job = ProcessingJob(
        id=job_id,
        episode_id=episode_id,
        status=ProcessingStatus.QUEUED,
        progress=0.0,
        current_step="等待处理…",
    )
    db.add(episode)
    db.add(job)
    db.commit()

    try:
        async_result = extract_task.delay(job_id, episode_id, parsed.normalized_url, provider_config.to_dict())
        job.celery_task_id = async_result.id
        db.commit()
    except Exception as exc:
        job.status = ProcessingStatus.FAILED
        job.error_message = str(exc)
        episode.processing_status = ProcessingStatus.FAILED
        episode.error_message = str(exc)
        db.commit()
        return _job_to_response(job)

    return _job_to_response(job)


@router.get("/{job_id}", response_model=JobStatusResponse)
async def get_job_status(job_id: str, db: Session = Depends(get_db)):
    job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return _job_to_response(job)
