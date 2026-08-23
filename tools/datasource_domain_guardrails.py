#!/usr/bin/env python3
"""Zero-dependency guardrails for the Datasource domain and plugin contract."""

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
PLUGIN_DOMAIN_DIR = DOMAIN_DIR / "plugin"
EXECUTION_DOMAIN_DIR = JAVA_ROOT / "execution/domain"
GATEWAY_DIR = JAVA_ROOT / "gateway"
PLUGIN_ROOT = ROOT / "yak-ops-plugins/yak-ops-plugin-datasource"
PLUGIN_API_DIR = (
    PLUGIN_ROOT
    / "yak-ops-plugin-datasource-api/src/main/java/io/yak/ops/spi/datasource"
)
PLUGIN_DOC = PLUGIN_ROOT / "PLUGIN.md"

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
    "service/impl/DataSourcePluginConfigServiceImpl.java",
    "service/support/DataSourceViewMapper.java",
    "service/support/DataSourcePluginViewMapper.java",
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

    g.check("record ConnectionProfile" in profile, "ConnectionProfile must remain immutable")
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
    g.check("enum ReadMode" in request, "CatalogReadRequest must own TABLE/SQL mode")
    g.check("TABLE" in request and "SQL" in request, "Catalog read modes must remain explicit")

    gateway = g.read(GATEWAY_DIR / "DataSourceCatalogGateway.java")
    gateway_code = code_only(gateway)
    g.check("CatalogReadRequest" in gateway, "Catalog gateway must use typed CatalogReadRequest")
    g.check("Map<" not in gateway_code, "Business Catalog gateway must not expose Map protocol")
    for name in ("CatalogTable", "CatalogColumn", "CatalogQueryResult"):
        g.check(name in gateway, f"Catalog gateway missing business model: {name}")

    service = g.read(JAVA_ROOT / "service/impl/DataSourceCatalogServiceImpl.java")
    g.check("toCatalogReadRequest(" in service, "HTTP Catalog Map must be parsed once")
    g.check("CatalogReadRequest" in service, "Catalog service must use typed request")

    adapter = g.read(GATEWAY_DIR / "adapter/SpiDataSourceCatalogGateway.java")
    adapter_code = code_only(adapter)
    g.check(
        "DataSourceCatalogReadRequest" in adapter,
        "Catalog SPI adapter must translate Business request to typed Plugin request",
    )
    for legacy in ("paramsList", '"read_mode"', "Map<String, Object>"):
        g.check(
            legacy not in adapter_code,
            f"Legacy Catalog Map leaked into executable Plugin adapter code: {legacy}",
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
        g.check(required in code, f"Application service bypassed aggregate behavior: {required}")
    for forbidden in (
        ".setConnStatus(",
        ".setJdbcUrl(",
        ".setConnectionParams(",
        ".setOriginalJson(",
    ):
        g.check(forbidden not in code, f"Application service must not mutate scalar: {forbidden}")


def check_gateway_boundary(g: Guard) -> None:
    for name in (
        "DataSourcePluginGateway.java",
        "DataSourceCatalogGateway.java",
        "SqlExecutionGateway.java",
    ):
        text = g.read(GATEWAY_DIR / name)
        for imported in imports_of(text):
            g.check(
                not imported.startswith(PORT_FORBIDDEN_IMPORTS),
                f"{name}: business gateway leaked external model: {imported}",
            )

    for relative in PROTECTED_APPLICATION_FILES:
        text = g.read(JAVA_ROOT / relative)
        for imported in imports_of(text):
            g.check(
                not imported.startswith("io.yak.ops.spi.datasource."),
                f"{relative}: Plugin SPI must stay behind Adapter: {imported}",
            )
            g.check(
                imported != "io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry",
                f"{relative}: Plugin Registry must stay behind Adapter",
            )
            g.check(
                imported != "io.yak.ops.business.datasource.util.DataSourceSecretCodec",
                f"{relative}: Secret helper must stay behind Adapter",
            )

    service = g.read(JAVA_ROOT / "service/impl/DataSourceServiceImpl.java")
    catalog_service = g.read(JAVA_ROOT / "service/impl/DataSourceCatalogServiceImpl.java")
    plugin_config_service = g.read(JAVA_ROOT / "service/impl/DataSourcePluginConfigServiceImpl.java")
    view_mapper = g.read(JAVA_ROOT / "service/support/DataSourceViewMapper.java")
    plugin_view_mapper = g.read(JAVA_ROOT / "service/support/DataSourcePluginViewMapper.java")
    runtime = g.read(JAVA_ROOT / "execution/DefaultSqlExecutionRuntime.java")
    g.check("DataSourcePluginGateway" in service, "DataSourceServiceImpl must use plugin gateway")
    g.check("DataSourceCatalogGateway" in catalog_service, "Catalog service must use catalog gateway")
    g.check("DataSourcePluginGateway" in plugin_config_service, "Plugin config service must use gateway")
    g.check("DataSourcePluginViewMapper" in plugin_config_service, "Plugin config service must use view mapper")
    g.check("DataSourcePluginGateway" in view_mapper, "DataSourceViewMapper must use plugin gateway")
    g.check("DataSourcePluginDescriptor" in plugin_view_mapper, "Plugin view mapper must project Business descriptor")
    g.check("SqlExecutionGateway" in runtime, "SQL Runtime must use SqlExecutionGateway")

    for relative, marker in (
        ("adapter/SpiDataSourcePluginGateway.java", "implements DataSourcePluginGateway"),
        ("adapter/SpiDataSourceCatalogGateway.java", "implements DataSourceCatalogGateway"),
        ("adapter/SpiSqlExecutionGateway.java", "implements SqlExecutionGateway"),
    ):
        text = g.read(GATEWAY_DIR / relative)
        g.check(marker in text, f"{relative}: SPI adapter must implement business port")
        g.check("io.yak.ops.spi.datasource" in text, f"{relative}: adapter must own SPI translation")


def check_plugin_contract(g: Guard) -> None:
    plugin_api = g.read(PLUGIN_API_DIR / "DataSourcePlugin.java")
    catalog_api = g.read(PLUGIN_API_DIR / "DataSourceCatalog.java")
    descriptor = g.read(PLUGIN_API_DIR / "DataSourcePluginDescriptor.java")
    capability = g.read(PLUGIN_API_DIR / "DataSourceCapability.java")
    typed_request = g.read(PLUGIN_API_DIR / "catalog/DataSourceCatalogReadRequest.java")

    g.check("descriptor()" in plugin_api, "DataSourcePlugin must expose descriptor()")
    g.check("pluginConfig(" not in plugin_api, "DataSourcePlugin.pluginConfig() must not return")
    g.check("DataSourcePluginConfigVO" not in plugin_api, "Plugin API must not depend on HTTP VO")
    g.check("DataSourceCatalogReadRequest" in catalog_api, "Plugin Catalog must use typed read request")
    g.check("Map<" not in code_only(catalog_api), "Plugin Catalog stable contract must be Map-free")
    g.check("CURRENT_API_VERSION" in descriptor, "Plugin Descriptor must expose API version")
    g.check("secretFieldKeys()" in descriptor, "Plugin Descriptor must declare secret-field semantics")
    g.check("record DataSourceCatalogReadRequest" in typed_request, "Typed Plugin Catalog request missing")

    for name in (
        "CONNECTION_TEST",
        "CATALOG_METADATA",
        "CATALOG_READ",
        "SQL_EXECUTION",
        "TRANSACTIONS",
        "SSH_TUNNEL",
    ):
        g.check(name in capability, f"Datasource capability missing: {name}")

    for path in PLUGIN_API_DIR.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for forbidden in (
            "io.yak.ops.common.bean.vo.",
            "io.yak.ops.common.bean.dto.",
            "io.yak.ops.business.datasource.",
        ):
            g.check(forbidden not in text, f"Plugin API leaked application model: {path.name}:{forbidden}")

    for module in ("yak-ops-plugin-datasource-jdbc", "yak-ops-plugin-datasource-doris"):
        source_root = PLUGIN_ROOT / module / "src/main/java"
        for path in source_root.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            g.check("DataSourcePluginConfigVO" not in text, f"Plugin implementation leaked HTTP VO: {path}")
            g.check("pluginConfig(" not in text, f"Legacy pluginConfig() implementation returned: {path}")

        service_file = (
            PLUGIN_ROOT
            / module
            / "src/main/resources/META-INF/services/io.yak.ops.spi.datasource.DataSourcePlugin"
        )
        g.check(service_file.exists(), f"{module}: ServiceLoader registration missing")

    registry = g.read(JAVA_ROOT / "plugin/DataSourcePluginRegistry.java")
    for marker in (
        "validateDescriptor(",
        "CURRENT_API_VERSION",
        "TRANSACTIONS",
        "SQL_EXECUTION",
        "CATALOG_READ",
        "CATALOG_METADATA",
    ):
        g.check(marker in registry, f"Plugin registry validation missing: {marker}")

    secret_codec = g.read(JAVA_ROOT / "util/DataSourceSecretCodec.java")
    g.check("DataSourcePluginDescriptor" in secret_codec, "Secret codec must read Plugin Descriptor")
    g.check("DataSourcePluginConfigVO" not in secret_codec, "Secret codec must not read HTTP VO")
    g.check("FormFieldVO" not in secret_codec, "Secret codec must not read VO form fields")

    business_descriptor = g.read(PLUGIN_DOMAIN_DIR / "DataSourcePluginDescriptor.java")
    check_no_forbidden_imports(g, "plugin/DataSourcePluginDescriptor.java", business_descriptor)

    plugin_doc = g.read(PLUGIN_DOC)
    for phrase in (
        "DataSourcePluginDescriptor",
        "DataSourceCapability",
        "DataSourceCatalogReadRequest",
        "ServiceLoader",
        "Phase 4 SPI 迁移",
        "Review Checklist",
    ):
        g.check(phrase in plugin_doc, f"PLUGIN.md lost required phrase: {phrase}")


def check_sql_execution_domain(g: Guard) -> None:
    aggregate = g.read(EXECUTION_DOMAIN_DIR / "SqlExecutionAggregate.java")
    for imported in imports_of(aggregate):
        g.check(not imported.startswith("io.yak.ops.spi.datasource."), f"Aggregate imported SPI: {imported}")
        g.check(not imported.startswith("org.springframework."), f"Aggregate imported Spring: {imported}")
        g.check(not imported.startswith("java.util.concurrent."), f"Aggregate owns concurrency: {imported}")

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
        g.check(behavior in code, f"SqlExecutionAggregate behavior missing: {behavior}")

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
        g.check(forbidden not in runtime_code, f"DefaultSqlExecutionRuntime leaked concern: {forbidden}")
    g.check("SqlExecutionAggregate" in runtime, "Runtime must delegate lifecycle to Aggregate")
    g.check("SqlExecutionGateway" in runtime, "Runtime must delegate physical SQL to Gateway")


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
        g.check(len(text.splitlines()) <= limit, f"{name} exceeds {limit} lines")

    for phrase in (
        "核心能力",
        "模块边界",
        "Requirement Gap",
        "CatalogReadRequest",
        "SQL Execution",
        "Plugin 标准",
        "DataSourcePluginDescriptor",
    ):
        g.check(phrase in requirements, f"REQUIREMENTS.md lost: {phrase}")

    for phrase in (
        "DataSourceDefinition",
        "ConnectionProfile",
        "12 条硬规则",
        "DataSourcePluginGateway",
        "DataSourceCatalogGateway",
        "CatalogReadRequest",
        "DataSourcePluginDescriptor",
        "Plugin Capability",
        "SqlExecutionGateway",
        "SqlExecutionAggregate",
        "Domain Impact Analysis",
        "Domain Compliance Report",
        "Domain Gap",
    ):
        g.check(phrase in contract, f"DOMAIN.md lost: {phrase}")

    for phrase in (
        "Requirement Gap",
        "Domain Gap",
        "P0 Blocker",
        "P1 Must Fix",
        "Conclusion: PASS | CHANGES_REQUIRED",
        "Missing Tests",
        "DataSourcePluginGateway",
        "DataSourceCatalogGateway",
        "DataSourcePluginConfigVO",
        "SqlExecutionGateway",
        "SqlExecutionAggregate",
        "PLUGIN.md",
    ):
        g.check(phrase in review, f"REVIEW.md lost: {phrase}")

    for name in ("REQUIREMENTS.md", "DOMAIN.md", "REVIEW.md", "PLUGIN.md"):
        g.check(name in overview, f"README.md must reference {name}")


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
    check_plugin_contract(g)
    check_sql_execution_domain(g)
    check_docs(g)
    check_pr(g, args.event)
    return g.finish()


if __name__ == "__main__":
    sys.exit(main())
