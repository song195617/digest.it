import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Integer, DateTime, Text, Float, Enum as SAEnum
from sqlalchemy.orm import declarative_base
import enum

Base = declarative_base()


class Platform(str, enum.Enum):
    BILIBILI = "BILIBILI"
    XIAOYUZHOU = "XIAOYUZHOU"
    UNKNOWN = "UNKNOWN"


class ProcessingStatus(str, enum.Enum):
    QUEUED = "QUEUED"
    EXTRACTING = "EXTRACTING"
    TRANSCRIBING = "TRANSCRIBING"
    SUMMARIZING = "SUMMARIZING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class Episode(Base):
    __tablename__ = "episodes"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    platform = Column(SAEnum(Platform), nullable=False)
    original_url = Column(String, nullable=False)
    title = Column(String, default="")
    author = Column(String, default="")
    cover_url = Column(String, nullable=True)
    duration_seconds = Column(Integer, default=0)
    audio_file_path = Column(String, nullable=True)
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    processing_status = Column(SAEnum(ProcessingStatus), default=ProcessingStatus.QUEUED)
    error_message = Column(Text, nullable=True)


class ProcessingJob(Base):
    __tablename__ = "processing_jobs"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    episode_id = Column(String, nullable=True)
    celery_task_id = Column(String, nullable=True)
    status = Column(SAEnum(ProcessingStatus), default=ProcessingStatus.QUEUED)
    progress = Column(Float, default=0.0)
    current_step = Column(String, default="等待中")
    error_message = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))


class Transcript(Base):
    __tablename__ = "transcripts"

    episode_id = Column(String, primary_key=True)
    full_text = Column(Text, nullable=False)
    segments_json = Column(Text, nullable=False, default="[]")
    language = Column(String, default="zh")
    word_count = Column(Integer, default=0)


class Summary(Base):
    __tablename__ = "summaries"

    episode_id = Column(String, primary_key=True)
    one_liner = Column(Text, default="")
    key_points_json = Column(Text, default="[]")
    topics_json = Column(Text, default="[]")
    highlights_json = Column(Text, default="[]")
    full_summary = Column(Text, default="")


class ChatMessage(Base):
    __tablename__ = "chat_messages"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    session_id = Column(String, nullable=False, index=True)
    episode_id = Column(String, nullable=False, index=True)
    role = Column(String, nullable=False)  # "user" or "assistant"
    content = Column(Text, nullable=False)
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
