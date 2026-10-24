#!/usr/bin/env python3
"""Regression cases for backend naming rules and legitimate network/domain vocabulary."""
import tempfile
import unittest
from pathlib import Path

from check_backend_naming import verify_repository, verify_source

CORE = "yak-ops-core/src/main/java/io/yak/ops/core/Foo.java"
CDC = "yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/Foo.java"
NETWORK = "yak-flow/yak-flow-runtime/src/main/java/io/yak/ops/flow/runtime/io/partition/InputGate.java"


def source(package: str, body: str) -> str:
    return f"package {package};\n\n{body}\n"


class BackendNamingTest(unittest.TestCase):
    def check(self, path: str, package: str, body: str):
        return verify_source(path, source(package, body))

    def test_project_codec_types_are_rejected(self):
        for declaration in (
            "class ParameterCodec {}",
            "interface JsonCodec {}",
            "record RowCodec(int id) {}",
            "enum StateCodec { A }",
            "@interface FieldCodec {}",
        ):
            with self.subTest(declaration=declaration):
                self.assertTrue(self.check(CORE, "io.yak.ops.core", declaration))

    def test_codec_method_declaration_is_rejected(self):
        violations = self.check(
            CORE, "io.yak.ops.core",
            "class Foo {\n    private static String createCodec() { return null; }\n}",
        )
        self.assertEqual(1, len(violations))
        self.assertIn("Method 'createCodec'", violations[0][1])

    def test_codec_package_is_rejected(self):
        errors = self.check(
            "yak-ops-core/src/main/java/io/yak/ops/core/codec/Parser.java",
            "io.yak.ops.core.codec",
            "class Parser {}",
        )
        self.assertEqual(1, len(errors))
        self.assertIn("Package segment 'codec'", errors[0][1])

    def test_ambiguous_business_and_cdc_gate_classes_are_rejected(self):
        for path, package, name in (
            (CORE, "io.yak.ops.core", "ScheduleAdmissionGate"),
            (CDC, "io.yak.ops.connector.cdc.mysql", "MySqlSchemaChangeGate"),
        ):
            with self.subTest(name=name):
                self.assertTrue(self.check(path, package, f"class {name} {{}}"))

    def test_real_network_input_gate_is_allowed(self):
        self.assertEqual([], self.check(
            NETWORK, "io.yak.ops.flow.runtime.io.partition",
            "public final class InputGate {}",
        ))

    def test_network_gate_name_in_wrong_owner_is_rejected(self):
        self.assertTrue(self.check(
            CDC, "io.yak.ops.connector.cdc.mysql",
            "public final class InputGate {}",
        ))

    def test_legitimate_normalization_and_domain_types_are_allowed(self):
        self.assertEqual([], self.check(
            CORE, "io.yak.ops.core",
            "class NameParser { String normalizeIdentifier(String name) { return name; } }\n"
            "class SchemaChangeHandler {}\n"
            "class ScheduleAdmissionController {}\n"
            "class ConnectionGateway {}",
        ))

    def test_comments_strings_and_method_calls_are_not_declarations(self):
        self.assertEqual([], self.check(
            CORE, "io.yak.ops.core",
            'class Foo {\n'
            '  // class BadCodec {} or class FakeGate {}\n'
            '  String message = "class BadCodec {}";\n'
            '  void update() { external.createCodec(); }\n'
            '}',
        ))

    def test_excludes_tests_and_third_party_packages(self):
        self.assertEqual([], verify_source(
            "yak-ops-core/src/test/java/io/yak/ops/core/BadCodec.java",
            source("io.yak.ops.core", "class BadCodec {}"),
        ))
        self.assertEqual([], self.check(
            CORE, "org.external.thirdparty",
            "class ExternalCodec {}",
        ))

    def test_repository_scan_reports_path_and_line(self):
        with tempfile.TemporaryDirectory() as root:
            folder = Path(root) / "yak-ops-core/src/main/java/io/yak/ops/core"
            folder.mkdir(parents=True)
            (folder / "BadCodec.java").write_text(
                source("io.yak.ops.core", "class BadCodec {}"), encoding="utf-8"
            )
            count, errors = verify_repository(Path(root))
            self.assertEqual(1, count)
            self.assertEqual(1, len(errors))
            self.assertIn("BadCodec.java:3:", errors[0])


if __name__ == "__main__":
    unittest.main()
