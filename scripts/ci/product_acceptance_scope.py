#!/usr/bin/env python3
"""Select product acceptance from the complete PR diff; uncertainty requires full coverage."""

import argparse
from fnmatch import fnmatchcase
import json
import os
from pathlib import Path
import re
import subprocess

SUITES = ("batch", "schedule", "realtime", "schema")
MODES = ("auto", "full", "browser", *SUITES)
UI = "yak-ops-ui/"
APP = UI + "apps/web/"


def plan(browser=False, suites=(), real_browser=False, reasons=()):
    selected = ["full"] if "full" in suites else [item for item in SUITES if item in suites]
    return {"browser": browser, "suites": selected, "real_browser": real_browser,
            "reasons": sorted(set(reasons))}


def full(reason):
    return plan(True, ["full"], True, [reason])


def classify(paths):
    browser, real_browser, selected, reasons = False, False, set(), set()
    for path in paths:
        if path.startswith((".github/workflows/", ".github/actions/", "scripts/ci/")) or path in {
                "scripts/acceptance/run_product_acceptance.py", "scripts/acceptance/task-execution-e2e.py"}:
            return full("acceptance infrastructure changed")
        if Path(path).suffix.lower() in {".md", ".mdx", ".rst"} or path in {"LICENSE", "NOTICE"}:
            reasons.add("documentation only")
            continue
        if path == "scripts/acceptance/task_schedule_ui_test.py":
            browser = True
            reasons.add("controlled browser contracts")
            continue
        script_suite = next((suite for pattern, suite in (
            ("scripts/acceptance/multitable_*.py", "batch"),
            ("scripts/acceptance/task_schedule_browser.py", "schedule"),
            ("scripts/acceptance/database_realtime*.py", "realtime"),
            ("scripts/acceptance/schema_migration*.py", "schema"),
        ) if fnmatchcase(path, pattern)), None)
        if script_suite:
            selected.add(script_suite)
            real_browser = True  # Test changes exercise their complete suite, including its UI.
            reasons.add(script_suite + " acceptance changed")
            continue
        if path.startswith(UI):
            browser = True
            reasons.add("frontend browser contracts")
            if Path(path).suffix.lower() in {".css", ".svg", ".png", ".jpg", ".webp", ".woff2"}:
                continue
            if path == APP + "app/data-sync/schema-preview.tsx":
                selected.add("schema")
            elif path.startswith(APP + "service/data-sync/") or path in {
                    APP + "app/data-sync/task-editor.tsx", APP + "app/data-sync/table-mapping-editor.tsx"}:
                selected.update(("batch", "realtime", "schema"))
            elif "schedule" in path or "/cron-scheduler/" in path:
                selected.add("schedule")
            elif path.startswith(APP + "service/") or Path(path).name in {
                    "package.json", "package-lock.json", "vite.config.ts"}:
                return full("shared frontend transport or dependency changed")
            if selected:
                real_browser = True
            continue
        if path.startswith(("yak-ops-business/yak-ops-business-schedule/",
                            "yak-ops-plugins/yak-ops-plugin-scheduler/")):
            selected.update(("schedule", "batch"))
            reasons.add("scheduler integration")
            continue
        if path.startswith("yak-flow/yak-flow-connector-jdbc/") and Path(path).name in {
                "JdbcMetadataApplier.java", "JdbcMetadataApplierTest.java"}:
            selected.add("schema")
            reasons.add("initial schema application")
            continue
        if path.startswith("yak-ops-boot/") and Path(path).name in {
                "DataSyncSchemaController.java", "DataSyncSchemaControllerTest.java"}:
            selected.add("schema")
            reasons.add("schema preview authorization")
            continue
        # Core, Runtime, Task lifecycle, datasource, DAO and unclassified changes are shared risk.
        return full("shared backend or unclassified change")
    return plan(browser, selected, real_browser, reasons or ["empty diff"])


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
        return full("explicit or reusable full acceptance")
    if mode == "browser":
        return plan(True, reasons=["explicit browser contracts"])
    if mode in SUITES:
        return plan(False, [mode], True, ["explicit focused acceptance"])
    if event != "pull_request":
        return full("automatic selection requires a PR comparison")
    try:
        return classify(changed_paths(base, head))
    except (ValueError, OSError, subprocess.SubprocessError):
        return full("comparison unavailable; conservative full fallback")


def verify_results(selection, scope, browser, product):
    if scope != "success":
        raise ValueError("Acceptance scope did not succeed")
    suites = selection.get("suites")
    if (type(selection.get("browser")) is not bool or type(selection.get("real_browser")) is not bool
            or not isinstance(suites, list) or any(item not in (*SUITES, "full") for item in suites)
            or len(suites) != len(set(suites)) or ("full" in suites and len(suites) != 1)):
        raise ValueError("Invalid acceptance selection")
    for name, required, actual in (("browser", selection["browser"], browser),
                                    ("product", bool(suites), product)):
        expected = "success" if required else "skipped"
        if actual != expected:
            raise ValueError(f"{name} acceptance was {actual}; expected {expected}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=MODES, default="auto")
    parser.add_argument("--check-results", action="store_true")
    args = parser.parse_args()
    if args.check_results:
        selection = json.loads(os.environ["ACCEPTANCE_PLAN"])
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
                summary.write("## Product acceptance gate\n\n")
                summary.write("Selected scope: `" + json.dumps(selection) + "`\n\n")
                for name in ("SCOPE_RESULT", "BROWSER_RESULT", "PRODUCT_RESULT"):
                    summary.write(f"- {name}: `{os.environ[name]}`\n")
                summary.write("\nUnselected suites are NOT EXECUTED, not passing acceptance.\n")
        verify_results(selection, os.environ["SCOPE_RESULT"],
                       os.environ["BROWSER_RESULT"], os.environ["PRODUCT_RESULT"])
        if selection["browser"] or selection["suites"]:
            print("Selected acceptance jobs succeeded; unselected suites are NOT EXECUTED.")
        else:
            print("No product suites selected: NOT EXECUTED (scope checks passed).")
        return
    selection = select(args.suite, os.environ.get("CI_EVENT_NAME", ""),
                       os.environ.get("CI_PR_BASE_SHA", ""), os.environ.get("CI_PR_HEAD_SHA", ""))
    encoded = json.dumps(selection, separators=(",", ":"))
    print(encoded)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"plan={encoded}\n")
            for name in ("browser", "real_browser"):
                output.write(f"{name}={str(selection[name]).lower()}\n")
            output.write("suites=" + json.dumps(selection["suites"], separators=(",", ":")) + "\n")
            output.write("matrix=" + json.dumps({"suite": selection["suites"] or ["full"]}) + "\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write("## Product acceptance scope\n\n```json\n" + encoded + "\n```\n\n")
            for name in ("CI_PR_BASE_SHA", "CI_PR_HEAD_SHA", "GITHUB_SHA"):
                summary.write(f"- {name}: `{os.environ.get(name, '')}`\n")
            summary.write("\nScope uses the complete PR diff, including both sides of renames. "
                          "Unselected suites are NOT EXECUTED, not passing acceptance.\n")


if __name__ == "__main__":
    main()
