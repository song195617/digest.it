import unittest
import asyncio
from unittest.mock import patch
from app.api.v1.endpoints.jobs import SubmitUrlRequest, submit_url
from app.models.episode import Episode, ProcessingJob, ProcessingStatus
from tests.test_support import create_test_session_factory


class JobsEndpointTests(unittest.TestCase):
    def setUp(self):
        self.session_factory = create_test_session_factory()

    @patch("app.api.v1.endpoints.jobs.dispatch_extract_job")
    def test_submit_url_reuses_existing_active_job(self, dispatch_mock):
        with self.session_factory() as db:
            first = asyncio.run(
                submit_url(
                    SubmitUrlRequest(url="https://www.bilibili.com/video/BV1xx411c7mD"),
                    db,
                    None,
                    None,
                    None,
                    None,
                )
            )
            second = asyncio.run(
                submit_url(
                    SubmitUrlRequest(url="https://www.bilibili.com/video/BV1xx411c7mD?p=1"),
                    db,
                    None,
                    None,
                    None,
                    None,
                )
            )

        self.assertEqual(first.job_id, second.job_id)
        self.assertEqual(dispatch_mock.call_count, 1)

    @patch("app.api.v1.endpoints.jobs.dispatch_extract_job", side_effect=RuntimeError("broker down"))
    def test_submit_url_marks_job_and_episode_failed_when_enqueue_fails(self, _dispatch_mock):
        with self.session_factory() as db:
            response = asyncio.run(
                submit_url(
                    SubmitUrlRequest(url="https://www.xiaoyuzhoufm.com/episode/64c7f123abcd123456789abc"),
                    db,
                    None,
                    None,
                    None,
                    None,
                )
            )

        self.assertEqual(response.status, ProcessingStatus.FAILED.value)
        self.assertIn("broker down", response.error_message)

        with self.session_factory() as db:
            job = db.query(ProcessingJob).filter(ProcessingJob.id == response.job_id).first()
            episode = db.query(Episode).filter(Episode.id == response.episode_id).first()
            self.assertEqual(job.status, ProcessingStatus.FAILED)
            self.assertEqual(episode.processing_status, ProcessingStatus.FAILED)
            self.assertIn("broker down", episode.error_message)


if __name__ == "__main__":
    unittest.main()
