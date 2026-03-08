import unittest
from types import SimpleNamespace
from unittest.mock import patch
from app.models.episode import Episode, ProcessingJob, ProcessingStatus
from tests.test_support import create_test_client


class JobsEndpointTests(unittest.TestCase):
    def setUp(self):
        self.client, self.session_factory = create_test_client()

    @patch("app.api.v1.endpoints.jobs.extract_task.delay", return_value=SimpleNamespace(id="celery-1"))
    def test_submit_url_reuses_existing_active_job(self, delay_mock):
        first = self.client.post("/v1/jobs", json={"url": "https://www.bilibili.com/video/BV1xx411c7mD"})
        second = self.client.post("/v1/jobs", json={"url": "https://www.bilibili.com/video/BV1xx411c7mD?p=1"})

        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(first.json()["job_id"], second.json()["job_id"])
        self.assertEqual(delay_mock.call_count, 1)

    @patch("app.api.v1.endpoints.jobs.extract_task.delay", side_effect=RuntimeError("broker down"))
    def test_submit_url_marks_job_and_episode_failed_when_enqueue_fails(self, _delay_mock):
        response = self.client.post("/v1/jobs", json={"url": "https://www.xiaoyuzhoufm.com/episode/64c7f123abcd123456789abc"})
        data = response.json()

        self.assertEqual(response.status_code, 200)
        self.assertEqual(data["status"], ProcessingStatus.FAILED.value)
        self.assertIn("broker down", data["error_message"])

        with self.session_factory() as db:
            job = db.query(ProcessingJob).filter(ProcessingJob.id == data["job_id"]).first()
            episode = db.query(Episode).filter(Episode.id == data["episode_id"]).first()
            self.assertEqual(job.status, ProcessingStatus.FAILED)
            self.assertEqual(episode.processing_status, ProcessingStatus.FAILED)
            self.assertIn("broker down", episode.error_message)


if __name__ == "__main__":
    unittest.main()

