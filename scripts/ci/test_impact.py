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
        self.assertIn("yak-ops-common", check["modules"])
        self.assertIn("yak-ops-boot", check["modules"])
        self.assertEqual(set(), check["suites"])
        self.assertTrue(check["backend"])
        self.assertIn("yak-ops-boot", impact.outputs(["yak-ops-common/src/main/java/Example.java"]).split("modules=")[1].splitlines()[0])

    def test_business_task_only_relevant_suites(self):
        check = impact.analyze(["yak-ops-business/yak-ops-business-task/src/main/java/X.java"])
        self.assertIn("yak-ops-business/yak-ops-business-task", check["modules"])
        self.assertIn("yak-ops-boot", check["modules"])
        self.assertEqual({"business", "backend-e2e"}, check["suites"])

    def test_downstream_checks_follow_runtime_dependencies(self):
        selected = impact.affected_modules(["yak-flow/yak-flow-runtime"])
        self.assertIn("yak-flow/yak-flow-runtime", selected)
        self.assertIn("yak-ops-boot", selected)
        self.assertNotIn("yak-ops-dist", selected)
        self.assertNotIn("yak-ops-ui", selected)

    def test_vendor_specific_and_shared_jdbc(self):
        vendor = impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/main/java/dialect/MySqlDialect.java"])
        self.assertIn("jdbc-mysql", vendor)
        self.assertNotIn("jdbc-oracle", vendor)
        common = impact.select_suites(["yak-flow/yak-flow-connector-jdbc/src/main/java/internal/JdbcOutputFormat.java"])
        self.assertEqual(["jdbc-mysql", "jdbc-postgres", "jdbc-oracle", "pipeline", "cdc-pipeline-mysql", "cdc-pipeline-postgres", "cdc-pipeline-oracle", "cdc-acceptance-postgres", "cdc-acceptance-oracle", "cdc-recovery"], common)

    def test_shared_cdc_base_runs_affected_cdc_suites(self):
        path = "yak-flow/yak-flow-connector-cdc-base/src/main/java/io/yak/ops/connector/cdc/base/source/assigner/SnapshotSplitAssigner.java"
        self.assertEqual(set(impact.CDC_SUITES) | {"recovery-e2e"}, set(impact.select_suites([path])))
        self.assertIn("yak-flow/yak-flow-connector-cdc-mysql", impact.analyze([path])["modules"])
        self.assertEqual([], impact.select_suites([
            "yak-flow/yak-flow-connector-cdc-base/src/test/java/SnapshotSplitAssignerTest.java"]))

    def test_cdc_it_owner_is_selected_without_unrelated_databases(self):
        root = "yak-flow/yak-flow-connector-cdc-mysql/src/test/java/"
        expected = {
            "source/MySqlBinlogIT.java": "cdc-binlog",
            "source/MySqlHybridIT.java": "cdc-hybrid",
            "source/reader/MySqlBackfillIT.java": "cdc-snapshot",
            "source/reader/MySqlKeylessBackfillIT.java": "cdc-snapshot",
            "integration/MySqlCdcToMySqlIT.java": "cdc-pipeline-mysql",
            "integration/MySqlCdcToPostgresIT.java": "cdc-pipeline-postgres",
            "integration/MySqlCdcToOracleIT.java": "cdc-pipeline-oracle",
            "integration/MySqlCdcToMySqlCrashRecoveryIT.java": "cdc-recovery",
        }
        for path, selected in expected.items():
            with self.subTest(path=path):
                actual = set(impact.select_suites([root + path]))
                if selected == "cdc-pipeline-postgres":
                    self.assertEqual({"cdc-pipeline-postgres", "cdc-acceptance-postgres"}, actual)
                elif selected == "cdc-pipeline-oracle":
                    self.assertEqual({"cdc-pipeline-oracle", "cdc-acceptance-oracle"}, actual)
                else:
                    self.assertEqual({selected}, actual)
        self.assertEqual(set(impact.CDC_PIPELINES + impact.CDC_ACCEPTANCE) | {"cdc-recovery"}, set(impact.select_suites([
            root + "integration/MySqlCdcPipelineTestBase.java",
        ])))

    def test_snapshot_and_shared_cdc_change_preserve_recovery_coverage(self):
        snapshot = impact.select_suites([
            impact.CDC + "src/main/java/io/yak/ops/connector/cdc/mysql/source/assigner/MySqlChunkSplitter.java"
        ])
        self.assertEqual(
            {"cdc-snapshot", "cdc-hybrid", "cdc-recovery", "recovery-e2e"} | set(impact.CDC_PIPELINES),
            set(snapshot),
        )
        self.assertNotIn("cdc-binlog", snapshot)
        self.assertNotIn("cdc-acceptance-postgres", snapshot)
        self.assertNotIn("cdc-acceptance-oracle", snapshot)
        unknown = impact.select_suites([impact.CDC + "src/main/java/source/UnexpectedChange.java"])
        self.assertEqual(set(impact.CDC_SUITES), set(unknown))
        checkpoint = impact.select_suites([impact.CDC + "src/main/java/source/CheckpointOffset.java"])
        self.assertEqual(set(impact.CDC_SUITES) | {"recovery-e2e"}, set(checkpoint))

    def test_vendor_specific_cdc_pipeline_and_mysql_xa(self):
        postgres = impact.select_suites([
            impact.JDBC + "src/main/java/io/yak/ops/connector/jdbc/database/dialect/PostgresDialect.java"
        ])
        self.assertIn("cdc-pipeline-postgres", postgres)
        self.assertIn("cdc-acceptance-postgres", postgres)
        self.assertNotIn("cdc-acceptance-oracle", postgres)
        self.assertNotIn("cdc-pipeline-oracle", postgres)
        self.assertNotIn("cdc-recovery", postgres)
        mysql = impact.select_suites([
            impact.JDBC + "src/main/java/io/yak/ops/connector/jdbc/database/dialect/MySqlDialect.java"
        ])
        self.assertIn("cdc-recovery", mysql)
        self.assertIn("cdc-pipeline-mysql", mysql)

    def test_unknown_cdc_and_shared_runtime_require_all_cross_db_acceptance(self):
        for path in (
            impact.CDC + "src/main/java/source/UnknownSource.java",
            impact.CDC_BASE + "src/main/java/source/assigner/SnapshotSplitAssigner.java",
            impact.RUNTIME + "src/main/java/execution/CheckpointCoordinator.java",
        ):
            with self.subTest(path=path):
                selected = set(impact.select_suites([path]))
                self.assertTrue(set(impact.CDC_ACCEPTANCE).issubset(selected))

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


    def test_registry_change_only_selects_affected_suite(self):
        before = 'SUITES = {"cdc-binlog": (1, 2), "backend-e2e": (1, 2)}\n\ndef runner():\n    return True\n'
        after = 'SUITES = {"cdc-binlog": (1, 2), "backend-e2e": (1, 3)}\n\ndef runner():\n    return True\n'
        self.assertEqual({"backend-e2e"}, impact.compare_suite_registry(before, after))
        self.assertEqual(set(), impact.compare_suite_registry(before, before + "# comment only\n"))

    def test_registry_logic_change_fails_closed(self):
        before = 'SUITES = {"cdc": 1}\ndef runner():\n    return 1\n'
        after = 'SUITES = {"cdc": 2}\ndef runner():\n    return 2\n'
        self.assertIsNone(impact.compare_suite_registry(before, after))
        self.assertIsNone(impact.compare_suite_registry(before, "SUITES = dict(cdc=1)"))
        self.assertIsNone(impact.compare_suite_registry(before, "SUITES = {'other': 1}"))

    def test_registry_selection_and_reason_are_consistent(self):
        from unittest.mock import patch
        with patch.object(impact, "suite_registration_changes", return_value={"backend-e2e"}):
            selected = impact.select_suites(["scripts/tests/backend_tests.py"])
            self.assertEqual(["backend-e2e"], selected)
            self.assertIn("suite registration only", impact.summary(["scripts/tests/backend_tests.py"]))
        with patch.object(impact, "suite_registration_changes", return_value=None):
            self.assertEqual(list(impact.SUITES), impact.select_suites(["scripts/tests/backend_tests.py"]))


if __name__ == "__main__":
    unittest.main()
