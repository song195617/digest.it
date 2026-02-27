"""
Celery task: transcribe audio using OpenAI Whisper API.
"""
import asyncio
import json
import os
from app.core.celery_app import celery_app
from app.core.database import SessionLocal
from app.models.episode import Episode, ProcessingJob, ProcessingStatus, Transcript
from app.services.transcription.whisper_service import transcribe_audio
from app.config import settings


@celery_app.task(bind=True, name="tasks.transcribe")
def transcribe_task(self, job_id: str, episode_id: str):
    """Transcribe audio using Whisper API with chunking for long files."""
    db = SessionLocal()
    try:
        job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
        episode = db.query(Episode).filter(Episode.id == episode_id).first()

        if not episode or not episode.audio_file_path:
            raise ValueError("No audio file to transcribe")

        work_dir = os.path.join(settings.audio_tmp_dir, episode_id, "chunks")
        os.makedirs(work_dir, exist_ok=True)

        total_chunks = [0]

        async def progress_cb(done, total):
            total_chunks[0] = total
            progress = 0.3 + (done / total) * 0.4  # 30%-70% range
            step = f"正在转录第 {done}/{total} 段…"
            if job:
                job.progress = progress
                job.current_step = step
            db.commit()

        result = asyncio.run(
            transcribe_audio(
                audio_path=episode.audio_file_path,
                work_dir=work_dir,
                language="zh",
                progress_callback=progress_cb,
            )
        )

        # Store transcript
        segments_data = [
            {"start_ms": s.start_ms, "end_ms": s.end_ms, "text": s.text}
            for s in result.segments
        ]
        transcript_obj = Transcript(
            episode_id=episode_id,
            full_text=result.full_text,
            segments_json=json.dumps(segments_data, ensure_ascii=False),
            language=result.language,
            word_count=result.word_count,
        )
        db.merge(transcript_obj)
        episode.duration_seconds = episode.duration_seconds or (result.segments[-1].end_ms // 1000 if result.segments else 0)

        if job:
            job.status = ProcessingStatus.SUMMARIZING
            job.progress = 0.72
            job.current_step = "正在生成摘要…"
        db.commit()

        # Chain to summarize task
        from app.tasks.summarize_task import summarize_task
        summarize_task.delay(job_id, episode_id)

    except Exception as e:
        db.rollback()
        job = db.query(ProcessingJob).filter(ProcessingJob.id == job_id).first()
        episode = db.query(Episode).filter(Episode.id == episode_id).first()
        if job:
            job.status = ProcessingStatus.FAILED
            job.error_message = str(e)
        if episode:
            episode.processing_status = ProcessingStatus.FAILED
            episode.error_message = str(e)
        db.commit()
        raise
    finally:
        db.close()
