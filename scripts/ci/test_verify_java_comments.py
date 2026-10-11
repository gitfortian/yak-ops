#!/usr/bin/env python3
"""Regression tests for the comment scanner's Java-string and type-doc boundaries."""

import unittest

from verify_java_comments import EXTRA_SOURCE_ROOTS, MODULES, check_source, scan_comments


class YakFlowCommentChecksTest(unittest.TestCase):
    def test_all_migrated_modules_are_scanned(self):
        self.assertEqual(
            ("yak-ops-core", "yak-flow-runtime", "yak-flow-connector-base", "yak-flow-connector-jdbc",
             "yak-flow-connector-cdc-mysql", "yak-ops-business-datasource", "yak-ops-business-task",
             "yak-ops-plugin-datasource-api", "yak-ops-plugin-datasource-jdbc",
             "yak-ops-plugin-task-api", "yak-ops-plugin-task-data-sync"),
            tuple(path.name for path in MODULES),
        )

    def test_shared_package_scopes_include_datasource_and_task(self):
        paths = tuple(str(root) for root in EXTRA_SOURCE_ROOTS)
        self.assertTrue(any(path.endswith("common/bean/dto/datasource") for path in paths))
        self.assertTrue(any(path.endswith("dao/entity/task") for path in paths))
        self.assertTrue(any(path.endswith("boot/controller/task") for path in paths))

    def test_ignores_chinese_literals_and_inline_urls(self):
        text = '''/** A documented type. */
public class Probe {
    String error = "错误";
    String url = "https://example.com/path";
    String text = """
        // 中文 text-block data
        /* comment-looking text */
        """;
    char quote = '/';
}'''
        self.assertEqual([], check_source(text, "Probe.java", True))

    def test_rejects_chinese_javadoc_and_line_comment(self):
        text = '''/** 中文类型 */
public class Probe {
    // 中文注释
    void run() {}
}'''
        self.assertEqual(2, len(check_source(text, "Probe.java", True)))

    def test_requires_type_javadoc(self):
        text = '''package test;
public final class Probe {
}'''
        self.assertIn("missing Javadoc", check_source(text, "Probe.java", True)[0][1])

    def test_accepts_type_annotations_between_javadoc_and_declaration(self):
        text = '''/** A functional interface. */
@FunctionalInterface
public interface Probe {
    void run();
}'''
        self.assertEqual([], check_source(text, "Probe.java", True))

    def test_tests_do_not_require_type_javadoc(self):
        self.assertEqual([], check_source("class Probe {}", "Probe.java", False))

    def test_ignores_chinese_inside_escaped_strings_and_chars(self):
        text = r'''/** An example. */
public class Probe {
    String escaped = "\\\"中文//text\\\"";
    char slash = '/';
}'''
        self.assertEqual([], check_source(text, "Probe.java", True))

    def test_detects_unterminated_block_comment(self):
        with self.assertRaisesRegex(ValueError, "Unterminated"):
            scan_comments("class Probe { /* never closed")


if __name__ == "__main__":
    unittest.main()
