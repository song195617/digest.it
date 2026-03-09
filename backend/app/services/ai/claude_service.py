"""
Claude API integration for summary generation and streaming chat.
"""
import json
import re
from typing import AsyncGenerator
from anthropic import AsyncAnthropic
from app.services.ai.base import AIService
from app.services.ai.provider_config import ProviderConfig
from app.services.ai._utils import build_chat_context, truncate_transcript, format_transcript_with_timestamps
from app.services.ai.prompts import SUMMARY_SYSTEM_PROMPT, SUMMARY_USER_TEMPLATE, CHAT_SYSTEM_TEMPLATE


class ClaudeService(AIService):
    def __init__(self, config: ProviderConfig):
        self._client = AsyncAnthropic(api_key=config.api_key)
        self._model = config.model

    async def generate_summary(
        self,
        title: str,
        author: str,
        full_text: str,
        segments: list[dict],
    ) -> dict:
        timestamped = format_transcript_with_timestamps(segments)
        transcript = truncate_transcript(timestamped if timestamped.strip() else full_text)
        user_message = SUMMARY_USER_TEMPLATE.format(
            title=title,
            author=author,
            transcript=transcript,
        )

        message = await self._client.messages.create(
            model=self._model,
            max_tokens=4096,
            system=SUMMARY_SYSTEM_PROMPT,
            messages=[{"role": "user", "content": user_message}],
        )

        content = message.content[0].text
        json_match = re.search(r"\{.*\}", content, re.DOTALL)
        if json_match:
            return json.loads(json_match.group())
        return json.loads(content)

    async def stream_chat(
        self,
        title: str,
        author: str,
        full_text: str,
        segments: list[dict],
        chat_history: list[dict],
        user_message: str,
    ) -> AsyncGenerator[str, None]:
        transcript_context = build_chat_context(full_text, segments)

        system_prompt = CHAT_SYSTEM_TEMPLATE.format(
            title=title,
            author=author,
            transcript_with_timestamps=transcript_context,
        )

        messages = []
        for msg in chat_history[-20:]:
            messages.append({"role": msg["role"], "content": msg["content"]})
        messages.append({"role": "user", "content": user_message})

        async with self._client.messages.stream(
            model=self._model,
            max_tokens=2048,
            system=system_prompt,
            messages=messages,
        ) as stream:
            async for text in stream.text_stream:
                yield text


from app.config import settings
from app.services.ai.provider_config import PROVIDER_CLAUDE, CLAUDE_DEFAULT_MODEL


async def generate_summary(
    title: str,
    author: str,
    full_text: str,
    segments: list[dict],
) -> dict:
    cfg = ProviderConfig(PROVIDER_CLAUDE, settings.claude_api_key, CLAUDE_DEFAULT_MODEL, None)
    return await ClaudeService(cfg).generate_summary(title, author, full_text, segments)


async def stream_chat(
    title: str,
    author: str,
    full_text: str,
    segments: list[dict],
    chat_history: list[dict],
    user_message: str,
) -> AsyncGenerator[str, None]:
    cfg = ProviderConfig(PROVIDER_CLAUDE, settings.claude_api_key, CLAUDE_DEFAULT_MODEL, None)
    async for delta in ClaudeService(cfg).stream_chat(
        title, author, full_text, segments, chat_history, user_message
    ):
        yield delta
