import unittest

from app.models.episode import Platform
from app.services.url_parser import detect_platform, extract_supported_url


class UrlParserTests(unittest.TestCase):
    def test_extract_supported_url_from_share_text(self):
        text = "【一口气了解伊朗经济-哔哩哔哩】 https://b23.tv/37gE8mi"

        self.assertEqual(extract_supported_url(text), "https://b23.tv/37gE8mi")

    def test_extract_supported_url_trims_trailing_punctuation(self):
        text = "看这个：https://b23.tv/37gE8mi】"

        self.assertEqual(extract_supported_url(text), "https://b23.tv/37gE8mi")

    def test_detect_platform_supports_share_text(self):
        parsed = detect_platform("【一口气了解伊朗经济-哔哩哔哩】 https://b23.tv/37gE8mi")

        self.assertEqual(parsed.platform, Platform.BILIBILI)
        self.assertEqual(parsed.normalized_url, "https://b23.tv/37gE8mi")


if __name__ == "__main__":
    unittest.main()
