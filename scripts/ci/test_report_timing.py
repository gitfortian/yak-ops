import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("report_timing", Path(__file__).with_name("report_timing.py"))
subject = importlib.util.module_from_spec(spec)
spec.loader.exec_module(subject)


class TimingReportTest(unittest.TestCase):
    def test_queue_and_execution_are_distinct(self):
        job = {
            "name": "Affected Backend Tests / Backend Tests (cdc)",
            "created_at": "2026-10-23T08:00:00Z",
            "started_at": "2026-10-23T08:02:00Z",
            "completed_at": "2026-10-23T08:05:20Z",
            "conclusion": "success",
        }
        report = subject.format_report([job])
        self.assertIn("2m 00s", report)
        self.assertIn("3m 20s", report)
        self.assertIn("success", report)

    def test_invalid_and_incomplete_jobs_do_not_crash(self):
        self.assertIsNone(subject.seconds_between("bad", "2026-10-23T08:05:20Z"))
        self.assertIn("No completed jobs", subject.format_report([{"name": "running"}]))
        self.assertEqual("0s", subject.duration(0))
        self.assertEqual("-", subject.duration(None))


if __name__ == "__main__":
    unittest.main()
