#!/usr/bin/env python3
"""Smoke tests for logging security without enforcing a log language or TRACE ban."""

import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from verify_logging_rules import ROOT, verify_file


class LoggingChecksTest(unittest.TestCase):
    def check(self, statement):
        with tempfile.TemporaryDirectory(dir=ROOT) as tempdir:
            source = Path(tempdir) / "Sample.java"
            source.write_text(
                "class Sample {\n"
                "    private static final Logger LOG = LoggerFactory.getLogger(Sample.class);\n"
                f"    void run() {{ {statement} }}\n"
                "}\n",
                encoding="utf-8",
            )
            return verify_file(source)

    def test_trace_and_english_message_are_allowed(self):
        self.assertEqual([], self.check('LOG.trace("Trace event");'))

    def test_sensitive_log_arguments_are_rejected(self):
        self.assertTrue(self.check('LOG.info("Value {}", password);'))

    def test_direct_console_output_is_rejected(self):
        self.assertTrue(self.check('System.out.println("unsafe");'))


if __name__ == "__main__":
    unittest.main()
