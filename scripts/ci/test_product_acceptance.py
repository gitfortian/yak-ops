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
    def test_documentation_does_not_start_browser_or_database(self):
        for path in ("AGENTS.md", "DEVELOPMENT.md", ".github/PULL_REQUEST_TEMPLATE.md", "docs/guide.mdx"):
            with self.subTest(path=path):
                selected = scope.classify([path])
                self.assertFalse(selected["browser"])
                self.assertEqual([], selected["suites"])

    def test_presentation_has_no_database_dependency(self):
        for path in ("yak-ops-ui/apps/web/app/workspace/page.tsx", "yak-ops-ui/apps/web/theme.css",
                     "yak-ops-ui/packages/yak-ui/src/table/index.tsx", "yak-ops-ui/apps/web/yak.svg"):
            with self.subTest(path=path):
                selected = scope.classify([path])
                self.assertTrue(selected["browser"])
                self.assertEqual([], selected["suites"])
                self.assertFalse(selected["real_browser"])

    def test_outbound_sync_contracts_have_real_acceptance(self):
        for path in (scope.APP + "app/data-sync/task-editor.tsx", scope.APP + "service/data-sync/config.ts"):
            selected = scope.classify([path])
            self.assertEqual(["batch", "realtime", "schema"], selected["suites"])
            self.assertTrue(selected["real_browser"])

    def test_preview_only_selects_schema(self):
        selected = scope.classify([scope.APP + "app/data-sync/schema-preview.tsx"])
        self.assertEqual(["schema"], selected["suites"])
        self.assertTrue(selected["real_browser"])

    def test_suite_scripts_are_independent(self):
        for path, suite in (("multitable_batch.py", "batch"), ("task_schedule_browser.py", "schedule"),
                            ("database_realtime.py", "realtime"), ("schema_migration_e2e.py", "schema")):
            with self.subTest(suite=suite):
                selected = scope.classify(["scripts/acceptance/" + path])
                self.assertEqual([suite], selected["suites"])
                self.assertTrue(selected["real_browser"])

    def test_controlled_browser_script_has_no_database_dependency(self):
        self.assertEqual([], scope.classify(["scripts/acceptance/task_schedule_ui_test.py"])["suites"])

    def test_scheduler_preserves_batch_quartz_coverage(self):
        selected = scope.classify(["yak-ops-plugins/yak-ops-plugin-scheduler/quartz/Factory.java"])
        self.assertEqual(["batch", "schedule"], selected["suites"])
        self.assertFalse(selected["real_browser"])

    def test_metadata_application_selects_schema(self):
        selected = scope.classify(["yak-flow/yak-flow-connector-jdbc/src/main/JdbcMetadataApplier.java"])
        self.assertEqual(["schema"], selected["suites"])

    def test_shared_and_unknown_changes_are_full(self):
        for path in ("yak-flow/yak-flow-runtime/Checkpoint.java", "yak-ops-dao/schema.sql",
                     "yak-ops-task-execution/Observer.java", "new-module/new.file", "pom.xml",
                     scope.APP + "service/http.ts", "yak-ops-ui/package-lock.json"):
            with self.subTest(path=path):
                self.assertEqual(["full"], scope.classify([path])["suites"])

    def test_infrastructure_changes_are_full(self):
        for path in ("scripts/ci/product_acceptance_scope.py", "scripts/acceptance/run_product_acceptance.py",
                     "scripts/acceptance/task-execution-e2e.py", ".github/actions/acceptance-browser/action.yml",
                     ".github/workflows/task-execution-product-acceptance.yml"):
            self.assertEqual(["full"], scope.classify([path])["suites"])

    def test_large_and_mixed_diffs_are_not_truncated(self):
        paths = [f"docs/{index}.md" for index in range(501)] + ["yak-ops-core/Row.java"]
        self.assertEqual(["full"], scope.classify(paths)["suites"])
        selected = scope.classify(["scripts/acceptance/schema_migration_e2e.py",
                                   "scripts/acceptance/database_realtime.py"])
        self.assertEqual(["realtime", "schema"], selected["suites"])

    def test_reusable_and_manual_full_do_not_use_pr_diff(self):
        with patch.object(scope, "changed_paths", side_effect=AssertionError("must not diff")):
            for event in ("pull_request", "workflow_dispatch", "workflow_call"):
                self.assertEqual(["full"], scope.select("full", event, "", "")["suites"])

    def test_comparison_failure_is_full_not_skipped(self):
        for error in (ValueError(), OSError(), subprocess.TimeoutExpired("git", 30)):
            with patch.object(scope, "changed_paths", side_effect=error):
                self.assertEqual(["full"], scope.select("auto", "pull_request", "", "")["suites"])
        self.assertEqual(["full"], scope.select("auto", "push", "", "")["suites"])

    def test_invalid_explicit_mode_fails(self):
        with self.assertRaises(ValueError):
            scope.select("schmea", "workflow_dispatch", "", "")

    def test_explicit_scope(self):
        for suite in scope.SUITES:
            self.assertEqual([suite], scope.select(suite, "workflow_dispatch", "", "")["suites"])
        self.assertEqual([], scope.select("browser", "workflow_dispatch", "", "")["suites"])


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
    def test_selected_success(self):
        scope.verify_results(scope.full("test"), "success", "success", "success")
        scope.verify_results(scope.plan(True), "success", "success", "skipped")
        scope.verify_results(scope.plan(), "success", "skipped", "skipped")

    def test_failure_cancellation_and_unexpected_skip_block(self):
        for bad in ("failure", "cancelled", "skipped", "", "missing"):
            with self.subTest(result=bad):
                with self.assertRaises(ValueError):
                    scope.verify_results(scope.full("test"), "success", "success", bad)
                with self.assertRaises(ValueError):
                    scope.verify_results(scope.full("test"), "success", bad, "success")
                with self.assertRaises(ValueError):
                    scope.verify_results(scope.full("test"), bad, "success", "success")

    def test_empty_scope_is_reported_as_not_executed(self):
        output = io.StringIO()
        with patch.dict(os.environ, {"ACCEPTANCE_PLAN": json.dumps(scope.plan()),
                "SCOPE_RESULT": "success", "BROWSER_RESULT": "skipped", "PRODUCT_RESULT": "skipped",
                "GITHUB_STEP_SUMMARY": ""}), patch("sys.argv", ["scope", "--check-results"]), redirect_stdout(output):
            scope.main()
        self.assertIn("NOT EXECUTED", output.getvalue())
        self.assertNotIn("acceptance jobs succeeded", output.getvalue())

    def test_malformed_selection_is_not_a_pass(self):
        for selected in ({}, scope.plan(True, ["full", "schema"])):
            if selected:
                selected["suites"] = ["full", "schema"]
            with self.assertRaises(ValueError):
                scope.verify_results(selected, "success", "success", "success")


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
    def test_release_full_default_and_gate_are_preserved(self):
        workflow = (ROOT / ".github/workflows/task-execution-product-acceptance.yml").read_text()
        self.assertEqual(2, workflow.count("default: full"))
        self.assertIn("fetch-depth: 0", workflow)
        self.assertNotIn("    paths:", workflow)
        self.assertIn("max-parallel: 2", workflow)
        self.assertIn("fail-fast: false", workflow)
        self.assertNotIn("continue-on-error", workflow)
        self.assertIn("MySQL JDBC + Multi-Table CDC Product E2E", workflow)
        self.assertIn("--check-results", workflow)

    def test_browser_job_has_no_backend_or_database_setup(self):
        workflow = (ROOT / ".github/workflows/task-execution-product-acceptance.yml").read_text()
        browser = workflow.split("  browser-contracts:", 1)[1].split("  product-e2e:", 1)[0]
        for forbidden in ("setup-java", "docker", "mvnw", "task-execution-e2e.py"):
            self.assertNotIn(forbidden, browser)
        self.assertIn("task_schedule_ui_test.py", browser)


if __name__ == "__main__":
    unittest.main()
