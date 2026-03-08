"""
Celery task: generate AI summary using the configured AI provider.
"""
import asyncio
import json
from app.config import settings
from app.core.celery_app import celery_app
from app.core.database import SessionLocal
from app.models.episode import Episode, ProcessingJob, ProcessingStatus, Summary, Transcript
from app.services.ai.factory import get_ai_service
from app.services.ai.provider_config import ProviderConfig, PROVIDER_CLAUDE, CLAUDE_DEFAULT_MODEL
from app.tasks.helpers import update_processing_state


@celery_app.task(bind=True, name="tasks.summarize")
def summarize_task(self, job_id: str, episode_id: str, provider_config_dict: dict = None):
    """Generate structured AI summary for the episode transcript."""
    db = SessionLocal()
    try:
        job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
        episode = db.query(Episode).filter(Episode.id == episode_id).first()
        transcript = db.query(Transcript).filter(Transcript.episode_id == episode_id).first()

        if not transcript:
            raise ValueError("No transcript found for episode")

        segments = json.loads(transcript.segments_json) if transcript.segments_json else []
        update_processing_state(db, job, episode, ProcessingStatus.SUMMARIZING, 0.75, "正在生成 AI 摘要…")

        if provider_config_dict:
            config = ProviderConfig.from_dict(provider_config_dict)
        else:
            config = ProviderConfig(
                provider=PROVIDER_CLAUDE,
                api_key=settings.claude_api_key,
                model=CLAUDE_DEFAULT_MODEL,
                base_url=None,
            )

        service = get_ai_service(config)
        summary_data = asyncio.run(
            service.generate_summary(
                title=episode.title if episode else "",
                author=episode.author if episode else "",
                full_text=transcript.full_text,
                segments=segments,
            )
        )

        summary_obj = Summary(
            episode_id=episode_id,
            one_liner=summary_data.get("one_liner", ""),
            key_points_json=json.dumps(summary_data.get("key_points", []), ensure_ascii=False),
            topics_json=json.dumps(summary_data.get("topics", []), ensure_ascii=False),
            highlights_json=json.dumps(summary_data.get("highlights", []), ensure_ascii=False),
            full_summary=summary_data.get("full_summary", ""),
        )
        db.merge(summary_obj)
        db.commit()

        update_processing_state(db, job, episode, ProcessingStatus.COMPLETED, 1.0, "处理完成")

    except Exception as exc:
        db.rollback()
        job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
        episode = db.query(Episode).filter(Episode.id == episode_id).first()
        update_processing_state(
            db,
            job,
            episode,
            ProcessingStatus.FAILED,
            job.progress if job else 0.0,
            "摘要生成失败",
            str(exc),
        )
        raise
    finally:
        db.close()
