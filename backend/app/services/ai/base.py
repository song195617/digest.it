from abc import ABC, abstractmethod
from typing import AsyncGenerator


class AIService(ABC):
    """Abstract base for all AI summarization/chat providers."""

    @abstractmethod
    async def generate_summary(
        self,
        title: str,
        author: str,
        full_text: str,
        segments: list[dict],
    ) -> dict:
        """Return a structured summary dict matching the JSON schema in prompts.py."""
        ...

    @abstractmethod
    def stream_chat(
        self,
        title: str,
        author: str,
        full_text: str,
        segments: list[dict],
        chat_history: list[dict],
        user_message: str,
    ) -> AsyncGenerator[str, None]:
        """Yield text deltas for streaming chat."""
        ...
