#!/usr/bin/env python3
"""Select backend E2E suites from the full PR diff; uncertain changes run all suites."""

import argparse
from fnmatch import fnmatchcase
import json
import os
from pathlib import Path
import re
import subprocess

SUITES = ("batch", "schedule", "realtime", "schema")
MODES = ("auto", "full", *SUITES)
JAVA = "yak-ops-boot/src/test/java/io/yak/ops/boot/e2e/"

def plan(suites=(), reasons=()):
    selected = ["full"] if "full" in suites else [item for item in SUITES if item in suites]
    return {"suites": selected, "reasons": sorted(set(reasons))}

def full(reason):
    return plan(["full"], [reason])

def classify(paths):
    selected, reasons = set(), set()
    classes = {
        "TaskExecutionIT.java": "batch",
        "MultiTableBatchIT.java": "batch",
        "OfflineIncrementalIT.java": "batch",
        "ScheduleIT.java": "schedule",
        "DatabaseRealtimeIT.java": "realtime",
        "ProcessRecoveryIT.java": "realtime",
        "SchemaMigrationIT.java": "schema",
    }
    for path in paths:
        if path == ".github/workflows/ui-acceptance.yml" or path.startswith("yak-ops-ui/"):
            reasons.add("frontend or Playwright has independent test scope")
            continue
        if path.endswith((".md", ".mdx", ".rst")) or path in ("LICENSE", "NOTICE"):
            reasons.add("documentation only")
            continue
        if path.startswith(JAVA):
            suite = classes.get(Path(path).name)
            if suite:
                selected.add(suite)
                reasons.add(suite + " JUnit integration test changed")
                continue
            return full("shared JUnit fixture changed")
        if path in ("scripts/backend-e2e/run.sh", "scripts/ci/backend_e2e_scope.py",
                    "scripts/ci/test_backend_e2e_scope.py", ".github/workflows/backend-e2e.yml",
                    ".github/workflows/backend-acceptance.yml",
                    ".github/workflows/v1-release-gate.yml"):
            return full("backend E2E infrastructure changed")
        if path.startswith((".github/workflows/", ".github/actions/", "scripts/ci/")):
            return full("shared CI contract changed")
        if path.startswith(("yak-ops-business/yak-ops-business-schedule/",
                            "yak-ops-plugins/yak-ops-plugin-scheduler/")):
            selected.update(("schedule", "batch"))
            reasons.add("scheduler integration")
            continue
        if path.startswith("yak-flow/yak-flow-connector-jdbc/") and Path(path).name in (
                "JdbcMetadataApplier.java", "JdbcMetadataApplierTest.java"):
            selected.add("schema")
            reasons.add("target DDL contract")
            continue
        if path.startswith("yak-ops-boot/") and Path(path).name in (
                "DataSyncSchemaController.java", "DataSyncSchemaControllerTest.java"):
            selected.add("schema")
            reasons.add("schema preview controller")
            continue
        return full("shared backend or unclassified change")
    return plan(selected, reasons or ["empty diff"])


def changed_paths(base, head):
    # Only event-supplied commit IDs are accepted; never interpolate ref names into a shell.
    if not all(re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}", ref or "") for ref in (base, head)):
        raise ValueError("Missing or invalid comparison commit")
    result = subprocess.run(
        ["git", "diff", "--no-ext-diff", "--no-textconv", "--no-renames", "--name-only", "-z",
         base + "..." + head, "--"], check=True, capture_output=True, timeout=30)
    return [path.decode("utf-8", errors="replace") for path in result.stdout.split(b"\0") if path]


def select(mode, event, base, head):
    if mode not in MODES:
        raise ValueError("Unknown acceptance mode: " + mode)
    if mode == "full":
        return full("explicit full backend E2E")
    if mode in SUITES:
        return plan([mode], ["selected backend E2E suite"])
    if event != "pull_request":
        return full("automatic selection requires a PR comparison")
    try:
        return classify(changed_paths(base, head))
    except (ValueError, OSError, subprocess.SubprocessError):
        return full("comparison unavailable; conservative full fallback")

def verify_results(selection, scope, product):
    if scope != "success":
        raise ValueError("Backend test selection failed")
    suites = selection.get("suites")
    if (not isinstance(suites, list) or any(item not in (*SUITES, "full") for item in suites)
            or len(suites) != len(set(suites)) or ("full" in suites and len(suites) != 1)):
        raise ValueError("Invalid backend E2E selection")
    expected = "success" if suites else "skipped"
    if product != expected:
        raise ValueError(f"Backend E2E was {product}; expected {expected}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=MODES, default="auto")
    parser.add_argument("--check-results", action="store_true")
    args = parser.parse_args()
    if args.check_results:
        selection = json.loads(os.environ["BACKEND_TEST_PLAN"])
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
                summary.write("## Backend E2E Results\n\n")
                summary.write("Selected scope: `" + json.dumps(selection) + "`\n\n")
                for name in ("SCOPE_RESULT", "BACKEND_RESULT"):
                    summary.write(f"- {name}: `{os.environ[name]}`\n")
                summary.write("\nUnselected suites are NOT RUN; no test result exists.\n")
        verify_results(selection, os.environ["SCOPE_RESULT"], os.environ["BACKEND_RESULT"])
        if selection["suites"]:
            print("Selected backend tests passed; unselected suites were NOT RUN.")
        else:
            print("No backend suites selected: NOT RUN (selection completed).")
        return
    selection = select(args.suite, os.environ.get("CI_EVENT_NAME", ""),
                       os.environ.get("CI_PR_BASE_SHA", ""), os.environ.get("CI_PR_HEAD_SHA", ""))
    encoded = json.dumps(selection, separators=(",", ":"))
    print(encoded)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"plan={encoded}\n")
            output.write("suites=" + json.dumps(selection["suites"], separators=(",", ":")) + "\n")
            output.write("matrix=" + json.dumps({"suite": selection["suites"] or ["full"]}) + "\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write("## Backend E2E Selection\n\n```json\n" + encoded + "\n```\n\n")
            for name in ("CI_PR_BASE_SHA", "CI_PR_HEAD_SHA", "GITHUB_SHA"):
                summary.write(f"- {name}: `{os.environ.get(name, '')}`\n")
            summary.write("\nSelection uses the full PR diff, including both sides of renames. "
                          "Unselected suites are NOT RUN; no test result exists.\n")


if __name__ == "__main__":
    main()
