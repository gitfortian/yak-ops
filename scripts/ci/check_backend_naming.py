#!/usr/bin/env python3
"""Check Java declaration names and package ownership without banning valid domain terms."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NETWORK_IO = "yak-flow/yak-flow-runtime/src/main/java/io/yak/ops/flow/runtime/io/"
OBSOLETE_DATA_SYNC_PACKAGES = (
    "yak-ops-common/src/main/java/io/yak/ops/common/bean/dto/datasync",
    "yak-ops-common/src/main/java/io/yak/ops/common/bean/vo/datasync",
    "yak-ops-common/src/main/java/io/yak/ops/common/enums/datasync",
)

# Mask comments and literal contents while preserving newlines for actionable errors.
JAVA_NON_CODE = re.compile(
    r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'
    r"'(?:\\.|[^'\\])*'",
    re.S,
)
PACKAGE = re.compile(r"\bpackage\s+([\w.]+)\s*;")
TYPE = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)")
# Only method declarations, not calls, imports, constructors or method references.
METHOD = re.compile(
    r"(?m)^[ \t]*(?!return\b|throw\b|new\b|if\b|for\b|while\b|switch\b|yield\b)"
    r"(?:(?:public|private|protected|static|final|abstract|default|synchronized|native)\s+)*"
    r"(?:<[^;{}()]*>\s+)?"
    r"[\w.$<>?,\[\] ]+\s+(?P<name>[A-Za-z_$][\w$]*)\s*\("
)
CODEC = re.compile(r"Codec|(?<![A-Za-z])codec")


def code_only(source: str) -> str:
    return JAVA_NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)


def line_number(source: str, offset: int) -> int:
    return source.count("\n", 0, offset) + 1


def verify_source(path: str, source: str) -> list[tuple[int, str]]:
    """Return naming violations from a Yak Ops production Java file."""
    if "/src/main/java/" not in path or not path.endswith(".java"):
        return []
    code = code_only(source)
    package = PACKAGE.search(code)
    if not package or not package.group(1).startswith("io.yak.ops."):
        return []

    violations = []
    for segment in package.group(1).split("."):
        if CODEC.search(segment):
            violations.append((
                line_number(code, package.start()),
                f"Package segment '{segment}' uses Codec; name the concrete responsibility",
            ))

    for match in TYPE.finditer(code):
        name = match.group(1)
        if CODEC.search(name):
            violations.append((
                line_number(code, match.start(1)),
                f"Type '{name}' uses Codec; prefer Parser, Serializer or a specific converter",
            ))
        if name.endswith("Gate") and not (
            path.startswith(NETWORK_IO) and package.group(1).startswith("io.yak.ops.flow.runtime.io.")
        ):
            violations.append((
                line_number(code, match.start(1)),
                f"Type '{name}' uses Gate outside Runtime network I/O; name its actual owner",
            ))

    # Limit declaration matching to symbol-bearing lines; scanning all Java lines
    # with the method grammar is both slower and prone to accidental call matches.
    for line, text in enumerate(code.splitlines(), start=1):
        if not CODEC.search(text):
            continue
        match = METHOD.match(text)
        if match and CODEC.search(match.group("name")):
            violations.append((
                line,
                f"Method '{match.group('name')}' uses Codec; describe the operation performed",
            ))
    return violations


def production_java_files(root: Path = ROOT) -> list[Path]:
    return sorted(
        path for path in root.rglob("*.java")
        if "/src/main/java/" in path.as_posix() and "yak-ops-ui" not in path.parts
    )


def verify_repository(root: Path = ROOT) -> tuple[int, list[str]]:
    files = production_java_files(root)
    errors = []
    for folder in OBSOLETE_DATA_SYNC_PACKAGES:
        if (root / folder).exists():
            errors.append(f"Obsolete Data Sync contract package must not return: {folder}")
    for path in files:
        relative = path.relative_to(root).as_posix()
        for line, reason in verify_source(relative, path.read_text(encoding="utf-8")):
            errors.append(f"{relative}:{line}: {reason}")
    return len(files), errors


def main() -> None:
    count, errors = verify_repository()
    if errors:
        print("Backend naming and ownership check failed:")
        for error in errors:
            print(f"- {error}")
        raise SystemExit(1)
    print(f"Backend naming and ownership check passed: {count} production Java files.")


if __name__ == "__main__":
    main()
