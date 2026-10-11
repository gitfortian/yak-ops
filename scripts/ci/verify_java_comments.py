#!/usr/bin/env python3
"""Validate objective Java comment conventions across migrated repository scopes."""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODULES = (
    ROOT / "yak-ops-core",
    ROOT / "yak-flow" / "yak-flow-runtime",
    ROOT / "yak-flow" / "yak-flow-connector-base",
    ROOT / "yak-flow" / "yak-flow-connector-jdbc",
    ROOT / "yak-flow" / "yak-flow-connector-cdc-mysql",
    ROOT / "yak-ops-business" / "yak-ops-business-datasource",
    ROOT / "yak-ops-business" / "yak-ops-business-task",
    ROOT / "yak-ops-plugins" / "yak-ops-plugin-datasource" / "yak-ops-plugin-datasource-api",
    ROOT / "yak-ops-plugins" / "yak-ops-plugin-datasource" / "yak-ops-plugin-datasource-jdbc",
    ROOT / "yak-ops-plugins" / "yak-ops-plugin-task" / "yak-ops-plugin-task-api",
    ROOT / "yak-ops-plugins" / "yak-ops-plugin-task" / "yak-ops-plugin-task-data-sync",
)

# Shared modules have multiple owners. Scan only the Datasource/Task API and persistence
# packages; expanding to unrelated, not-yet-migrated Java packages is a separate change.
EXTRA_SOURCE_ROOTS = tuple(
    ROOT / module / "src" / "main" / "java" / "io" / "yak" / "ops" / owner / kind
    for module, owner, kinds in (
        ("yak-ops-common", "common/bean/dto", ("datasource", "datasync", "task")),
        ("yak-ops-common", "common/bean/vo", ("datasource", "datasync", "task")),
        ("yak-ops-common", "common/enums", ("datasource", "datasync", "task")),
        ("yak-ops-dao", "dao/entity", ("datasource", "task")),
        ("yak-ops-dao", "dao/mapper", ("datasource", "task")),
        ("yak-ops-dao", "dao/repository", ("datasource", "task")),
        ("yak-ops-boot", "boot/controller", ("datasource", "datasync", "task")),
    )
    for kind in kinds
)

CJK = re.compile(r"[\u3400-\u9fff]")
JAVA_TYPE = r"(?:class|interface|enum|record)"


@dataclass(frozen=True)
class Comment:
    start: int
    end: int
    text: str
    javadoc: bool


def scan_comments(source: str) -> tuple[list[Comment], str]:
    """Scan Java comments without mistaking string, char or text-block contents for comments."""
    masked = list(source)
    comments: list[Comment] = []
    length = len(source)
    index = 0

    def hide(start: int, end: int) -> None:
        for offset in range(start, end):
            if source[offset] not in "\n\r":
                masked[offset] = " "

    while index < length:
        start = index
        if source.startswith("//", index):
            end = source.find("\n", index + 2)
            if end == -1:
                end = length
            comments.append(Comment(start, end, source[start:end], False))
            hide(start, end)
            index = end
        elif source.startswith("/*", index):
            end = source.find("*/", index + 2)
            if end == -1:
                raise ValueError(f"Unterminated block comment on line {source.count(chr(10), 0, start) + 1}")
            end += 2
            comments.append(Comment(start, end, source[start:end], source.startswith("/**", start)))
            hide(start, end)
            index = end
        elif source.startswith('"""', index):
            index += 3
            while index < length:
                if source.startswith('"""', index):
                    index += 3
                    break
                if source[index] == "\\":
                    index += 2
                else:
                    index += 1
            hide(start, min(index, length))
        elif source[index] in "\"'":
            quote = source[index]
            index += 1
            while index < length:
                if source[index] == "\\":
                    index += 2
                elif source[index] == quote:
                    index += 1
                    break
                else:
                    index += 1
            hide(start, min(index, length))
        else:
            index += 1
    return comments, "".join(masked)


def find_missing_type_doc(source: str, comments: list[Comment], masked: str, filename: str) -> int | None:
    """Require Javadoc on a production top-level type named after its source file."""
    if filename == "package-info.java":
        return None
    name = re.escape(Path(filename).stem)
    declaration = re.search(rf"\b{JAVA_TYPE}\s+{name}\b", masked)
    if declaration is None:
        return None

    # Annotations and modifiers can appear between the Javadoc and the type keyword.
    # A semicolon or curly brace indicates an intervening Java declaration instead.
    preceding = [comment for comment in comments if comment.javadoc and comment.end <= declaration.start()]
    if preceding:
        latest = preceding[-1]
        between = source[latest.end:declaration.start()]
        if len(between) <= 1200 and not any(token in between for token in ";{}"):
            return None
    return source.count("\n", 0, declaration.start()) + 1


def check_source(source: str, filename: str, production: bool) -> list[tuple[int, str]]:
    comments, masked = scan_comments(source)
    problems: list[tuple[int, str]] = []

    for comment in comments:
        if CJK.search(comment.text):
            line = source.count("\n", 0, comment.start) + 1
            problems.append((line, "Chinese text in Java comment; use English Javadoc or // comment"))

    if production:
        line = find_missing_type_doc(source, comments, masked, filename)
        if line is not None:
            problems.append((line, "Top-level Java type is missing Javadoc"))
    return problems


def main() -> int:
    failures = 0
    checked = 0
    source_roots = [
        (module / "src" / category / "java", category == "main")
        for module in MODULES
        for category in ("main", "test")
    ]
    source_roots.extend((root, True) for root in EXTRA_SOURCE_ROOTS)
    for base, production in source_roots:
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.java")):
            checked += 1
            relative = path.relative_to(ROOT)
            try:
                issues = check_source(path.read_text(encoding="utf-8"), path.name, production)
            except (UnicodeError, ValueError) as error:
                issues = [(1, f"Unable to parse Java comments: {error}")]
            for line, reason in issues:
                print(f"{relative}:{line}: {reason}", file=sys.stderr)
                failures += 1
    if failures:
        print(f"Java comment check failed: {failures} issue(s) in {checked} Java files.", file=sys.stderr)
        return 1
    print(f"Java comment conventions passed: checked {checked} selected Java files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
