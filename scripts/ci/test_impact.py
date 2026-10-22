import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import impact


class ImpactTest(unittest.TestCase):
    def test_docs_and_ci_unit_tests_are_cheap(self):
        for filename in ("README.md", "docs/architecture.md", "scripts/ci/impact.py", "scripts/tests/test_backend_tests.py"):
            with self.subTest(filename=filename):
                check = impact.analyze([filename])
                self.assertFalse(any(check[key] for key in ("backend", "frontend", "distribution", "release")))
                self.assertEqual(set(), check["suites"])

    def test_backend_module_uses_reactor_path(self):
        check = impact.analyze(["yak-ops-common/src/main/java/io/yak/ops/common/enums/common/CommonErrorCode.java"])
        self.assertEqual({"yak-ops-common"}, check["modules"])
        self.assertEqual(set(), check["suites"])
        self.assertTrue(check["backend"])
        self.assertEqual("yak-ops-common", impact.outputs(["yak-ops-common/src/main/java/Example.java"]).split("modules=")[1].splitlines()[0])

    def test_business_task_only_relevant_suites(self):
        check = impact.analyze(["yak-ops-business/yak-ops-business-task/src/main/java/X.java"])
        self.assertEqual({"yak-ops-business/yak-ops-business-task"}, check["modules"])
        self.assertEqual({"business", "backend-e2e"}, check["suites"])

    def test_vendor_specific_and_shared_jdbc(self):
        vendor = impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/main/java/dialect/MySqlDialect.java"])
        self.assertIn("jdbc-mysql", vendor)
        self.assertNotIn("jdbc-oracle", vendor)
        common = impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/main/java/internal/JdbcOutputFormat.java"])
        self.assertEqual(["jdbc-mysql", "jdbc-postgres", "jdbc-oracle", "pipeline", "cdc-pipeline"], common)

    def test_test_only_changes_do_not_start_database(self):
        self.assertEqual([], impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/test/java/JdbcBatchExecutionTest.java"]))
        self.assertEqual(["jdbc-mysql"], impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/test/java/MySqlSourceIT.java"]))
        self.assertEqual(["recovery-e2e"], impact.select_suites(["yak-ops-e2e/src/test/java/ProcessRecoveryIT.java"]))

    def test_rename_union_and_release(self):
        check = impact.analyze(["yak-ops-ui/apps/web/app/data-sync/index.tsx", "yak-ops-dao/src/main/resources/db/migration/V1__init.sql"])
        self.assertTrue(check["frontend"])
        self.assertTrue(check["backend"])
        self.assertTrue(check["release"])
        self.assertIn("backend-e2e", check["suites"])

    def test_unknown_and_root_build_are_fail_closed(self):
        for path in (".editorconfig", "pom.xml", "unknown/Config.java"):
            with self.subTest(path=path):
                check = impact.analyze([path])
                self.assertTrue(all(check[key] for key in ("backend", "frontend", "distribution", "release")))
                self.assertEqual(set(impact.SUITES), check["suites"])

    def test_release_full_and_release_only(self):
        self.assertEqual(set(impact.SUITES), impact.analyze([], full=True)["suites"])
        self.assertFalse(impact.analyze(["scripts/release/check-release-metadata.sh"])["backend"])
        self.assertTrue(impact.analyze(["scripts/release/check-release-metadata.sh"])["release"])

    def test_nested_aggregator_module(self):
        modules = impact.module_for("yak-ops-business/pom.xml")
        self.assertIn("yak-ops-business/yak-ops-business-task", modules)
        self.assertNotIn("yak-ops-business", modules)


if __name__ == "__main__":
    unittest.main()
