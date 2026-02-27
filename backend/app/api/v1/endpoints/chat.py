"""
Chat endpoint: WebSocket for streaming AI responses.
"""
import json
import uuid
from fastapi import APIRouter, Depends, WebSocket, WebSocketDisconnect, Header
from sqlalchemy.orm import Session
from app.core.database import get_db
from app.models.episode import Episode, Transcript, Summary, ChatMessage
from app.services.ai.factory import get_ai_service
from app.services.ai.provider_config import ProviderConfig
from app.config import settings

router = APIRouter(prefix="/v1/ws", tags=["chat"])


@router.websocket("/chat/{session_id}")
async def chat_websocket(
    websocket: WebSocket,
    session_id: str,
    db: Session = Depends(get_db),
    x_ai_provider: str | None = Header(default=None, alias="X-AI-Provider"),
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    x_ai_model: str | None = Header(default=None, alias="X-AI-Model"),
    x_ai_base_url: str | None = Header(default=None, alias="X-AI-Base-URL"),
):
    await websocket.accept()

    provider_config = ProviderConfig.from_headers(
        provider=x_ai_provider,
        api_key=x_api_key,
        model=x_ai_model,
        base_url=x_ai_base_url,
        fallback_claude_key=settings.claude_api_key,
    )
    ai_service = get_ai_service(provider_config)

    try:
        while True:
            data = await websocket.receive_json()
            episode_id = data.get("episode_id")
            user_message = data.get("message", "").strip()

            if not episode_id or not user_message:
                await websocket.send_json({"error": "Missing episode_id or message"})
                continue

            episode = db.query(Episode).filter(Episode.id == episode_id).first()
            transcript = db.query(Transcript).filter(Transcript.episode_id == episode_id).first()

            if not episode or not transcript:
                await websocket.send_json({"error": "Episode or transcript not found"})
                continue

            # Resolve session id
            resolved_session = session_id if session_id != "new" else str(uuid.uuid4())

            # Load chat history
            history = db.query(ChatMessage).filter(
                ChatMessage.episode_id == episode_id,
                ChatMessage.session_id == resolved_session,
            ).order_by(ChatMessage.created_at).all()

            history_dicts = [{"role": m.role, "content": m.content} for m in history]

            # Save user message
            user_msg = ChatMessage(
                id=str(uuid.uuid4()),
                session_id=resolved_session,
                episode_id=episode_id,
                role="user",
                content=user_message,
            )
            db.add(user_msg)
            db.commit()

            # Stream assistant response
            segments = json.loads(transcript.segments_json) if transcript.segments_json else []
            full_response = ""

            async for delta in ai_service.stream_chat(
                title=episode.title,
                author=episode.author,
                full_text=transcript.full_text,
                segments=segments,
                chat_history=history_dicts,
                user_message=user_message,
            ):
                full_response += delta
                await websocket.send_json({
                    "delta": delta,
                    "done": False,
                    "session_id": resolved_session,
                    "referenced_timestamps": [],
                })

            # Send done signal
            await websocket.send_json({
                "delta": "",
                "done": True,
                "session_id": resolved_session,
                "referenced_timestamps": _extract_timestamps(full_response),
            })

            # Save assistant message
            assistant_msg = ChatMessage(
                id=str(uuid.uuid4()),
                session_id=resolved_session,
                episode_id=episode_id,
                role="assistant",
                content=full_response,
            )
            db.add(assistant_msg)
            db.commit()

    except WebSocketDisconnect:
        pass
    except Exception as e:
        try:
            await websocket.send_json({"error": str(e), "done": True})
        except Exception:
            pass


def _extract_timestamps(text: str) -> list[int]:
    """Extract [MM:SS] timestamps mentioned in the response."""
    import re
    pattern = re.compile(r"\[(\d{2}):(\d{2})\]")
    result = []
    for m in pattern.finditer(text):
        mins, secs = int(m.group(1)), int(m.group(2))
        result.append((mins * 60 + secs) * 1000)
    return result
