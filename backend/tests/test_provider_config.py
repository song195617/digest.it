import unittest
from app.services.ai.provider_config import (
    ProviderConfig,
    PROVIDER_CLAUDE,
    PROVIDER_GEMINI,
    PROVIDER_OPENAI_COMPATIBLE,
    GEMINI_DEFAULT_BASE_URL,
    GEMINI_DEFAULT_MODEL,
    CLAUDE_DEFAULT_MODEL,
)


class ProviderConfigTests(unittest.TestCase):
    def test_defaults_to_claude_when_provider_missing(self):
        config = ProviderConfig.from_headers(None, None, None, None, fallback_claude_key="fallback")
        self.assertEqual(config.provider, PROVIDER_CLAUDE)
        self.assertEqual(config.api_key, "fallback")
        self.assertEqual(config.model, CLAUDE_DEFAULT_MODEL)
        self.assertIsNone(config.base_url)

    def test_gemini_sets_openai_compat_base_url(self):
        config = ProviderConfig.from_headers(PROVIDER_GEMINI, "g-key", None, None)
        self.assertEqual(config.provider, PROVIDER_GEMINI)
        self.assertEqual(config.api_key, "g-key")
        self.assertEqual(config.model, GEMINI_DEFAULT_MODEL)
        self.assertEqual(config.base_url, GEMINI_DEFAULT_BASE_URL)

    def test_openai_compatible_respects_custom_values(self):
        config = ProviderConfig.from_headers(
            PROVIDER_OPENAI_COMPATIBLE,
            "o-key",
            "gpt-custom",
            "https://example.com/v1",
        )
        self.assertEqual(config.provider, PROVIDER_OPENAI_COMPATIBLE)
        self.assertEqual(config.api_key, "o-key")
        self.assertEqual(config.model, "gpt-custom")
        self.assertEqual(config.base_url, "https://example.com/v1")


if __name__ == "__main__":
    unittest.main()
