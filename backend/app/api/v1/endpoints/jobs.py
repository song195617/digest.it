import uuid
from fastapi import APIRouter, Depends, HTTPException, Header
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.models.episode import Episode, ProcessingJob, ProcessingStatus, Platform
from app.services.url_parser import detect_platform
from app.services.ai.provider_config import ProviderConfig
from app.tasks.extract_task import extract_task
from app.config import settings

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

    # Kick off Celery pipeline with provider config
    extract_task.delay(job_id, episode_id, parsed.normalized_url, provider_config.to_dict())

    return JobStatusResponse(
        job_id=job_id,
        episode_id=episode_id,
        status=ProcessingStatus.QUEUED.value,
        progress=0.0,
        current_step="等待处理…",
        error_message=None,
    )


@router.get("/{job_id}", response_model=JobStatusResponse)
async def get_job_status(job_id: str, db: Session = Depends(get_db)):
    job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return JobStatusResponse(
        job_id=job.id,
        episode_id=job.episode_id,
        status=job.status.value,
        progress=job.progress,
        current_step=job.current_step,
        error_message=job.error_message,
    )
