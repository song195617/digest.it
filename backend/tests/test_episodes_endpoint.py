import asyncio
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from fastapi.responses import FileResponse
from starlette.requests import Request
from app.api.v1.endpoints.episodes import delete_episode, get_episode, list_episodes, stream_audio
from app.models.episode import ChatMessage, Episode, Platform, ProcessingJob, ProcessingStatus, Summary, Transcript
from tests.test_support import create_test_session_factory


class EpisodesEndpointTests(unittest.TestCase):
    def setUp(self):
        self.session_factory = create_test_session_factory()

    def make_request(self, range_header: str | None = None) -> Request:
        headers = []
        if range_header is not None:
            headers.append((b"range", range_header.encode("utf-8")))
        return Request({"type": "http", "method": "GET", "headers": headers})

    def test_list_episodes_uses_v1_audio_path_only_when_audio_exists(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            existing_audio = Path(tmp_dir) / "episode-1.mp3"
            existing_audio.write_text("audio")
            missing_audio = Path(tmp_dir) / "missing.mp3"

            with self.session_factory() as db:
                db.add(Episode(
                    id="episode-1",
                    platform=Platform.BILIBILI,
                    original_url="https://www.bilibili.com/video/BV1xx411c7mD",
                    title="有音频",
                    processing_status=ProcessingStatus.COMPLETED,
                    audio_file_path=str(existing_audio),
                ))
                db.add(Episode(
                    id="episode-2",
                    platform=Platform.XIAOYUZHOU,
                    original_url="https://www.xiaoyuzhoufm.com/episode/123",
                    title="音频文件丢失",
                    processing_status=ProcessingStatus.COMPLETED,
                    audio_file_path=str(missing_audio),
                ))
                db.commit()

                response = asyncio.run(list_episodes(db))

            payload = {item.id: item for item in response}
            self.assertEqual(payload["episode-1"].audio_url, "/v1/episodes/episode-1/audio")
            self.assertIsNone(payload["episode-2"].audio_url)

    def test_get_episode_uses_v1_audio_path(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_path = Path(tmp_dir) / "episode-1.mp3"
            audio_path.write_text("audio")

            with self.session_factory() as db:
                db.add(Episode(
                    id="episode-1",
                    platform=Platform.BILIBILI,
                    original_url="https://www.bilibili.com/video/BV1xx411c7mD",
                    title="可播放节目",
                    processing_status=ProcessingStatus.COMPLETED,
                    audio_file_path=str(audio_path),
                ))
                db.commit()

                response = asyncio.run(get_episode("episode-1", db))

            self.assertEqual(response.audio_url, "/v1/episodes/episode-1/audio")

    def test_stream_audio_returns_file_response(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_path = Path(tmp_dir) / "episode-1.mp3"
            audio_path.write_bytes(b"fake-audio")

            with self.session_factory() as db:
                db.add(Episode(
                    id="episode-1",
                    platform=Platform.BILIBILI,
                    original_url="https://www.bilibili.com/video/BV1xx411c7mD",
                    title="可播放节目",
                    processing_status=ProcessingStatus.COMPLETED,
                    audio_file_path=str(audio_path),
                ))
                db.commit()

                response = asyncio.run(stream_audio("episode-1", self.make_request(), db))

            self.assertIsInstance(response, FileResponse)
            self.assertEqual(Path(response.path), audio_path)
            self.assertEqual(response.media_type, "audio/mpeg")
            self.assertEqual(response.filename, "episode-1.mp3")

    def test_stream_audio_honors_range_requests(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            audio_path = Path(tmp_dir) / "episode-1.mp3"
            audio_path.write_bytes(b"0123456789")

            with self.session_factory() as db:
                db.add(Episode(
                    id="episode-1",
                    platform=Platform.BILIBILI,
                    original_url="https://www.bilibili.com/video/BV1xx411c7mD",
                    title="可播放节目",
                    processing_status=ProcessingStatus.COMPLETED,
                    audio_file_path=str(audio_path),
                ))
                db.commit()

                response = asyncio.run(stream_audio("episode-1", self.make_request("bytes=2-5"), db))

            self.assertEqual(response.status_code, 206)
            self.assertEqual(response.headers["content-range"], "bytes 2-5/10")
            self.assertEqual(response.headers["content-length"], "4")

    def test_delete_episode_cascades_related_rows_and_temp_files(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            episode_dir = Path(tmp_dir) / "episode-1"
            episode_dir.mkdir(parents=True)
            (episode_dir / "audio.mp3").write_text("audio")

            with self.session_factory() as db:
                db.add(Episode(
                    id="episode-1",
                    platform=Platform.BILIBILI,
                    original_url="https://www.bilibili.com/video/BV1xx411c7mD",
                    title="处理中…",
                    processing_status=ProcessingStatus.QUEUED,
                ))
                db.add(ProcessingJob(
                    id="job-1",
                    episode_id="episode-1",
                    status=ProcessingStatus.TRANSCRIBING,
                    progress=0.5,
                    current_step="转录中",
                    celery_task_id="celery-1",
                ))
                db.add(Transcript(
                    episode_id="episode-1",
                    full_text="全文",
                    segments_json="[]",
                    language="zh",
                    word_count=2,
                ))
                db.add(Summary(
                    episode_id="episode-1",
                    one_liner="摘要",
                    key_points_json="[]",
                    topics_json="[]",
                    highlights_json="[]",
                    full_summary="完整摘要",
                ))
                db.add(ChatMessage(
                    id="chat-1",
                    session_id="session-1",
                    episode_id="episode-1",
                    role="user",
                    content="hello",
                ))
                db.commit()

            with patch("app.api.v1.endpoints.episodes.settings.audio_tmp_dir", tmp_dir), \
                patch("app.api.v1.endpoints.episodes.celery_app.control.revoke") as revoke_mock:
                with self.session_factory() as db:
                    response = asyncio.run(delete_episode("episode-1", db))

            self.assertEqual(response, {"ok": True})
            revoke_mock.assert_called_once_with("celery-1", terminate=False)
            self.assertFalse(episode_dir.exists())

            with self.session_factory() as db:
                self.assertIsNone(db.query(Episode).filter(Episode.id == "episode-1").first())
                self.assertIsNone(db.query(ProcessingJob).filter(ProcessingJob.episode_id == "episode-1").first())
                self.assertIsNone(db.query(Transcript).filter(Transcript.episode_id == "episode-1").first())
                self.assertIsNone(db.query(Summary).filter(Summary.episode_id == "episode-1").first())
                self.assertIsNone(db.query(ChatMessage).filter(ChatMessage.episode_id == "episode-1").first())


if __name__ == "__main__":
    unittest.main()
