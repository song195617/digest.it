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
