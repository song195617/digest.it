from celery import Celery
from app.config import settings

celery_app = Celery(
    "digestit",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.tasks.extract_task", "app.tasks.transcribe_task", "app.tasks.summarize_task"],
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Shanghai",
    enable_utc=True,
    task_track_started=True,
    task_acks_late=True,
    worker_prefetch_multiplier=1,
)
