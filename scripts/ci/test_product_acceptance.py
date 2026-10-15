"""Fast routing, merge-gate and runner checks; no browser, database or credentials required."""

from contextlib import nullcontext, redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

import product_acceptance_scope as scope

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "run_product_acceptance", ROOT / "scripts/acceptance/run_product_acceptance.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class ScopeTest(unittest.TestCase):
    def test_docs_and_frontend_do_not_run_backend_e2e(self):
        for path in ("AGENTS.md", "DEVELOPMENT.md", "docs/guide.mdx",
                     "yak-ops-ui/apps/web/app/workspace/page.tsx",
                     "yak-ops-ui/apps/web/app/data-sync/task-editor.tsx",
                     "yak-ops-ui/apps/web/service/data-sync/config.ts",
                     "yak-ops-ui/package-lock.json",
                     "yak-ops-ui/tests/e2e/run.py",
                     ".github/workflows/ui-acceptance.yml"):
            with self.subTest(path=path):
                self.assertEqual([], scope.classify([path])["suites"])

    def test_backend_scripts_select_matching_suites(self):
        for filename, suite in (("multitable_batch.py", "batch"),
                                ("offline_incremental_e2e.py", "batch"),
                                ("database_realtime.py", "realtime"),
                                ("schema_migration_e2e.py", "schema")):
            self.assertEqual([suite], scope.classify(["scripts/acceptance/" + filename])["suites"])

    def test_scheduler_schema_and_shared_backend(self):
        self.assertEqual(["batch", "schedule"], scope.classify([
            "yak-ops-plugins/yak-ops-plugin-scheduler/quartz/Factory.java"])["suites"])
        self.assertEqual(["schema"], scope.classify([
            "yak-flow/yak-flow-connector-jdbc/src/main/JdbcMetadataApplier.java"])["suites"])
        for path in ("yak-flow/yak-flow-runtime/Checkpoint.java", "yak-ops-dao/schema.sql",
                     "new-module/new.file", "pom.xml", "scripts/ci/product_acceptance_scope.py"):
            self.assertEqual(["full"], scope.classify([path])["suites"])

    def test_mixed_frontend_and_backend_keeps_backend_checks(self):
        self.assertEqual(["full"], scope.classify([
            "yak-ops-ui/apps/web/app/data-sync/index.tsx",
            "yak-ops-business/TaskService.java"])["suites"])
        self.assertEqual(["realtime", "schema"], scope.classify([
            "scripts/acceptance/schema_migration_e2e.py",
            "scripts/acceptance/database_realtime.py"])["suites"])

    def test_explicit_and_reusable_full(self):
        with patch.object(scope, "changed_paths", side_effect=AssertionError("must not diff")):
            for event in ("pull_request", "workflow_dispatch", "workflow_call"):
                self.assertEqual(["full"], scope.select("full", event, "", "")["suites"])
        for suite in scope.SUITES:
            self.assertEqual([suite], scope.select(suite, "workflow_dispatch", "", "")["suites"])
        with self.assertRaises(ValueError):
            scope.select("browser", "workflow_dispatch", "", "")

    def test_comparison_error_is_conservative(self):
        for error in (ValueError(), OSError(), subprocess.TimeoutExpired("git", 30)):
            with patch.object(scope, "changed_paths", side_effect=error):
                self.assertEqual(["full"], scope.select("auto", "pull_request", "", "")["suites"])
        self.assertEqual(["full"], scope.select("auto", "push", "", "")["suites"])


class GitDiffTest(unittest.TestCase):
    def test_full_pr_diff_and_both_sides_of_rename(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.DEVNULL).decode().strip()
            git("init", "-q")
            git("config", "user.email", "ci@example.invalid")
            git("config", "user.name", "CI")
            (root / "README.md").write_text("initial\n")
            git("add", ".")
            git("commit", "-qm", "base")
            base = git("rev-parse", "HEAD")
            (root / "Execution.java").write_text("runtime\n")
            git("add", ".")
            git("commit", "-qm", "runtime change")
            (root / "yak-ops-ui").mkdir()
            (root / "yak-ops-ui/small change.css").write_text("body {}\n")
            git("add", ".")
            git("commit", "-qm", "small follow-up")
            head = git("rev-parse", "HEAD")
            original = Path.cwd()
            try:
                os.chdir(root)
                self.assertEqual(["full"], scope.classify(scope.changed_paths(base, head))["suites"])
                git("mv", "Execution.java", "Execution.md")
                git("commit", "-qm", "rename")
                paths = scope.changed_paths(head, git("rev-parse", "HEAD"))
                self.assertEqual({"Execution.java", "Execution.md"}, set(paths))
                self.assertEqual(["full"], scope.classify(paths)["suites"])
            finally:
                os.chdir(original)

    def test_invalid_ref_is_rejected_without_shell(self):
        with patch.object(scope.subprocess, "run") as run:
            with self.assertRaises(ValueError):
                scope.changed_paths("--output=/tmp/file", "HEAD")
            run.assert_not_called()


class GateTest(unittest.TestCase):
    def test_selected_backend_suite_enforced(self):
        scope.verify_results(scope.full("test"), "success", "success")
        scope.verify_results(scope.plan(), "success", "skipped")
        for bad in ("failure", "cancelled", "skipped", "", "missing"):
            with self.subTest(status=bad):
                with self.assertRaises(ValueError):
                    scope.verify_results(scope.full("test"), "success", bad)
                with self.assertRaises(ValueError):
                    scope.verify_results(scope.full("test"), bad, "success")

    def test_empty_scope_is_not_a_pass(self):
        output = io.StringIO()
        with patch.dict(os.environ, {"BACKEND_TEST_PLAN": json.dumps(scope.plan()),
                  "SCOPE_RESULT": "success", "BACKEND_RESULT": "skipped",
                  "GITHUB_STEP_SUMMARY": ""}), patch("sys.argv", ["scope", "--check-results"]), redirect_stdout(output):
            scope.main()
        self.assertIn("NOT RUN", output.getvalue())

    def test_bad_plan_rejected(self):
        for selected in ({}, scope.plan(["full", "schema"])):
            if selected:
                selected["suites"] = ["full", "schema"]
            with self.assertRaises(ValueError):
                scope.verify_results(selected, "success", "success")


class RunnerTest(unittest.TestCase):
    def test_full_delegates_without_changing_the_original_sequence(self):
        harness = SimpleNamespace(acceptance=Mock())
        timings = []
        with redirect_stdout(io.StringIO()):
            runner.execute("full", harness, timings)
        harness.acceptance.assert_called_once_with()
        self.assertEqual("PASSED", timings[0]["result"])

    def test_failure_is_timed_and_propagated(self):
        harness = SimpleNamespace(acceptance=Mock(side_effect=RuntimeError("injected")))
        timings = []
        with redirect_stdout(io.StringIO()), self.assertRaises(RuntimeError):
            runner.execute("full", harness, timings)
        self.assertEqual("FAILED", timings[0]["result"])

    def test_selected_suite_restarts_and_checks_exact_report(self):
        client = SimpleNamespace(workspace="workspace")
        refreshed = Mock()
        refreshed.call.return_value = {"executionReport": "pinned"}
        harness = SimpleNamespace(APP=object(), shutdown=Mock(), boot=Mock(), Client=Mock(return_value=refreshed))
        with patch.object(runner, "bootstrap", return_value=(client, "source", "target")), \
                patch.object(runner, "run_suite", return_value=[("instance", "pinned")]) as run, \
                redirect_stdout(io.StringIO()):
            runner.execute("schema", harness, timings=[])
        run.assert_called_once_with("schema", harness, client, "source", "target")
        self.assertEqual(2, harness.shutdown.call_count)
        harness.boot.assert_called_once()
        self.assertEqual("workspace", refreshed.workspace)
        refreshed.call.assert_called_once_with("GET", "/api/v1/task-instances/instance")

    def test_focused_failure_keeps_failure_and_closes_process(self):
        harness = SimpleNamespace(APP=object(), shutdown=Mock())
        client = Mock()
        with patch.object(runner, "bootstrap", return_value=(client, "s", "t")), \
                patch.object(runner, "run_suite", side_effect=RuntimeError("original failure")), \
                patch.object(runner, "report_failures") as report, redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "original failure"):
                runner.execute("schema", harness, [])
        report.assert_called_once_with(client)
        harness.shutdown.assert_called_once_with(harness.APP)

    def test_conflict_fixture_is_canceled_and_released(self):
        client = Mock()
        harness = SimpleNamespace(create_task=Mock(return_value="task"), submit=Mock(return_value=({"instanceId": "i"}, "k")),
                                  wait_status=Mock(), claim_count=Mock(side_effect=[2, 0]))
        with self.assertRaises(RuntimeError):
            with runner.occupied_target(harness, client, "source", "target"):
                raise RuntimeError("assertion failed")
        client.call.assert_called_once_with("POST", "/api/v1/task-instances/i/cancel")
        self.assertEqual(2, harness.wait_status.call_count)

    def test_batch_keeps_atomic_conflict_assertions(self):
        module = SimpleNamespace(verify_multitable_batch=Mock(return_value=("i", "report")), verify_multitable_conflict=Mock())
        harness, client = SimpleNamespace(), Mock()
        with patch.object(runner.importlib, "import_module", return_value=module), \
                patch.object(runner, "occupied_target", return_value=nullcontext()):
            self.assertEqual([("i", "report")], runner.run_suite("batch", harness, client, "s", "t"))
        module.verify_multitable_conflict.assert_called_once_with(vars(harness), client, "s", "t")

    def test_realtime_and_schema_do_not_invoke_previous_suites(self):
        for suite, module_name, function in (("realtime", "database_realtime", "verify_database_realtime"),
                                              ("schema", "schema_migration_e2e", "verify_schema_migration")):
            module = SimpleNamespace(**{function: Mock(return_value=[("i", "report")])})
            with patch.object(runner.importlib, "import_module", return_value=module) as imported:
                self.assertEqual([("i", "report")], runner.run_suite(suite, SimpleNamespace(), Mock(), "s", "t"))
            imported.assert_called_once_with(module_name)

    def test_schedule_keeps_version_and_busy_checks(self):
        harness = SimpleNamespace(**{name: Mock() for name in ("create_task", "verify_schedule_configuration",
            "verify_parameter_versions", "verify_periodic_jdbc", "verify_busy_schedule")})
        with patch.dict(os.environ, {"E2E_BROWSER": "false"}), \
                patch.object(runner, "occupied_target", return_value=nullcontext()):
            self.assertEqual([], runner.run_suite("schedule", harness, Mock(), "s", "t"))
        for name in ("verify_schedule_configuration", "verify_parameter_versions", "verify_periodic_jdbc", "verify_busy_schedule"):
            getattr(harness, name).assert_called_once()

    def test_failure_evidence_excludes_parameters_and_messages(self):
        client = Mock()
        client.call.return_value = {"bizData": [{"id": "i", "status": "FAILED", "failureCode": "SCHEMA_FAILED",
            "parameters": "secret-parameters", "failureMessage": "secret-message"}]}
        output = io.StringIO()
        with redirect_stdout(output):
            runner.report_failures(client)
        self.assertIn("SCHEMA_FAILED", output.getvalue())
        self.assertNotIn("secret", output.getvalue())

    def test_help_needs_no_credentials_or_browser(self):
        result = subprocess.run(["python3", str(ROOT / "scripts/acceptance/run_product_acceptance.py"), "--help"],
                                capture_output=True, text=True, check=True)
        self.assertIn("--suite", result.stdout)


class WorkflowTest(unittest.TestCase):
    def test_release_backend_and_ui_gates_are_separate(self):
        backend = (ROOT / ".github/workflows/task-execution-product-acceptance.yml").read_text()
        release = (ROOT / ".github/workflows/v1-release-gate.yml").read_text()
        ui = (ROOT / ".github/workflows/ui-acceptance.yml").read_text()
        self.assertEqual(2, backend.count("default: full"))
        self.assertIn("fetch-depth: 0", backend)
        self.assertIn("max-parallel: 2", backend)
        self.assertIn("name: Select Backend Tests", backend)
        self.assertIn("Backend E2E Check", backend)
        self.assertNotIn("acceptance-browser", backend)
        self.assertNotIn("browser-contracts", backend)
        self.assertNotIn("E2E_BROWSER", backend)
        self.assertIn("YAK_PLAYWRIGHT: 'true'", release)
        self.assertIn("name: Playwright UI Tests", ui)
        self.assertIn("workflow_dispatch:", ui)
        self.assertIn("yak-ops-ui/tests/e2e/run.py", ui)
        scenarios = (ROOT / "yak-ops-ui/tests/e2e/run.py").read_text()
        for scenario in ("multitable-mapping", "multitable-selection", "schema-preview", "realtime-stages"):
            self.assertIn(scenario, scenarios)

    def test_browser_runner_has_no_backend_acceptance_dependency(self):
        content = (ROOT / "yak-ops-ui/tests/e2e/run.py").read_text()
        for forbidden in ("task-execution-e2e.py", "schema_migration_e2e.py", "docker run"):
            self.assertNotIn(forbidden, content)


if __name__ == "__main__":
    unittest.main()
