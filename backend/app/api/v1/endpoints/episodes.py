import json
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.models.episode import Episode, Transcript, Summary

router = APIRouter(prefix="/v1/episodes", tags=["episodes"])


class EpisodeResponse(BaseModel):
    id: str
    platform: str
    original_url: str
    title: str
    author: str
    cover_url: str | None
    duration_seconds: int
    created_at: str
    processing_status: str
    error_message: str | None


class SegmentDto(BaseModel):
    start_ms: int
    end_ms: int
    text: str


class TranscriptResponse(BaseModel):
    episode_id: str
    full_text: str
    segments: list[SegmentDto]
    language: str
    word_count: int


class HighlightDto(BaseModel):
    quote: str
    timestamp_ms: int
    context: str


class SummaryResponse(BaseModel):
    episode_id: str
    one_liner: str
    key_points: list[str]
    topics: list[str]
    highlights: list[HighlightDto]
    full_summary: str


def _episode_to_response(ep: Episode) -> EpisodeResponse:
    return EpisodeResponse(
        id=ep.id,
        platform=ep.platform.value,
        original_url=ep.original_url,
        title=ep.title,
        author=ep.author,
        cover_url=ep.cover_url,
        duration_seconds=ep.duration_seconds,
        created_at=ep.created_at.isoformat(),
        processing_status=ep.processing_status.value,
        error_message=ep.error_message,
    )


@router.get("", response_model=list[EpisodeResponse])
async def list_episodes(db: Session = Depends(get_db)):
    episodes = db.query(Episode).order_by(Episode.created_at.desc()).all()
    return [_episode_to_response(ep) for ep in episodes]


@router.get("/{episode_id}", response_model=EpisodeResponse)
async def get_episode(episode_id: str, db: Session = Depends(get_db)):
    ep = db.query(Episode).filter(Episode.id == episode_id).first()
    if not ep:
        raise HTTPException(status_code=404, detail="Episode not found")
    return _episode_to_response(ep)


@router.delete("/{episode_id}")
async def delete_episode(episode_id: str, db: Session = Depends(get_db)):
    ep = db.query(Episode).filter(Episode.id == episode_id).first()
    if not ep:
        raise HTTPException(status_code=404, detail="Episode not found")
    db.delete(ep)
    db.commit()
    return {"ok": True}


@router.get("/{episode_id}/transcript", response_model=TranscriptResponse)
async def get_transcript(episode_id: str, db: Session = Depends(get_db)):
    t = db.query(Transcript).filter(Transcript.episode_id == episode_id).first()
    if not t:
        raise HTTPException(status_code=404, detail="Transcript not ready yet")
    segments = json.loads(t.segments_json) if t.segments_json else []
    return TranscriptResponse(
        episode_id=t.episode_id,
        full_text=t.full_text,
        segments=[SegmentDto(**s) for s in segments],
        language=t.language,
        word_count=t.word_count,
    )


@router.get("/{episode_id}/summary", response_model=SummaryResponse)
async def get_summary(episode_id: str, db: Session = Depends(get_db)):
    s = db.query(Summary).filter(Summary.episode_id == episode_id).first()
    if not s:
        raise HTTPException(status_code=404, detail="Summary not ready yet")
    return SummaryResponse(
        episode_id=s.episode_id,
        one_liner=s.one_liner,
        key_points=json.loads(s.key_points_json) if s.key_points_json else [],
        topics=json.loads(s.topics_json) if s.topics_json else [],
        highlights=[HighlightDto(**h) for h in (json.loads(s.highlights_json) if s.highlights_json else [])],
        full_summary=s.full_summary,
    )
