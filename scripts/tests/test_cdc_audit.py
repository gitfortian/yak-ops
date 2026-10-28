"""Fail-closed coverage and timing evidence tests for the CDC integration suite."""
import json
from pathlib import Path
import tempfile
import unittest

import cdc_audit as audit


class CdcAuditTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.module = self.root / audit.CDC
        self.sources = self.module / "src/test/java"
        self.sources.mkdir(parents=True)
        self.reports = self.module / "target/failsafe-reports"
        self.reports.mkdir(parents=True)
        self.inventory = {"cdc": {"ExampleIT": 2}}
        self.write_source("ExampleIT", ("first", "second"))
        self.write_report(("first", "second"))

    def write_source(self, classname, methods, parent=None, tags=None):
        extension = f" extends {parent}" if parent else ""
        tags = tags or {}
        body = "\n".join(
            '@Test\n    ' + (f'@Tag("{tags[method]}")\n    ' if method in tags else '')
            + f"void {method}() {{}}"
            for method in methods
        )
        (self.sources / f"{classname}.java").write_text(
            f"class {classname}{extension} {{\n{body}\n}}\n", encoding="utf-8"
        )

    def write_report(self, names, *, status=None):
        cases = []
        for method in names:
            child = f"<{status}/>" if status else ""
            cases.append(
                f'<testcase classname="sample.ExampleIT" name="{method}" time="1.25">{child}</testcase>'
            )
        (self.reports / "TEST-ExampleIT.xml").write_text(
            '<testsuite name="sample.ExampleIT">' + "".join(cases) + "</testsuite>",
            encoding="utf-8",
        )

    def collect(self):
        return audit.collect(self.root, "cdc", self.inventory)

    def test_complete_method_evidence(self):
        result = self.collect()
        self.assertEqual("PASS", result["status"])
        self.assertEqual((2, 2), (result["observedMethods"], result["expectedMethods"]))
        self.assertEqual(["first", "second"], sorted(case["method"] for case in result["cases"]))
        self.assertIn("ExampleIT.first", audit.markdown(result))

    def test_missing_method_fails_even_when_count_is_unchanged(self):
        self.write_report(("first", "unregistered"))
        result = self.collect()
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(any("missing=['second']" in error for error in result["errors"]))
        self.assertTrue(any("unexpected=['unregistered']" in error for error in result["errors"]))

    def test_missing_and_skipped_results_are_not_pass(self):
        for status in ("skipped", "error", "failure"):
            with self.subTest(status=status):
                self.write_report(("first", "second"), status=status)
                self.assertEqual("FAIL", self.collect()["status"])
        (self.reports / "TEST-ExampleIT.xml").unlink()
        self.assertEqual("FAIL", self.collect()["status"])

    def test_minimum_count_catches_accidental_test_deletion(self):
        self.write_source("ExampleIT", ("first",))
        result = self.collect()
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(any("expected at least 2 Java @Test methods" in error for error in result["errors"]))

    def test_renamed_method_needs_no_second_name_inventory(self):
        self.write_source("ExampleIT", ("renamed", "second"))
        self.write_report(("renamed", "second"))
        self.assertEqual("PASS", self.collect()["status"])

    def test_parameterized_invocations_count_as_one_declared_method(self):
        """Mirror Flink CDC's @ParameterizedTest source and multiple Surefire XML rows."""
        (self.sources / "ExampleIT.java").write_text(
            "class ExampleIT {\n"
            "  @ParameterizedTest\n"
            "  @ValueSource(ints = {1, 2})\n"
            "  void first(int value) {}\n"
            "  @Test\n"
            "  void second() {}\n"
            "}\n",
            encoding="utf-8",
        )
        self.write_report(("first(int)[1]", "first(int)[2]", "second"))
        result = self.collect()
        self.assertEqual("PASS", result["status"])
        self.assertEqual((2, 2), (result["observedMethods"], result["expectedMethods"]))
        self.assertEqual(3, result["observedInvocations"])

    def test_new_test_must_appear_in_execution_report(self):
        self.write_source("ExampleIT", ("first", "second", "added"))
        self.assertTrue(any("missing=['added']" in error for error in self.collect()["errors"]))
        self.write_report(("first", "second", "added"))
        self.assertEqual("PASS", self.collect()["status"])

    def test_generic_method_bound_is_not_a_test_superclass(self):
        source = self.sources / "ExampleIT.java"
        source.write_text(
            source.read_text(encoding="utf-8").replace(
                "}\\n",
                "  public <G extends Gauge<Long>> G metric(G gauge) { return gauge; }\\n}\\n",
            ),
            encoding="utf-8",
        )
        self.assertEqual("PASS", self.collect()["status"])

    def test_inherited_test_methods_are_checked(self):
        self.write_source("ExampleIT", (), "SharedITBase")
        self.write_source("SharedITBase", ("first", "second"))
        self.assertEqual("PASS", self.collect()["status"])
        self.write_source("SharedITBase", ("first",))
        self.assertEqual("FAIL", self.collect()["status"])

    def test_split_suites_audit_only_tagged_methods(self):
        self.inventory = {
            "cdc-pipeline-postgres": {"ExampleIT": 1},
            "cdc-acceptance-postgres": {"ExampleIT": 1},
        }
        tags = {"first": "cdc-schema", "second": "cdc-changelog"}
        self.write_source("ExampleIT", ("first", "second"), tags=tags)
        self.write_report(("first",))
        fast = audit.collect(self.root, "cdc-pipeline-postgres", self.inventory)
        self.assertEqual("PASS", fast["status"])
        self.assertEqual((1, 1), (fast["observedMethods"], fast["expectedMethods"]))
        self.write_report(("second",))
        full = audit.collect(self.root, "cdc-acceptance-postgres", self.inventory)
        self.assertEqual("PASS", full["status"])
        self.assertEqual((1, 1), (full["observedMethods"], full["expectedMethods"]))
        self.write_source("ExampleIT", ("first",), tags=tags)
        self.assertEqual("FAIL", audit.collect(self.root, "cdc-acceptance-postgres", self.inventory)["status"])

    def test_full_inventory_combines_split_suite_methods(self):
        self.inventory = {
            "cdc-pipeline-postgres": {"ExampleIT": 1},
            "cdc-acceptance-postgres": {"ExampleIT": 1},
        }
        self.write_source("ExampleIT", ("first", "second"), tags={
            "first": "cdc-schema", "second": "cdc-changelog"
        })
        self.write_report(("first", "second"))
        report = audit.collect(self.root, "full", self.inventory)
        self.assertEqual("PASS", report["status"])
        self.assertEqual((2, 2), (report["observedMethods"], report["expectedMethods"]))

    def test_untagged_pipeline_method_is_not_silently_excluded(self):
        self.inventory = {
            "cdc-pipeline-postgres": {"ExampleIT": 1},
            "cdc-acceptance-postgres": {"ExampleIT": 1},
        }
        self.write_source("ExampleIT", ("first", "second", "untagged"), tags={
            "first": "cdc-schema", "second": "cdc-changelog"
        })
        self.write_report(("first",))
        result = audit.collect(self.root, "cdc-pipeline-postgres", self.inventory)
        self.assertEqual("FAIL", result["status"])
        self.assertTrue(any("without acceptance tags" in e for e in result["errors"]))

    def test_duplicate_result_cannot_replace_missing_test(self):
        self.write_report(("first", "first"))
        result = self.collect()
        self.assertTrue(any("duplicate result" in error for error in result["errors"]))
        self.assertEqual("FAIL", result["status"])

    def test_container_startup_and_poll_locations_are_visible(self):
        self.write_source("ExampleIT", ("first", "second"))
        source = self.sources / "ExampleIT.java"
        source.write_text(source.read_text() + "Thread.sleep(100);\n", encoding="utf-8")
        startup = self.module / "target/test-artifacts/containers/mysql/startup.json"
        startup.parent.mkdir(parents=True)
        startup.write_text(json.dumps({
            "containerStartupMillis": 2000, "sqlReadyMillis": 100, "elapsedMillis": 2100
        }), encoding="utf-8")
        result = self.collect()
        self.assertEqual("PASS", result["status"])
        self.assertEqual(2000, result["databaseStartup"][0]["containerStartupMillis"])
        self.assertTrue(any("ExampleIT.java" in site for site in result["pollLocations"]))
        self.assertIn("Container ms", audit.markdown(result))

    def test_invalid_xml_and_missing_time_fail(self):
        self.write_report(("first", "second"))
        file = self.reports / "TEST-ExampleIT.xml"
        file.write_text("<testsuite><testcase>", encoding="utf-8")
        self.assertEqual("FAIL", self.collect()["status"])
        self.write_report(("first", "second"))
        file.write_text(file.read_text().replace('time="1.25"', ""), encoding="utf-8")
        self.assertEqual("FAIL", self.collect()["status"])


if __name__ == "__main__":
    unittest.main()
