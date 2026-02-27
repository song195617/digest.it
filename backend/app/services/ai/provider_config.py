from dataclasses import dataclass

PROVIDER_CLAUDE = "claude"
PROVIDER_GEMINI = "gemini"
PROVIDER_OPENAI_COMPATIBLE = "openai_compatible"

GEMINI_DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/"
GEMINI_DEFAULT_MODEL = "gemini-2.0-flash"
CLAUDE_DEFAULT_MODEL = "claude-sonnet-4-6"


@dataclass
class ProviderConfig:
    provider: str          # "claude" | "gemini" | "openai_compatible"
    api_key: str           # caller-supplied API key
    model: str             # model name, with provider-specific defaults applied
    base_url: str | None   # only used for openai_compatible; None for claude/gemini

    @classmethod
    def from_headers(
        cls,
        provider: str | None,
        api_key: str | None,
        model: str | None,
        base_url: str | None,
        fallback_claude_key: str = "",
    ) -> "ProviderConfig":
        """Construct from HTTP request headers, applying defaults."""
        provider = (provider or PROVIDER_CLAUDE).lower().strip()
        api_key = api_key or fallback_claude_key

        if provider == PROVIDER_GEMINI:
            resolved_model = model or GEMINI_DEFAULT_MODEL
            resolved_base_url = GEMINI_DEFAULT_BASE_URL
        elif provider == PROVIDER_OPENAI_COMPATIBLE:
            resolved_model = model or "gpt-4o"
            resolved_base_url = base_url
        else:
            provider = PROVIDER_CLAUDE
            resolved_model = model or CLAUDE_DEFAULT_MODEL
            resolved_base_url = None

        return cls(
            provider=provider,
            api_key=api_key,
            model=resolved_model,
            base_url=resolved_base_url,
        )

    def to_dict(self) -> dict:
        return {
            "provider": self.provider,
            "api_key": self.api_key,
            "model": self.model,
            "base_url": self.base_url,
        }

    @classmethod
    def from_dict(cls, d: dict) -> "ProviderConfig":
        return cls(
            provider=d["provider"],
            api_key=d["api_key"],
            model=d["model"],
            base_url=d.get("base_url"),
        )
