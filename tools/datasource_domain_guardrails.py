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
CATALOG_DOMAIN_DIR = DOMAIN_DIR / "catalog"
EXECUTION_DOMAIN_DIR = JAVA_ROOT / "execution/domain"
GATEWAY_DIR = JAVA_ROOT / "gateway"

CORE_FILES = (
    "ConnectionProfile.java",
    "DataSourceDefinition.java",
    "DataSourceQuery.java",
    "DataSourceSummary.java",
)

CATALOG_DOMAIN_FILES = (
    "CatalogReadRequest.java",
    "CatalogTableQuery.java",
    "CatalogTablePath.java",
    "CatalogTable.java",
    "CatalogColumn.java",
    "CatalogQueryResult.java",
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

PORT_FORBIDDEN_IMPORTS = (
    "io.yak.ops.spi.datasource.",
    "io.yak.ops.common.bean.dto.",
    "io.yak.ops.common.bean.vo.",
    "io.yak.ops.common.bean.po.",
    "com.baomidou.",
    "org.mybatis.",
)

PROTECTED_APPLICATION_FILES = (
    "service/impl/DataSourceServiceImpl.java",
    "service/impl/DataSourceCatalogServiceImpl.java",
    "service/support/DataSourceViewMapper.java",
    "execution/DefaultSqlExecutionRuntime.java",
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


def imports_of(text: str) -> list[str]:
    return re.findall(r"(?m)^\s*import\s+(?:static\s+)?([^;]+);", text)


def check_no_forbidden_imports(g: Guard, name: str, text: str) -> None:
    for imported in imports_of(text):
        g.check(
            not imported.startswith(FORBIDDEN_IMPORTS),
            f"{name}: framework/adapter import is forbidden: {imported}",
        )


def check_core(g: Guard) -> None:
    for name in CORE_FILES:
        check_no_forbidden_imports(g, name, g.read(DOMAIN_DIR / name))

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
    for field in ("jdbcUrl", "connectionParams", "originalJson"):
        g.check(
            f"@ToString.Exclude private String {field};" in definition,
            f"DataSourceDefinition {field} must stay excluded from toString",
        )


def check_catalog_domain(g: Guard) -> None:
    for name in CATALOG_DOMAIN_FILES:
        text = g.read(CATALOG_DOMAIN_DIR / name)
        check_no_forbidden_imports(g, f"catalog/{name}", text)

    request = g.read(CATALOG_DOMAIN_DIR / "CatalogReadRequest.java")
    g.check("enum ReadMode" in request, "CatalogReadRequest must own explicit TABLE/SQL mode")
    g.check("TABLE" in request and "SQL" in request, "Catalog read modes must remain explicit")

    gateway = g.read(GATEWAY_DIR / "DataSourceCatalogGateway.java")
    code = code_only(gateway)
    g.check("CatalogReadRequest" in gateway, "Catalog gateway must use typed CatalogReadRequest")
    g.check("Map<String" not in code and "Map<" not in code, "Catalog gateway must not expose Map protocol")
    for name in ("CatalogTable", "CatalogColumn", "CatalogQueryResult"):
        g.check(name in gateway, f"Catalog gateway must expose business catalog model: {name}")

    service = g.read(JAVA_ROOT / "service/impl/DataSourceCatalogServiceImpl.java")
    g.check("toCatalogReadRequest(" in service, "HTTP Catalog Map must be parsed once at application boundary")
    g.check("CatalogReadRequest" in service, "Catalog application service must use typed request")

    adapter = g.read(GATEWAY_DIR / "adapter/SpiDataSourceCatalogGateway.java")
    g.check("toPluginRequest(" in adapter, "SPI catalog adapter must own legacy Map projection")
    g.check("Map<String, Object>" in adapter, "Legacy Plugin Map must stay isolated in SPI adapter")


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


def check_gateway_boundary(g: Guard) -> None:
    ports = (
        "DataSourcePluginGateway.java",
        "DataSourceCatalogGateway.java",
        "SqlExecutionGateway.java",
    )
    for name in ports:
        text = g.read(GATEWAY_DIR / name)
        for imported in imports_of(text):
            g.check(
                not imported.startswith(PORT_FORBIDDEN_IMPORTS),
                f"{name}: business gateway contract leaked external model: {imported}",
            )

    for relative in PROTECTED_APPLICATION_FILES:
        text = g.read(JAVA_ROOT / relative)
        for imported in imports_of(text):
            g.check(
                not imported.startswith("io.yak.ops.spi.datasource."),
                f"{relative}: Datasource Plugin SPI must stay behind Gateway Adapter: {imported}",
            )
            g.check(
                imported != "io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry",
                f"{relative}: Plugin Registry must stay behind Gateway Adapter",
            )
            g.check(
                imported != "io.yak.ops.business.datasource.util.DataSourceSecretCodec",
                f"{relative}: SPI secret helper must stay behind Gateway Adapter",
            )

    service = g.read(JAVA_ROOT / "service/impl/DataSourceServiceImpl.java")
    catalog_service = g.read(JAVA_ROOT / "service/impl/DataSourceCatalogServiceImpl.java")
    view_mapper = g.read(JAVA_ROOT / "service/support/DataSourceViewMapper.java")
    runtime = g.read(JAVA_ROOT / "execution/DefaultSqlExecutionRuntime.java")
    g.check("DataSourcePluginGateway" in service, "DataSourceServiceImpl must use DataSourcePluginGateway")
    g.check("DataSourceCatalogGateway" in catalog_service, "DataSourceCatalogServiceImpl must use DataSourceCatalogGateway")
    g.check("DataSourcePluginGateway" in view_mapper, "DataSourceViewMapper must use DataSourcePluginGateway for masking")
    g.check("SqlExecutionGateway" in runtime, "DefaultSqlExecutionRuntime must use SqlExecutionGateway")

    adapters = (
        ("adapter/SpiDataSourcePluginGateway.java", "implements DataSourcePluginGateway"),
        ("adapter/SpiDataSourceCatalogGateway.java", "implements DataSourceCatalogGateway"),
        ("adapter/SpiSqlExecutionGateway.java", "implements SqlExecutionGateway"),
    )
    for relative, marker in adapters:
        text = g.read(GATEWAY_DIR / relative)
        g.check(marker in text, f"{relative}: SPI adapter must implement business port")
        g.check("io.yak.ops.spi.datasource" in text, f"{relative}: adapter must own plugin protocol translation")


def check_sql_execution_domain(g: Guard) -> None:
    aggregate = g.read(EXECUTION_DOMAIN_DIR / "SqlExecutionAggregate.java")
    for imported in imports_of(aggregate):
        g.check(
            not imported.startswith("io.yak.ops.spi.datasource."),
            f"SqlExecutionAggregate must not import datasource SPI: {imported}",
        )
        g.check(
            not imported.startswith("org.springframework."),
            f"SqlExecutionAggregate must not import Spring: {imported}",
        )
        g.check(
            not imported.startswith("java.util.concurrent."),
            f"SqlExecutionAggregate must not own concurrency runtime: {imported}",
        )

    code = code_only(aggregate)
    for behavior in (
        "requestCancel(",
        "markRunning(",
        "markStatementRunning(",
        "markStatementSucceeded(",
        "finishSucceeded(",
        "finishFailed(",
        "finishTimedOut(",
        "finishCancelled(",
        "snapshot(",
    ):
        g.check(behavior in code, f"SqlExecutionAggregate lifecycle behavior missing: {behavior}")

    runtime = g.read(JAVA_ROOT / "execution/DefaultSqlExecutionRuntime.java")
    runtime_code = code_only(runtime)
    for forbidden in (
        "DataSourceExecutionProvider",
        "DataSourceSqlExecutor",
        "DataSourceSqlRequest",
        "DataSourceSqlResult",
        "class ManagedExecution",
        "class MutableStatement",
    ):
        g.check(forbidden not in runtime_code, f"DefaultSqlExecutionRuntime leaked physical/lifecycle concern: {forbidden}")
    g.check("SqlExecutionAggregate" in runtime, "Runtime must delegate lifecycle to SqlExecutionAggregate")
    g.check("SqlExecutionGateway" in runtime, "Runtime must delegate physical SQL to SqlExecutionGateway")


def check_docs(g: Guard) -> None:
    requirements = g.read(MODULE / "REQUIREMENTS.md")
    contract = g.read(MODULE / "DOMAIN.md")
    review = g.read(MODULE / "REVIEW.md")
    overview = g.read(MODULE / "README.md")

    for name, text, limit in (
        ("REQUIREMENTS.md", requirements, 150),
        ("DOMAIN.md", contract, 190),
        ("REVIEW.md", review, 220),
    ):
        g.check(
            len(text.splitlines()) <= limit,
            f"{name} exceeds {limit} lines; keep module contracts concise",
        )

    for phrase in ("核心能力", "模块边界", "Requirement Gap", "CatalogReadRequest", "SQL Execution"):
        g.check(phrase in requirements, f"REQUIREMENTS.md lost required phrase: {phrase}")

    for phrase in (
        "DataSourceDefinition",
        "ConnectionProfile",
        "12 条硬规则",
        "DataSourcePluginGateway",
        "DataSourceCatalogGateway",
        "CatalogReadRequest",
        "SqlExecutionGateway",
        "SqlExecutionAggregate",
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
        "DataSourcePluginGateway",
        "DataSourceCatalogGateway",
        "SqlExecutionGateway",
        "SqlExecutionAggregate",
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
    check_catalog_domain(g)
    check_application_mutations(g)
    check_gateway_boundary(g)
    check_sql_execution_domain(g)
    check_docs(g)
    check_pr(g, args.event)
    return g.finish()


if __name__ == "__main__":
    sys.exit(main())
