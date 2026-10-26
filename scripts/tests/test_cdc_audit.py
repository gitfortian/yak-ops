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
        self.inventory = {"cdc": {"ExampleIT": ("first", "second")}}
        self.write_source("ExampleIT", ("first", "second"))
        self.write_report(("first", "second"))

    def write_source(self, classname, methods, parent=None):
        extension = f" extends {parent}" if parent else ""
        body = "\n".join(f"@Test\n    void {method}() {{}}" for method in methods)
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

    def test_java_inventory_catches_accidental_test_deletion(self):
        self.write_source("ExampleIT", ("first",))
        self.assertTrue(any("Java @Test inventory mismatch" in error for error in self.collect()["errors"]))

    def test_inherited_test_methods_are_checked(self):
        self.write_source("ExampleIT", (), "SharedITBase")
        self.write_source("SharedITBase", ("first", "second"))
        self.assertEqual("PASS", self.collect()["status"])
        self.write_source("SharedITBase", ("first",))
        self.assertEqual("FAIL", self.collect()["status"])

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
