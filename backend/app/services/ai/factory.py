from app.services.ai.base import AIService
from app.services.ai.provider_config import ProviderConfig, PROVIDER_CLAUDE


def get_ai_service(config: ProviderConfig) -> AIService:
    """Return the correct AIService implementation for the given provider config."""
    if config.provider == PROVIDER_CLAUDE:
        from app.services.ai.claude_service import ClaudeService
        return ClaudeService(config)
    else:
        from app.services.ai.openai_service import OpenAICompatibleService
        return OpenAICompatibleService(config)
