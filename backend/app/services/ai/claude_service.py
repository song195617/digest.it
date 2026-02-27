"""
Claude API integration for summary generation and streaming chat.
"""
import json
import re
from typing import AsyncGenerator
from anthropic import AsyncAnthropic
from app.config import settings
from app.services.ai.prompts import SUMMARY_SYSTEM_PROMPT, SUMMARY_USER_TEMPLATE, CHAT_SYSTEM_TEMPLATE

MAX_TRANSCRIPT_CHARS = 80000  # Claude's context window safety limit


def _truncate_transcript(text: str) -> str:
    if len(text) <= MAX_TRANSCRIPT_CHARS:
        return text
    # Truncate middle, keep beginning and end
    half = MAX_TRANSCRIPT_CHARS // 2
    return text[:half] + "\n\n[... 中间内容已省略 ...]\n\n" + text[-half:]


def _format_transcript_with_timestamps(segments: list[dict]) -> str:
    lines = []
    for seg in segments:
        ms = seg.get("start_ms", 0)
        mins = ms // 60000
        secs = (ms % 60000) // 1000
        lines.append(f"[{mins:02d}:{secs:02d}] {seg.get('text', '')}")
    return "\n".join(lines)


async def generate_summary(
    title: str,
    author: str,
    full_text: str,
    segments: list[dict],
) -> dict:
    """Generate structured summary using Claude API."""
    client = AsyncAnthropic(api_key=settings.claude_api_key)
    transcript = _truncate_transcript(full_text)

    user_message = SUMMARY_USER_TEMPLATE.format(
        title=title,
        author=author,
        transcript=transcript,
    )

    message = await client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=4096,
        system=SUMMARY_SYSTEM_PROMPT,
        messages=[{"role": "user", "content": user_message}],
    )

    content = message.content[0].text
    # Extract JSON from response
    json_match = re.search(r"\{.*\}", content, re.DOTALL)
    if json_match:
        return json.loads(json_match.group())
    return json.loads(content)


async def stream_chat(
    title: str,
    author: str,
    full_text: str,
    segments: list[dict],
    chat_history: list[dict],
    user_message: str,
) -> AsyncGenerator[str, None]:
    """
    Stream chat response from Claude using transcript as context.
    Yields text deltas for SSE/WebSocket streaming.
    """
    client = AsyncAnthropic(api_key=settings.claude_api_key)

    transcript_with_ts = _format_transcript_with_timestamps(segments)
    truncated_transcript = _truncate_transcript(transcript_with_ts)

    system_prompt = CHAT_SYSTEM_TEMPLATE.format(
        title=title,
        author=author,
        transcript_with_timestamps=truncated_transcript,
    )

    messages = []
    for msg in chat_history[-20:]:  # Keep last 20 messages to manage context
        messages.append({
            "role": msg["role"],
            "content": msg["content"],
        })
    messages.append({"role": "user", "content": user_message})

    async with client.messages.stream(
        model="claude-sonnet-4-6",
        max_tokens=2048,
        system=system_prompt,
        messages=messages,
    ) as stream:
        async for text in stream.text_stream:
            yield text
