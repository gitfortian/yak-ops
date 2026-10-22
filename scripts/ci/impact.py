#!/usr/bin/env python3
"""One source of truth for PR quality, Maven modules and database-test impact."""
from __future__ import annotations

from functools import lru_cache
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SUITES = ("jdbc-mysql", "jdbc-postgres", "jdbc-oracle", "pipeline", "cdc", "cdc-pipeline", "business", "backend-e2e", "recovery-e2e")
JDBC = "yak-flow/yak-flow-connector-jdbc/"
CDC = "yak-flow/yak-flow-connector-cdc-mysql/"
RUNTIME = "yak-flow/yak-flow-runtime/"
E2E = "yak-ops-e2e/"
BUSINESS = "yak-ops-business/"
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


@lru_cache(maxsize=1)
def reactor_modules() -> tuple[str, ...]:
    """Read the actual nested reactor; new modules require no hard-coded path list."""
    result = []

    def visit(folder: Path) -> None:
        pom = ET.parse(folder / "pom.xml").getroot()
        children = [element.text for element in pom.findall("m:modules/m:module", NAMESPACE)]
        if children:
            for child in children:
                visit(folder / child)
        elif folder != ROOT:
            result.append(folder.relative_to(ROOT).as_posix())

    visit(ROOT)
    return tuple(result)


def module_for(path: str) -> list[str]:
    modules = reactor_modules()
    owners = [m for m in modules if path.startswith(m + "/")]
    if owners:
        return [max(owners, key=len)]
    # An aggregator POM or module configuration owns its children.
    if path.endswith("/pom.xml"):
        folder = path.removesuffix("/pom.xml")
        return [m for m in modules if m.startswith(folder + "/")]
    return []


@lru_cache(maxsize=1)
def module_dependents() -> dict[str, set[str]]:
    """Map reactor dependencies to consumers so changed contracts compile downstream."""
    projects = {}
    artifacts = {}
    for module in reactor_modules():
        project = ET.parse(ROOT / module / "pom.xml").getroot()
        projects[module] = project
        artifact = project.findtext("m:artifactId", namespaces=NAMESPACE)
        if artifact:
            artifacts[artifact] = module
    consumers = {module: set() for module in projects}
    for module, project in projects.items():
        for dependency in project.findall("m:dependencies/m:dependency", NAMESPACE):
            if dependency.findtext("m:groupId", namespaces=NAMESPACE) != "io.yak.ops":
                continue
            producer = artifacts.get(dependency.findtext("m:artifactId", namespaces=NAMESPACE))
            if producer:
                consumers[producer].add(module)
    return consumers


def affected_modules(owners: list[str]) -> set[str]:
    """Only changed modules and actual transitively dependent modules need unit checks."""
    consumers = module_dependents()
    selected = set(owners)
    pending = list(owners)
    while pending:
        for module in consumers.get(pending.pop(), set()):
            if module not in selected:
                selected.add(module)
                pending.append(module)
    return selected - {"yak-ops-ui", "yak-ops-dist"}


def test_suites(path: str) -> set[str]:
    """Select integration suites only for production code and the owning *IT tests."""
    if "/src/test/" in path:
        if not path.endswith("IT.java"):
            return set()
        filename = path.rsplit("/", 1)[-1]
        if path.startswith(JDBC):
            if "To" in filename or "/integration/" in path:
                return {"pipeline"}
            vendor = next((v for v in ("mysql", "postgres", "oracle") if v in filename.lower()), None)
            return {"jdbc-" + vendor} if vendor else {"pipeline"}
        if path.startswith(CDC):
            return {"cdc-pipeline"} if "To" in filename else {"cdc"}
        if path.startswith(E2E):
            return {"recovery-e2e"} if "Recovery" in filename else {"backend-e2e"}
        if path.startswith(BUSINESS):
            return {"business"}
        return set()

    if path.startswith(JDBC):
        vendor = next((v for v in ("mysql", "postgres", "oracle") if v in path.lower().removeprefix(JDBC)), None)
        if vendor:
            return {"jdbc-" + vendor, "pipeline", "cdc-pipeline"}
        return {"jdbc-mysql", "jdbc-postgres", "jdbc-oracle", "pipeline", "cdc-pipeline"}
    if path.startswith(CDC):
        return {"cdc", "cdc-pipeline"} | ({"recovery-e2e"} if any(x in path.lower() for x in ("checkpoint", "split", "offset")) else set())
    if path.startswith(RUNTIME):
        return {"pipeline", "cdc-pipeline", "recovery-e2e", "backend-e2e"}
    if path.startswith("yak-flow/yak-flow-connector-base/"):
        return {"pipeline", "cdc", "cdc-pipeline"}
    if path.startswith("yak-ops-core/"):
        return {"pipeline", "cdc-pipeline", "backend-e2e"}
    if path.startswith("yak-ops-business/yak-ops-business-task/"):
        return {"business", "backend-e2e"}
    if path.startswith("yak-ops-business/yak-ops-business-schedule/"):
        return {"business", "backend-e2e"}
    if path.startswith("yak-ops-business/"):
        return {"business"}
    if path.startswith("yak-ops-task-execution/"):
        return {"backend-e2e", "recovery-e2e"}
    if path.startswith(E2E):
        return {"backend-e2e", "recovery-e2e"}
    if path.startswith(("yak-ops-boot/", "yak-ops-plugins/")):
        return {"backend-e2e"}
    if path.startswith("yak-ops-dao/"):
        return {"business", "backend-e2e"}
    if path.startswith("yak-ops-platform/"):
        return {"business", "backend-e2e"}
    if path.startswith(("yak-ops-common/", "yak-ops-configuration/", "yak-ops-spi/", "yak-ops-bom/")):
        # Compile/unit-check shared contracts, but do not run unrelated databases.
        return set()
    return set()


def analyze(paths: list[str], *, full: bool = False) -> dict:
    result = dict(backend=False, frontend=False, distribution=False, release=False,
                  modules=set(), suites=set(), full=False)

    def all_checks() -> None:
        result.update(backend=True, frontend=True, distribution=True, release=True, full=True)
        result["suites"].update(SUITES)

    if full:
        all_checks()
    for path in paths:
        if path.startswith("docs/") or path.endswith(".md") or path in {".gitignore", ".asf.yaml", "LICENSE"}:
            continue
        if path.startswith("yak-ops-ui/"):
            result["frontend"] = True
            if path in {"yak-ops-ui/pom.xml", "yak-ops-ui/package.json", "yak-ops-ui/package-lock.json"}:
                result["distribution"] = True
            continue
        if path.startswith((".github/workflows/playwright-", ".github/ISSUE_TEMPLATE/")):
            continue
        if path.startswith("scripts/ci/") or path.startswith("scripts/tests/test_") or path == "scripts/tests/__init__.py":
            # Scope unit tests are always run in the cheap select job.
            continue
        if path.startswith("scripts/tests/") or path == ".github/workflows/backend-tests.yml":
            result["suites"].update(SUITES)
            continue
        if path in {".github/workflows/quality-check.yml"}:
            all_checks()
            continue
        if path.startswith(".github/workflows/") or path.startswith("scripts/release/") or path == "release.env":
            result["release"] = True
            continue
        if path in {"Dockerfile", "compose.yaml", "compose.without-mysql.yaml", "NOTICE", ".env.example"} or path.startswith(("docker/", "yak-ops-dist/")):
            result["distribution"] = True
            continue
        if path == "pom.xml" or path in {"mvnw", "mvnw.cmd"} or path.startswith((".mvn/", "tools/maven/")):
            all_checks()
            continue
        if path.startswith("yak-ops-dao/") and "/db/migration/" in path:
            result["release"] = True
        owners = [m for m in module_for(path) if m not in {"yak-ops-ui", "yak-ops-dist"}]
        if owners:
            result["backend"] = True
            result["modules"].update(affected_modules(owners))
            result["suites"].update(test_suites(path))
            continue
        # An unknown path might change a build or generated contract: fail closed.
        all_checks()
    return result


def select_suites(paths: list[str]) -> list[str]:
    result = analyze(paths)
    return [name for name in SUITES if name in result["suites"]]


def outputs(paths: list[str], *, full: bool = False) -> str:
    impact = analyze(paths, full=full)
    selected_modules = "!yak-ops-ui,!yak-ops-dist" if impact["full"] else ",".join(sorted(impact["modules"]))
    fields = {
        "backend": str(impact["backend"]).lower(),
        "frontend": str(impact["frontend"]).lower(),
        "distribution": str(impact["distribution"]).lower(),
        "release": str(impact["release"]).lower(),
        "tests": str(bool(impact["suites"])).lower(),
        "modules": selected_modules,
        "suites": ",".join(name for name in SUITES if name in impact["suites"]),
    }
    return "\n".join(f"{key}={value}" for key, value in fields.items()) + "\n"


if __name__ == "__main__":
    # Paths are collected using git -z, so spaces and non-ASCII file names are safe.
    print(outputs(sys.argv[1:]), end="")
