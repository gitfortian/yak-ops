"""Backend E2E suite routing and required check contracts, with no Docker dependency."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import backend_e2e_scope as scope

ROOT = Path(__file__).resolve().parents[2]


class BackendSelectionTest(unittest.TestCase):
    def test_frontend_and_docs_do_not_trigger_backend_e2e(self):
        for path in ("AGENTS.md", "DEVELOPMENT.md", "yak-ops-ui/apps/web/app/data-sync/index.tsx",
                     "yak-ops-ui/tests/e2e/run.py", ".github/workflows/ui-acceptance.yml"):
            self.assertEqual([], scope.classify([path])["suites"])

    def test_java_suites_are_routed_by_owner(self):
        for name, suite in {
            "TaskExecutionIT.java": "batch", "MultiTableBatchIT.java": "batch",
            "OfflineIncrementalIT.java": "batch", "ScheduleIT.java": "schedule",
            "DatabaseRealtimeIT.java": "realtime", "ProcessRecoveryIT.java": "realtime",
            "SchemaMigrationIT.java": "schema",
        }.items():
            with self.subTest(name=name):
                self.assertEqual([suite], scope.classify([scope.JAVA + name])["suites"])

    def test_shared_infrastructure_selects_full_backend_e2e(self):
        for path in ("pom.xml", "scripts/backend-e2e/run.sh", "scripts/ci/backend_e2e_scope.py",
                     ".github/workflows/backend-e2e.yml",
                     scope.JAVA + "BackendE2EEnvironment.java",
                     "yak-flow/yak-flow-runtime/Checkpoint.java"):
            self.assertEqual(["full"], scope.classify([path])["suites"])

    def test_missing_comparison_is_conservative(self):
        for error in (ValueError(), OSError(), subprocess.TimeoutExpired("git", 30)):
            with patch.object(scope, "changed_paths", side_effect=error):
                self.assertEqual(["full"], scope.select("auto", "pull_request", "", "")["suites"])
        with self.assertRaises(ValueError):
            scope.select("browser", "workflow_dispatch", "", "")

    def test_git_diff_includes_both_sides_of_rename(self):
        with tempfile.TemporaryDirectory() as work:
            base = Path(work)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=base,
                                               stderr=subprocess.DEVNULL).decode().strip()
            git("init", "-q")
            git("config", "user.name", "CI")
            git("config", "user.email", "ci@example.invalid")
            (base / "A.java").write_text("class A {}")
            git("add", ".")
            git("commit", "-qm", "base")
            first = git("rev-parse", "HEAD")
            git("mv", "A.java", "A.md")
            git("commit", "-qm", "rename")
            last = git("rev-parse", "HEAD")
            original = Path.cwd()
            try:
                os.chdir(base)
                self.assertEqual({"A.java", "A.md"}, set(scope.changed_paths(first, last)))
            finally:
                os.chdir(original)

    def test_selected_gate_never_counts_skipped_as_pass(self):
        scope.verify_results(scope.full("release"), "success", "success")
        scope.verify_results(scope.plan(), "success", "skipped")
        for result in ("skipped", "failure", "cancelled", "", "missing"):
            with self.assertRaises(ValueError):
                scope.verify_results(scope.full("release"), "success", result)

    def test_runner_selects_matching_junit_classes(self):
        runner = (ROOT / "scripts/backend-e2e/run.sh").read_text()
        for suite in ("batch", "schedule", "realtime", "schema", "full"):
            self.assertIn(suite + ")", runner)
        for name in ("OfflineIncrementalIT", "ScheduleAdmissionIT", "ProcessRecoveryIT"):
            self.assertIn(name, runner)
        workflow = (ROOT / ".github/workflows/backend-e2e.yml").read_text()
        self.assertIn("Backend E2E Check", workflow)
        self.assertIn("scripts/backend-e2e/run.sh", workflow)
        self.assertNotIn("scripts/acceptance/", workflow)
        self.assertNotIn("docker run", workflow)
        self.assertIn("backend-e2e.yml",
                (ROOT / ".github/workflows/backend-acceptance.yml").read_text())


if __name__ == "__main__":
    unittest.main()
