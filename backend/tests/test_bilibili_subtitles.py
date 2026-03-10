import asyncio
import unittest
from unittest.mock import patch
from app.services.extractor.bilibili import parse_subtitle_segments
from app.services.extractor.bilibili import fetch_bilibili_metadata


class BilibiliSubtitleTests(unittest.TestCase):
    def test_parse_subtitle_segments_generates_millisecond_ranges(self):
        body = [
            {"from": 1.25, "to": 3.5, "content": "第一句"},
            {"from": 3601.0, "to": 3605.2, "content": "第二句"},
        ]

        segments = parse_subtitle_segments(body)

        self.assertEqual(len(segments), 2)
        self.assertEqual(segments[0].start_ms, 1250)
        self.assertEqual(segments[0].end_ms, 3500)
        self.assertEqual(segments[1].start_ms, 3601000)
        self.assertEqual(segments[1].end_ms, 3605200)

    def test_fetch_bilibili_metadata_falls_back_to_ytdlp_when_api_returns_empty(self):
        async def run_test():
            with patch(
                "app.services.extractor.bilibili._get_video_info",
                return_value={},
            ), patch(
                "app.services.extractor.bilibili._get_video_info_from_ytdlp",
                return_value={"title": "测试标题", "pic": "https://image.example/cover.jpg"},
            ):
                return await fetch_bilibili_metadata("https://b23.tv/test")

        metadata = asyncio.run(run_test())

        self.assertEqual(metadata["title"], "测试标题")
        self.assertEqual(metadata["pic"], "https://image.example/cover.jpg")


if __name__ == "__main__":
    unittest.main()
