from celery import Celery
from kombu import Queue
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
    broker_connection_retry_on_startup=True,
    broker_transport_options={"visibility_timeout": 43200},  # 12小时，防止长任务被重复投递
    task_queues=(
        Queue("celery"),      # 默认队列：extract / summarize
        Queue("transcribe"),  # 转录专用队列，worker 并发=1，防止 GPU OOM
    ),
    task_routes={
        "tasks.transcribe": {"queue": "transcribe"},
    },
)
