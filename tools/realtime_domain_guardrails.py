#!/usr/bin/env python3
"""Zero-dependency guardrails for the Realtime Sync domain."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "yak-ops-business/yak-ops-business-sync/yak-ops-business-sync-realtime"
JAVA_ROOT = MODULE / "src/main/java/io/yak/ops/business/sync/realtime"
DOMAIN_DIR = JAVA_ROOT / "domain"
DOC_DIR = ROOT / "docs/realtime-sync/domain"

CORE_FILES = (
    "RealtimeJobState.java",
    "SyncDefinition.java",
    "RuntimeEnvironmentRef.java",
    "DefinitionDigest.java",
    "SyncDefinitionDigestCalculator.java",
    "DefinitionVersion.java",
    "SyncExecution.java",
    "SyncExecutionStateMachine.java",
)

FORBIDDEN_IMPORTS = (
    "org.springframework.",
    "com.fasterxml.jackson.",
    "com.baomidou.",
    "org.mybatis.",
    "jakarta.persistence.",
    "io.yak.ops.business.sync.realtime.controller.",
    "io.yak.ops.business.sync.realtime.service.",
    "io.yak.ops.business.sync.realtime.repository.",
    "io.yak.ops.business.sync.realtime.dao.",
    "io.yak.ops.business.sync.realtime.engine.",
)

FORBIDDEN_CORE_NAMES = (
    "pipelineYaml",
    "flinkHome",
    "flinkCdcHome",
    "flinkRestUrl",
    "sshHost",
    "sshUser",
    "identityFile",
    "jdbcUrl",
    "password",
    "sceneType",
    "syncType",
)


class Guard:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.checks = 0

    def check(self, ok: bool, message: str) -> None:
        self.checks += 1
        if not ok:
            self.errors.append(message)

    def read(self, path: Path) -> str:
        self.check(path.exists(), f"Missing required file: {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8") if path.exists() else ""

    def finish(self) -> int:
        if self.errors:
            print("Realtime Sync Domain Guardrails: FAILED")
            for i, error in enumerate(self.errors, 1):
                print(f"{i}. {error}")
            return 1
        print(f"Realtime Sync Domain Guardrails: OK ({self.checks} checks)")
        return 0


def code_only(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    return text


def check_core(g: Guard) -> None:
    for name in CORE_FILES:
        text = g.read(DOMAIN_DIR / name)
        imports = re.findall(r"(?m)^\s*import\s+(?:static\s+)?([^;]+);", text)
        for imported in imports:
            g.check(
                imported.startswith("java.")
                or imported.startswith("io.yak.ops.business.sync.realtime.domain."),
                f"{name}: Core import must be JDK/domain-local: {imported}",
            )
            g.check(
                not imported.startswith(FORBIDDEN_IMPORTS),
                f"{name}: framework/adapter import is forbidden: {imported}",
            )
        code = code_only(text)
        for symbol in FORBIDDEN_CORE_NAMES:
            g.check(
                re.search(rf"\b{re.escape(symbol)}\b", code) is None,
                f"{name}: infrastructure/scenario symbol leaked into Core: {symbol}",
            )

    for path in DOMAIN_DIR.glob("*.java"):
        g.check(
            not re.match(r"^(Wizard|Yaml|Flink|Mysql|Postgres|Kafka).*(Spec|Definition|Task)\.java$", path.name, re.I),
            f"Second domain truth requires review: {path.name}",
        )
        code = code_only(path.read_text(encoding="utf-8"))
        for symbol in ("sceneType", "syncType"):
            g.check(
                re.search(rf"\b{symbol}\b", code) is None,
                f"Scenario discriminator requires domain review: {path.name}:{symbol}",
            )


def check_runtime_truth(g: Guard) -> None:
    dao = g.read(JAVA_ROOT / "dao/impl/RealtimeJobDaoImpl.java")
    query = g.read(MODULE / "src/main/resources/mapper/realtime/RealtimeJobQueryMapper.xml")
    store = g.read(JAVA_ROOT / "repository/RealtimeJobStore.java")

    for token in (
        "RealtimeJobDefinitionPO::getDesiredState",
        "RealtimeJobDefinitionPO::getObservedState",
        "RealtimeJobDefinitionPO::getLastError",
    ):
        g.check(token not in dao, f"Task runtime dual-write reintroduced: {token}")

    for token in ("d.desired_state", "d.observed_state", "d.last_error"):
        g.check(token not in query, f"Task runtime fallback reintroduced: {token}")

    for method in ("desiredJobs", "hasOtherDesiredRunning", "markStarting"):
        g.check(not re.search(rf"\b{method}\s*\(", store), f"Legacy runtime side-path reintroduced: {method}")

    g.check(
        "p.definition_version_id" in query and "d.published_definition_version_id" in query,
        "publishedUpdateAvailable must compare immutable DefinitionVersion IDs",
    )


def check_commands(g: Guard) -> None:
    service = g.read(JAVA_ROOT / "service/RealtimeJobService.java")
    controller = g.read(JAVA_ROOT / "controller/v1/RealtimeJobController.java")
    frontend = g.read(ROOT / "yak-ops-ui/src/pages/realtime-sync/api.ts")

    g.check("restartExecution(" in service, "RestartExecution semantics missing")
    g.check("applyPublishedVersion(" in service, "ApplyPublishedVersion semantics missing")
    g.check(
        re.search(r"\bpublic\s+[^\n{;]+\brestart\s*\(", service) is None,
        "Generic Application restart() must not return",
    )
    g.check("service.restart(" not in controller, "Controller must not use generic restart()")
    if '@PostMapping("/{id}/restart")' in controller:
        g.check("service.restartExecution(id, key)" in controller, "Legacy /restart must alias restartExecution()")

    g.check("'restart-execution'" in frontend, "Frontend restart-execution action missing")
    g.check("'apply-published-version'" in frontend, "Frontend apply-published-version action missing")
    g.check(not re.search(r"\|\s*'restart'\b", frontend), "Generic frontend restart action must not return")

    g.check("requirePublishedDefinition(id)" in service, "Start must resolve Published DefinitionVersion")
    g.check("prepare(id, true)" not in service, "Start must not fall back to mutable Draft")


def check_semantic_names(g: Guard) -> None:
    store = g.read(JAVA_ROOT / "repository/RealtimeJobStore.java")
    view = g.read(DOMAIN_DIR / "RealtimeJobView.java")
    text = store + view
    for symbol in ("sourceConfigDigest()", "artifactDigest()", "draftRevision()"):
        g.check(symbol in text, f"Missing semantic compatibility alias: {symbol}")


def check_docs(g: Guard) -> None:
    requirements = g.read(MODULE / "REQUIREMENTS.md")
    contract = g.read(MODULE / "DOMAIN.md")
    review = g.read(MODULE / "REVIEW.md")
    overview = g.read(DOC_DIR / "README.md")
    decisions = g.read(DOC_DIR / "DECISIONS.md")

    for name, text, limit in (
        ("REQUIREMENTS.md", requirements, 150),
        ("DOMAIN.md", contract, 180),
        ("REVIEW.md", review, 200),
    ):
        g.check(
            len(text.splitlines()) <= limit,
            f"{name} exceeds {limit} lines; keep module contracts concise and move detail to code/tests/Git history",
        )

    for phrase in ("核心能力", "模块边界", "Requirement Gap"):
        g.check(phrase in requirements, f"REQUIREMENTS.md lost required phrase: {phrase}")

    for phrase in (
        "RealtimeSyncTask",
        "DefinitionVersion",
        "SyncExecution",
        "Domain Impact Analysis",
        "Domain Compliance Report",
    ):
        g.check(phrase in contract, f"DOMAIN.md lost mandatory phrase: {phrase}")

    for phrase in (
        "Requirement Gap",
        "Domain Gap",
        "P0 Blocker",
        "P1 Must Fix",
        "Conclusion: PASS | CHANGES_REQUIRED",
        "Missing Tests",
    ):
        g.check(phrase in review, f"REVIEW.md lost review protocol phrase: {phrase}")

    g.check("历史设计过程看 Git / PR" in overview, "README.md must describe current model, not stage history")
    g.check("文档保持小" in decisions, "DECISIONS.md must preserve the documentation-budget decision")


def check_pr(g: Guard, event_path: Path | None) -> None:
    if not event_path:
        return
    event = json.loads(event_path.read_text(encoding="utf-8"))
    pr = event.get("pull_request")
    if not pr:
        return
    body = pr.get("body") or ""
    for phrase in ("Domain Impact Analysis", "Domain Gap", "Domain Compliance Report"):
        g.check(phrase.lower() in body.lower(), f"PR body must contain '{phrase}'")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", type=Path)
    args = parser.parse_args()

    g = Guard()
    check_core(g)
    check_runtime_truth(g)
    check_commands(g)
    check_semantic_names(g)
    check_docs(g)
    check_pr(g, args.event)
    return g.finish()


if __name__ == "__main__":
    sys.exit(main())
