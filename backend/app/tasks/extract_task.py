"""
Celery task: extract audio/subtitle from platform URL.
"""
import asyncio
import json
import os
from app.config import settings
from app.core.celery_app import celery_app
from app.core.database import SessionLocal
from app.models.episode import Episode, ProcessingJob, ProcessingStatus, Transcript, Platform
from app.services.extractor.bilibili import extract_bilibili
from app.services.extractor.xiaoyuzhou import extract_xiaoyuzhou
from app.services.url_parser import detect_platform
from app.tasks.helpers import update_processing_state


@celery_app.task(bind=True, name="tasks.extract")
def extract_task(self, job_id: str, episode_id: str, url: str, provider_config_dict: dict = None):
    """Extract audio or subtitle from Bilibili/Xiaoyuzhou URL."""
    db = SessionLocal()
    try:
        job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
        episode = db.query(Episode).filter(Episode.id == episode_id).first()

        if not episode:
            raise ValueError(f"Episode {episode_id} not found in database")

        update_processing_state(db, job, episode, ProcessingStatus.EXTRACTING, 0.1, "正在提取内容…")

        work_dir = os.path.join(settings.audio_tmp_dir, episode_id)
        os.makedirs(work_dir, exist_ok=True)

        parsed = detect_platform(url)

        if parsed.platform == Platform.BILIBILI:
            result = asyncio.run(extract_bilibili(url, work_dir))
            episode.title = result.title or episode.title
            episode.author = result.author or episode.author
            episode.cover_url = result.cover_url
            episode.duration_seconds = result.duration_seconds

            if result.subtitle:
                transcript_obj = Transcript(
                    episode_id=episode_id,
                    full_text=result.subtitle.text,
                    segments_json=json.dumps(
                        [
                            {
                                "start_ms": segment.start_ms,
                                "end_ms": segment.end_ms,
                                "text": segment.text,
                            }
                            for segment in result.subtitle.segments
                        ],
                        ensure_ascii=False,
                    ),
                    language=result.subtitle.language,
                    word_count=len(result.subtitle.text.replace(" ", "")),
                )
                db.merge(transcript_obj)
                episode.audio_file_path = None
                db.commit()
                update_processing_state(db, job, episode, ProcessingStatus.SUMMARIZING, 0.7, "正在生成摘要…")
                from app.tasks.summarize_task import summarize_task
                async_result = summarize_task.delay(job_id, episode_id, provider_config_dict)
                if job:
                    job.celery_task_id = async_result.id
                    db.commit()
                return

            episode.audio_file_path = result.audio_local_path

        elif parsed.platform == Platform.XIAOYUZHOU:
            result = asyncio.run(extract_xiaoyuzhou(url, work_dir))
            episode.title = result.title or episode.title
            episode.author = result.author or episode.author
            episode.cover_url = result.cover_url
            episode.duration_seconds = result.duration_seconds
            episode.audio_file_path = result.local_path

        db.commit()
        update_processing_state(db, job, episode, ProcessingStatus.TRANSCRIBING, 0.3, "正在转录音频…")

        from app.tasks.transcribe_task import transcribe_task
        async_result = transcribe_task.delay(job_id, episode_id, provider_config_dict)
        if job:
            job.celery_task_id = async_result.id
            db.commit()

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
            "提取失败",
            str(exc),
        )
        raise
    finally:
        db.close()
