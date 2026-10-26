#!/usr/bin/env python3
"""Audit CDC integration-test methods and report slow cases without changing test execution."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
CDC = "yak-flow/yak-flow-connector-cdc-mysql"

# Deliberate method-level acceptance inventory. An added, renamed or removed @Test
# requires review of this inventory, including inherited pipeline tests.
PIPELINE_METHODS = (
    "restoresCheckpointAndConvergesMultiTableChangelogAtEachJdbcDestination",
    "evolvesExistingTableAndRecoversSchemaWithBinlogCheckpoint",
)
EXPECTED = {
    "cdc-binlog": {
        "MySqlBinlogIT": (
            "snapshotSessionUsesUtcWithoutChangingGlobalTimeZone",
            "streamsBinlogAndRestoresFromMailboxEmittedOffset",
            "realMultiRowTransactionIdentityRemainsUniqueAcrossBinlogRotation",
        ),
    },
    "cdc-snapshot": {
        "MySqlBackfillIT": (
            "backfillsRepeatedUpdatesDeletesCrossChunkMovesAndIgnoresOtherTables",
            "quietEmptyChunkAndFollowingScanFinishFromActualWatermarks",
            "plansKeylessStringChunksWithActualMySqlCollation",
            "typedKeyedChunkReconcilesOneCommittedTransactionAfterLowWithoutHoldingTheReadLock",
            "wideSnapshotChunkFailsBeforePublishingPartialOutputWhenByteBudgetIsExceeded",
            "limitedReplicationPrivilegesAllowJdbcScanButRejectTypedGlobalReadLock",
        ),
        "MySqlKeylessBackfillIT": (
            "reconcilesDuplicateRowsInTheLowHighWindowAndFreezesOneDurableChunk",
            "preservesTwoIdenticalImagesWithoutAnyBinlogChanges",
            "checkpointsAnOversizedRepeatedRowGroupFromRealMysqlWithoutDroppingOccurrences",
            "failsClosedIfTheChunkKeyChangesDuringSnapshot",
            "consistentReadViewPreventsDoubleCountingWritesAfterLowBeforeTypedScan",
            "binlogOnlyRemainsClosedButHybridSourceIsAdmitted",
        ),
    },
    "cdc-hybrid": {
        "MySqlHybridIT": (
            "keylessSnapshotAndBinlogPreserveDuplicateRows",
            "emptyTableFinishesSchemaBootstrapBeforeItsFirstInsert",
            "factoryPipelineSnapshotsMultipleTablesThenStreamsUpdatesDeletesAndKeyMoves",
            "identicalPayloadRowsRemainDistinctThroughKeyMovesAndStreamingRecovery",
            "partialExternalWritesRecoverFrozenChunkAndApplyLaterSourceDeletion",
            "streamingRestoreKeepsChunkFiltersAndResumesMailboxEmittedOffset",
        ),
    },
    "cdc-pipeline-mysql": {"MySqlCdcToMySqlIT": PIPELINE_METHODS},
    "cdc-pipeline-postgres": {"MySqlCdcToPostgresIT": PIPELINE_METHODS},
    "cdc-pipeline-oracle": {"MySqlCdcToOracleIT": PIPELINE_METHODS},
    "cdc-recovery": {
        "MySqlCdcToMySqlCrashRecoveryIT": (
            "replaysCommittedChangeAfterProcessCrashBeforeCheckpointAndDoesNotReplayAfterCheckpoint",
        ),
    },
}

TEST_PATTERN = re.compile(
    r"@Test\s+(?:(?:@\w+(?:\([^)]*\))?\s+)|(?:(?:public|protected|private|static|final)\s+))*"
    r"void\s+(\w+)\s*\(",
    re.MULTILINE,
)


def test_sources(test_root: Path, classname: str) -> list[Path]:
    """Include inherited @Test methods; pipeline subclasses own no test methods."""
    found = list(test_root.rglob(classname + ".java"))
    if len(found) != 1:
        raise ValueError(f"Expected exactly one Java source for {classname}, found {len(found)}")
    source = found[0]
    result = [source]
    declaration = source.read_text(encoding="utf-8")
    base = re.search(r"\bextends\s+(\w+)", declaration)
    if base:
        parent = list(test_root.rglob(base.group(1) + ".java"))
        if len(parent) != 1:
            raise ValueError(f"Cannot inspect inherited tests of {classname}: {base.group(1)}")
        result.extend(parent)
    return result


def source_methods(test_root: Path, classname: str) -> set[str]:
    names: list[str] = []
    for path in test_sources(test_root, classname):
        names.extend(TEST_PATTERN.findall(path.read_text(encoding="utf-8")))
    if len(names) != len(set(names)):
        raise ValueError(f"Duplicate source test methods in {classname}")
    return set(names)


def poll_locations(test_root: Path, classes: set[str]) -> list[str]:
    """Static wait-site hints; these are not measured wait durations."""
    locations = []
    paths: set[Path] = set()
    for classname in classes:
        try:
            paths.update(test_sources(test_root, classname))
        except ValueError:
            continue
    for path in sorted(paths):
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if "Thread.sleep(" in line or "TimeUnit.MILLISECONDS.sleep(" in line:
                locations.append(f"{path.name}:{line_no}")
    return sorted(set(locations))


def collect(root: Path, suite: str, inventory: dict | None = None) -> dict:
    """Collect partial results and validation errors, even when Maven failed."""
    selected = EXPECTED if inventory is None else inventory
    suites = list(EXPECTED) if suite == "full" else [suite]
    if any(name not in selected for name in suites):
        raise ValueError("Unsupported CDC suite: " + suite)

    module = root / CDC
    test_root = module / "src/test/java"
    expected = {name: tuple(methods) for group in suites for name, methods in selected[group].items()}
    errors: list[str] = []
    if inventory is None:
        from backend_tests import SUITES

        for name in suites:
            registered = set(SUITES[name][2])
            audited = set(selected[name])
            if registered != audited:
                errors.append(
                    f"{name}: test-class registration mismatch; "
                    f"missing={sorted(registered - audited)}, unexpected={sorted(audited - registered)}"
                )
    for classname, required in sorted(expected.items()):
        try:
            actual = source_methods(test_root, classname)
            if actual != set(required) or len(required) != len(set(required)):
                errors.append(
                    f"{classname}: Java @Test inventory mismatch; "
                    f"missing={sorted(set(required) - actual)}, unregistered={sorted(actual - set(required))}"
                )
        except (ValueError, OSError) as exc:
            errors.append(str(exc))

    observed: dict[str, dict[str, dict]] = {}
    report_paths = sorted((module / "target/failsafe-reports").glob("TEST-*.xml"))
    for path in report_paths:
        try:
            xml = ET.parse(path).getroot()
            report_suites = [xml] if xml.tag == "testsuite" else xml.findall("testsuite")
            for report_suite in report_suites:
                for key in ("failures", "errors", "skipped"):
                    if int(report_suite.get(key, "0")):
                        errors.append(f"{path.name}: nonzero {key}")
                for case in report_suite.findall("testcase"):
                    classname = case.get("classname", "").split(".")[-1]
                    if classname not in expected:
                        continue
                    method = case.get("name", "")
                    if not method:
                        errors.append(f"{classname}: unnamed test case in {path.name}")
                        continue
                    if method in observed.setdefault(classname, {}):
                        errors.append(f"{classname}.{method}: duplicate result")
                    try:
                        seconds = float(case.attrib["time"])
                    except (ValueError, KeyError):
                        errors.append(f"{classname}.{method}: missing or invalid time")
                        seconds = 0.0
                    status = next(
                        (status for status in ("failure", "error", "skipped") if case.find(status) is not None),
                        "passed",
                    )
                    if status != "passed":
                        errors.append(f"{classname}.{method}: {status}")
                    observed[classname][method] = {
                        "class": classname, "method": method, "seconds": round(seconds, 3), "status": status
                    }
        except (ET.ParseError, OSError, ValueError) as exc:
            errors.append(f"{path.name}: invalid JUnit report ({exc})")

    for classname, required in sorted(expected.items()):
        actual = set(observed.get(classname, {}))
        if actual != set(required):
            errors.append(
                f"{classname}: missing={sorted(set(required) - actual)}, unexpected={sorted(actual - set(required))}"
            )
    cases = sorted(
        (record for methods in observed.values() for record in methods.values()),
        key=lambda item: (-item["seconds"], item["class"], item["method"]),
    )
    startups = []
    seen: set[Path] = set()
    for base in (module / "target/test-artifacts/containers", root / "target/test-artifacts/containers"):
        for path in sorted(base.glob("**/startup.json")):
            if path in seen:
                continue
            seen.add(path)
            try:
                payload = json.loads(path.read_text(encoding="utf-8"))
                startup = {"resource": str(path.parent.relative_to(base)), **payload}
                startups.append(startup)
            except (ValueError, OSError):
                errors.append(f"Cannot read container timing: {path}")

    return {
        "suite": suite,
        "expectedMethods": sum(len(methods) for methods in expected.values()),
        "observedMethods": len(cases),
        "cases": cases,
        "databaseStartup": startups,
        "pollLocations": poll_locations(test_root, set(expected)),
        "errors": errors,
        "status": "FAIL" if errors else "PASS",
    }


def markdown(report: dict) -> str:
    lines = [
        f"### CDC method audit: {report['suite']} ({report['status']})",
        "",
        f"Methods: {report['observedMethods']}/{report['expectedMethods']}; "
        f"sum of method durations: {sum(case['seconds'] for case in report['cases']):.1f}s "
        "(not CI wall-clock time).",
        "",
        "| Slowest test method | Seconds | Status |",
        "| --- | ---: | --- |",
    ]
    for case in report["cases"][:15]:
        lines.append(f"| `{case['class']}.{case['method']}` | {case['seconds']:.2f} | {case['status']} |")
    if not report["cases"]:
        lines.append("| No recorded methods | - | - |")
    lines.extend(["", "#### Disposable database startup", "", "| Resource | Container ms | SQL-ready ms | Total ms |",
                  "| --- | ---: | ---: | ---: |"])
    for resource in report["databaseStartup"]:
        lines.append(
            f"| `{resource['resource']}` | {resource.get('containerStartupMillis', '-')} | "
            f"{resource.get('sqlReadyMillis', '-')} | {resource.get('elapsedMillis', '-')} |"
        )
    if not report["databaseStartup"]:
        lines.append("| Not recorded | - | - | - |")
    lines.extend(["", "#### Polling locations (static source hints, not measured wait durations)", ""])
    lines.append(", ".join(f"`{name}`" for name in report["pollLocations"]) or "None")
    if report["errors"]:
        lines.extend(["", "#### Audit failures", ""])
        lines.extend(f"- {message}" for message in report["errors"])
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", choices=(*EXPECTED, "full"), required=True)
    args = parser.parse_args()
    report = collect(ROOT, args.suite)
    evidence = ROOT / "target/test-artifacts"
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence / f"cdc-audit-{args.suite}.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    output = markdown(report)
    (evidence / f"cdc-audit-{args.suite}.md").write_text(output, encoding="utf-8")
    print(output)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(output)
    if report["errors"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
