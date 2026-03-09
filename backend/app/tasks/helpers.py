from app.models.episode import Episode, ProcessingJob, ProcessingStatus


def update_processing_state(
    db,
    job: ProcessingJob | None,
    episode: Episode | None,
    status: ProcessingStatus,
    progress: float,
    step: str,
    error_message: str | None = None,
) -> None:
    if job:
        job.status = status
        job.progress = progress
        job.current_step = step
        job.error_message = error_message
    if episode:
        episode.processing_status = status
        episode.error_message = error_message
    db.commit()


def dispatch_extract_job(db, job: ProcessingJob, episode: Episode, url: str, provider_config_dict: dict | None) -> None:
    from app.tasks.extract_task import extract_task

    async_result = extract_task.delay(job.id, episode.id, url, provider_config_dict)
    job.celery_task_id = async_result.id
    db.commit()
