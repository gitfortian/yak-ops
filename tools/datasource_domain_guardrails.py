#!/usr/bin/env python3
"""Zero-dependency guardrails for the Datasource domain."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "yak-ops-business/yak-ops-business-datasource"
JAVA_ROOT = MODULE / "src/main/java/io/yak/ops/business/datasource"
DOMAIN_DIR = JAVA_ROOT / "domain"

CORE_FILES = (
    "ConnectionProfile.java",
    "DataSourceDefinition.java",
    "DataSourceQuery.java",
    "DataSourceSummary.java",
)

FORBIDDEN_IMPORTS = (
    "org.springframework.",
    "com.baomidou.",
    "org.mybatis.",
    "jakarta.persistence.",
    "io.yak.ops.common.bean.dto.",
    "io.yak.ops.common.bean.vo.",
    "io.yak.ops.common.bean.po.",
    "io.yak.ops.spi.datasource.",
    "io.yak.ops.business.datasource.controller.",
    "io.yak.ops.business.datasource.service.",
    "io.yak.ops.business.datasource.repository.",
    "io.yak.ops.business.datasource.dao.",
    "io.yak.ops.business.datasource.plugin.",
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
            print("Datasource Domain Guardrails: FAILED")
            for i, error in enumerate(self.errors, 1):
                print(f"{i}. {error}")
            return 1
        print(f"Datasource Domain Guardrails: OK ({self.checks} checks)")
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
                not imported.startswith(FORBIDDEN_IMPORTS),
                f"{name}: framework/adapter import is forbidden: {imported}",
            )

    definition = g.read(DOMAIN_DIR / "DataSourceDefinition.java")
    profile = g.read(DOMAIN_DIR / "ConnectionProfile.java")
    code = code_only(definition)

    for behavior in (
        "create(",
        "updateConfiguration(",
        "replaceConnectionProfile(",
        "assertTypeUnchanged(",
        "markConnected(",
        "markDisconnected(",
        "markConnectionUnknown(",
    ):
        g.check(behavior in code, f"DataSource aggregate behavior missing: {behavior}")

    g.check(
        "record ConnectionProfile" in profile,
        "ConnectionProfile must remain an immutable record value object",
    )
    g.check(
        '@ToString.Exclude private String jdbcUrl;' in definition,
        "DataSourceDefinition jdbcUrl must stay excluded from toString",
    )
    g.check(
        '@ToString.Exclude private String connectionParams;' in definition,
        "DataSourceDefinition connectionParams must stay excluded from toString",
    )
    g.check(
        '@ToString.Exclude private String originalJson;' in definition,
        "DataSourceDefinition originalJson must stay excluded from toString",
    )


def check_application_mutations(g: Guard) -> None:
    service = g.read(JAVA_ROOT / "service/impl/DataSourceServiceImpl.java")
    code = code_only(service)

    for required in (
        "DataSourceDefinition.create(",
        "existing.assertTypeUnchanged(",
        "existing.updateConfiguration(",
        "definition.markConnected(",
        "definition.markDisconnected(",
    ):
        g.check(required in code, f"Application service bypassed aggregate behavior: missing {required}")

    for forbidden in (
        ".setConnStatus(",
        ".setJdbcUrl(",
        ".setConnectionParams(",
        ".setOriginalJson(",
    ):
        g.check(forbidden not in code, f"Application service must not mutate aggregate scalar directly: {forbidden}")


def check_docs(g: Guard) -> None:
    requirements = g.read(MODULE / "REQUIREMENTS.md")
    contract = g.read(MODULE / "DOMAIN.md")
    review = g.read(MODULE / "REVIEW.md")
    overview = g.read(MODULE / "README.md")

    for name, text, limit in (
        ("REQUIREMENTS.md", requirements, 150),
        ("DOMAIN.md", contract, 180),
        ("REVIEW.md", review, 210),
    ):
        g.check(
            len(text.splitlines()) <= limit,
            f"{name} exceeds {limit} lines; keep module contracts concise",
        )

    for phrase in ("核心能力", "模块边界", "Requirement Gap"):
        g.check(phrase in requirements, f"REQUIREMENTS.md lost required phrase: {phrase}")

    for phrase in (
        "DataSourceDefinition",
        "ConnectionProfile",
        "12 条硬规则",
        "Domain Impact Analysis",
        "Domain Compliance Report",
        "Domain Gap",
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

    for name in ("REQUIREMENTS.md", "DOMAIN.md", "REVIEW.md"):
        g.check(name in overview, f"README.md must link {name}")


def check_pr(g: Guard, event_path: Path | None) -> None:
    if not event_path:
        return
    event = json.loads(event_path.read_text(encoding="utf-8"))
    pr = event.get("pull_request")
    if not pr:
        return
    body = pr.get("body") or ""
    for phrase in (
        "Domain Impact Analysis",
        "Domain Gap",
        "Compatibility",
        "Domain Compliance Report",
    ):
        g.check(phrase.lower() in body.lower(), f"PR body must contain '{phrase}'")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", type=Path)
    args = parser.parse_args()

    g = Guard()
    check_core(g)
    check_application_mutations(g)
    check_docs(g)
    check_pr(g, args.event)
    return g.finish()


if __name__ == "__main__":
    sys.exit(main())
