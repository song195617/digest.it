"""
OpenAI-compatible AI service for summary generation and streaming chat.
Supports Gemini (via OpenAI-compat endpoint) and any OpenAI-compatible provider.
"""
import json
import re
from typing import AsyncGenerator
from openai import AsyncOpenAI
from app.services.ai.base import AIService
from app.services.ai.provider_config import ProviderConfig
from app.services.ai._utils import build_chat_context, truncate_transcript, format_transcript_with_timestamps
from app.services.ai.prompts import SUMMARY_SYSTEM_PROMPT, SUMMARY_USER_TEMPLATE, CHAT_SYSTEM_TEMPLATE


class OpenAICompatibleService(AIService):
    def __init__(self, config: ProviderConfig):
        self._client = AsyncOpenAI(
            api_key=config.api_key,
            base_url=config.base_url,
        )
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

        response = await self._client.chat.completions.create(
            model=self._model,
            max_tokens=4096,
            messages=[
                {"role": "system", "content": SUMMARY_SYSTEM_PROMPT},
                {"role": "user", "content": user_message},
            ],
        )

        content = response.choices[0].message.content
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

        messages = [{"role": "system", "content": system_prompt}]
        for msg in chat_history[-20:]:
            messages.append({"role": msg["role"], "content": msg["content"]})
        messages.append({"role": "user", "content": user_message})

        stream = await self._client.chat.completions.create(
            model=self._model,
            max_tokens=2048,
            messages=messages,
            stream=True,
        )

        async for chunk in stream:
            delta = chunk.choices[0].delta.content
            if delta:
                yield delta
