import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from app.models.episode import ChatMessage, Episode, Platform, ProcessingJob, ProcessingStatus, Summary, Transcript
from tests.test_support import create_test_client


class EpisodesEndpointTests(unittest.TestCase):
    def setUp(self):
        self.client, self.session_factory = create_test_client()

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

            response = self.client.get("/v1/episodes/episode-1")

            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json()["audio_url"], "/v1/episodes/episode-1/audio")

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
                response = self.client.delete("/v1/episodes/episode-1")

            self.assertEqual(response.status_code, 200)
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
