"""
Celery task: transcribe audio using local faster-whisper.
"""
import asyncio
import json
import os
from app.config import settings
from app.core.celery_app import celery_app
from app.core.database import SessionLocal
from app.models.episode import Episode, ProcessingJob, ProcessingStatus, Transcript
from app.services.transcription.whisper_service import transcribe_audio
from app.tasks.helpers import update_processing_state


@celery_app.task(bind=True, name="tasks.transcribe")
def transcribe_task(self, job_id: str, episode_id: str, provider_config_dict: dict = None):
    """Transcribe audio using faster-whisper with progress updates."""
    db = SessionLocal()
    try:
        job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
        episode = db.query(Episode).filter(Episode.id == episode_id).first()

        if not episode or not episode.audio_file_path:
            raise ValueError("No audio file to transcribe")

        work_dir = os.path.join(settings.audio_tmp_dir, episode_id, "chunks")
        os.makedirs(work_dir, exist_ok=True)

        async def progress_cb(done, total):
            if not job:
                return
            progress = 0.3 + (done / max(total, 1)) * 0.4
            job.progress = progress
            job.current_step = f"正在转录第 {done}/{total} 段…"
            if episode:
                episode.processing_status = ProcessingStatus.TRANSCRIBING
            db.commit()

        result = asyncio.run(
            transcribe_audio(
                audio_path=episode.audio_file_path,
                work_dir=work_dir,
                language="zh",
                progress_callback=progress_cb,
            )
        )

        segments_data = [
            {"start_ms": segment.start_ms, "end_ms": segment.end_ms, "text": segment.text}
            for segment in result.segments
        ]
        transcript_obj = Transcript(
            episode_id=episode_id,
            full_text=result.full_text,
            segments_json=json.dumps(segments_data, ensure_ascii=False),
            language=result.language,
            word_count=result.word_count,
        )
        db.merge(transcript_obj)
        episode.duration_seconds = episode.duration_seconds or (
            result.segments[-1].end_ms // 1000 if result.segments else 0
        )
        db.commit()

        update_processing_state(db, job, episode, ProcessingStatus.SUMMARIZING, 0.72, "正在生成摘要…")

        from app.tasks.summarize_task import summarize_task
        async_result = summarize_task.delay(job_id, episode_id, provider_config_dict)
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
            "转录失败",
            str(exc),
        )
        raise
    finally:
        db.close()
