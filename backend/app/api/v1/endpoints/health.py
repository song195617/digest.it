from fastapi import APIRouter, Depends
from pydantic import BaseModel
from redis import Redis
from sqlalchemy import text
from sqlalchemy.orm import Session
from app.config import settings
from app.core.celery_app import celery_app
from app.core.database import get_db
from app.services.transcription.whisper_service import get_runtime_config

router = APIRouter(prefix="/v1", tags=["health"])


class ComponentHealthResponse(BaseModel):
    status: str
    detail: str | None = None


class WhisperHealthResponse(BaseModel):
    status: str
    mode: str
    model: str
    configured_mode: str
    initialized: bool
    fallback: bool
    error_message: str | None = None


class HealthResponse(BaseModel):
    status: str
    api: ComponentHealthResponse
    db: ComponentHealthResponse
    redis: ComponentHealthResponse
    celery: ComponentHealthResponse
    whisper: WhisperHealthResponse


@router.get("/health", response_model=HealthResponse)
async def get_health(db: Session = Depends(get_db)):
    api_health = ComponentHealthResponse(status="ok", detail="ready")

    try:
        db.execute(text("SELECT 1"))
        db_health = ComponentHealthResponse(status="ok", detail="query ok")
    except Exception as exc:
        db_health = ComponentHealthResponse(status="error", detail=str(exc))

    try:
        redis_client = Redis.from_url(settings.redis_url)
        redis_client.ping()
        redis_health = ComponentHealthResponse(status="ok", detail="ping ok")
    except Exception as exc:
        redis_health = ComponentHealthResponse(status="error", detail=str(exc))

    try:
        workers = celery_app.control.inspect(timeout=1).ping() or {}
        celery_status = "ok" if workers else "degraded"
        detail = f"{len(workers)} worker(s) responding" if workers else "no workers responded"
        celery_health = ComponentHealthResponse(status=celery_status, detail=detail)
    except Exception as exc:
        celery_health = ComponentHealthResponse(status="error", detail=str(exc))

    whisper_runtime = get_runtime_config(probe=True)
    whisper_status = "ok"
    if whisper_runtime["error_message"]:
        whisper_status = "error"
    elif whisper_runtime["fallback"]:
        whisper_status = "degraded"
    whisper_health = WhisperHealthResponse(
        status=whisper_status,
        mode=str(whisper_runtime["mode"]),
        model=str(whisper_runtime["model"]),
        configured_mode=(
            f"{whisper_runtime['configured_device']}/{whisper_runtime['configured_compute_type']}"
        ),
        initialized=bool(whisper_runtime["initialized"]),
        fallback=bool(whisper_runtime["fallback"]),
        error_message=whisper_runtime["error_message"],
    )

    component_statuses = [db_health.status, redis_health.status, celery_health.status, whisper_health.status]
    overall_status = "ok"
    if "error" in component_statuses:
        overall_status = "error"
    elif "degraded" in component_statuses:
        overall_status = "degraded"

    return HealthResponse(
        status=overall_status,
        api=api_health,
        db=db_health,
        redis=redis_health,
        celery=celery_health,
        whisper=whisper_health,
    )
