import unittest
from app.services.extractor.bilibili import parse_subtitle_segments


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


if __name__ == "__main__":
    unittest.main()
