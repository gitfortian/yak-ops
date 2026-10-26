#!/usr/bin/env python3
"""Validate changed YOIP proposals without treating historical links as proposals."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILENAME = re.compile(r"^docs/yoips/YOIP-([1-9]\d*)-([a-z0-9]+(?:-[a-z0-9]+)*)\.md$")
HEADING = re.compile(r"^# YOIP-([1-9]\d*): (\S.*)$", re.MULTILINE)
META = ("Status", "Discussion", "Decision", "Implementation", "Release", "Related FLIPs")
SECTIONS = (
    "Motivation",
    "Public Interfaces",
    "Proposed Changes",
    "Compatibility, Deprecation, and Migration Plan",
    "Test Plan",
    "Rejected Alternatives",
)
# PR #1736 replaced these implementation-diary identifiers with historical link stubs.
RETIRED = frozenset(range(7, 20))
DISCUSSION_LINK = re.compile(r"https://github\.com/weifuwan/yak-ops/pull/\d+(?:\b|[/#])")
DECISION_LINK = re.compile(r"https://github\.com/weifuwan/yak-ops/pull/\d+#issuecomment-\d+")
HISTORICAL_PREFIX = "# Historical engineering note moved:"


def git(*args: str, root: Path = ROOT) -> bytes:
    return subprocess.check_output(["git", *args], cwd=root, stderr=subprocess.PIPE)


def content_at(ref: str, path: str, *, root: Path = ROOT) -> str:
    return git("show", f"{ref}:{path}", root=root).decode("utf-8")


def files_at(ref: str, *, root: Path = ROOT) -> list[str]:
    result = git("ls-tree", "-r", "--name-only", "-z", ref, "--", "docs/yoips/", root=root)
    return [p.decode("utf-8") for p in result.split(b"\0") if p]


def changed_files(base: str, head: str, *, root: Path = ROOT) -> list[tuple[str, str]]:
    result = git("diff", "--name-status", "--no-renames", "-z", f"{base}...{head}", "--", "docs/yoips/", root=root)
    parts = [p.decode("utf-8") for p in result.split(b"\0") if p]
    if len(parts) % 2:
        raise ValueError("Unrecognized git diff --name-status output")
    return list(zip(parts[::2], parts[1::2]))


def fields(text: str) -> tuple[dict[str, str], list[str]]:
    values = {}
    errors = []
    for name in META:
        found = re.findall(rf"^{re.escape(name)}:[ \t]*(.*)$", text, re.MULTILINE)
        if len(found) != 1 or not found[0].strip():
            errors.append(f"{name} must appear exactly once with a nonempty value")
        elif found[0].strip():
            values[name] = found[0].strip()
    return values, errors


def validate_proposal(path: str, text: str, previous: str | None, occupied: dict[int, list[str]]) -> list[str]:
    errors = []
    filename = FILENAME.fullmatch(path)
    if filename is None:
        return ["expected docs/yoips/YOIP-N-short-title.md with a lowercase kebab-case title"]
    number = int(filename.group(1))
    heading = HEADING.match(text)
    if not heading or int(heading.group(1)) != number:
        errors.append(f"first line must be '# YOIP-{number}: Title'")
    if previous is None and number in RETIRED:
        errors.append(f"YOIP-{number} is a retired historical identifier; choose an unused number")
    conflicting = [p for p in occupied.get(number, []) if p != path]
    if previous is None and conflicting:
        errors.append(f"YOIP-{number} is already assigned to {', '.join(conflicting)}")

    values, meta_errors = fields(text)
    errors.extend(meta_errors)
    sections = re.findall(r"^## (.+?)[ \t]*$", text, re.MULTILINE)
    for section in SECTIONS:
        if sections.count(section) != 1:
            errors.append(f"expected exactly one '## {section}' section")
    for section in SECTIONS:
        if sections.count(section) == 1:
            match = re.search(rf"^## {re.escape(section)}[ \t]*$", text, re.MULTILINE)
            rest = text[match.end():]
            next_heading = re.search(r"^## ", rest, re.MULTILINE)
            body = rest[:next_heading.start()] if next_heading else rest
            if not body.strip() or body.lstrip().startswith((
                "What problem is being solved", "Which public APIs", "Explain the proposed design",
                "Who is affected", "Describe how observable behavior", "Identify meaningful alternatives",
            )):
                errors.append(f"'## {section}' needs proposal-specific content, not template guidance")
    positions = [sections.index(s) for s in SECTIONS if sections.count(s) == 1]
    if len(positions) == len(SECTIONS) and positions != sorted(positions):
        errors.append("the six template sections must appear in the template order")

    status = values.get("Status")
    if status not in ("Discussion", "Accepted"):
        errors.append("Status must be Discussion or Accepted (not a PR stage)")
    old_values = fields(previous)[0] if previous else {}
    old_status = old_values.get("Status")
    if old_status == "Accepted" and status != "Accepted":
        errors.append("an accepted proposal cannot silently revert to Discussion")
    if previous is None or (old_status != status and status == "Accepted"):
        if not DISCUSSION_LINK.search(values.get("Discussion", "")):
            errors.append("Discussion must link the proposal pull request")
    if status == "Discussion" and (previous is None or old_status != status):
        if not values.get("Decision", "").startswith("Pending"):
            errors.append("a Discussion proposal must have a Pending decision")
    if status == "Accepted" and (previous is None or old_status != "Accepted"):
        decision = values.get("Decision", "")
        decision_link = DECISION_LINK.search(decision)
        proposal_link = DISCUSSION_LINK.search(values.get("Discussion", ""))
        if "[VOTE] Accepted" not in decision or not decision_link:
            errors.append("acceptance requires '[VOTE] Accepted' and a permalink to the maintainer decision")
        elif proposal_link and proposal_link.group().rstrip("/#") != decision_link.group().split("#")[0]:
            errors.append("acceptance decision must belong to the proposal discussion PR")
    return errors


def side_branch_changes(base: str, head: str, *, root: Path = ROOT) -> list[set[str]]:
    """Proposal paths each merged side branch does not hold identically with the head.

    A sync PR merges a long-lived branch, so every record that arrives byte-identical
    with a merged side was authored there; only the rest belongs to this PR.
    """
    log = git("rev-list", "--first-parent", "--merges", "--parents", f"{base}..{head}", root=root)
    sides = [parent for line in log.decode().splitlines() for parent in line.split()[2:]]
    changes = []
    for side in sides:
        diff = git("diff", "--name-only", "-z", side, head, "--", "docs/yoips/", root=root)
        changes.append({name.decode("utf-8") for name in diff.split(b"\0") if name})
    return changes


def verify(base: str, head: str, *, root: Path = ROOT) -> tuple[int, list[str]]:
    changed = changed_files(base, head, root=root)
    paths = files_at(head, root=root)
    index = content_at(head, "docs/yoips/README.md", root=root) if any(
        action == "A" and path.startswith("docs/yoips/YOIP-") for action, path in changed
    ) else ""
    occupied: dict[int, list[str]] = {}
    for path in paths:
        match = FILENAME.fullmatch(path)
        if match:
            occupied.setdefault(int(match.group(1)), []).append(path)

    errors = []
    inspected = 0
    side_changes = side_branch_changes(base, head, root=root)
    for action, path in changed:
        if not path.startswith("docs/yoips/") or path == "docs/yoips/README.md":
            continue
        if not path.startswith("docs/yoips/YOIP-"):
            continue
        if action == "D":
            renamed = path.replace("docs/yoips/YOIP-", "docs/yoips/FYOIP-", 1)
            if any(a == "A" and p == renamed for a, p in changed):
                continue  # Renamed into the fork FYOIP namespace within this PR.
            errors.append(f"{path}: do not delete proposal records or historical link stubs")
            continue
        if action not in ("A", "M", "T"):
            errors.append(f"{path}: unsupported changed-file status {action}")
            continue
        previous = None if action == "A" else content_at(base, path, root=root)
        current = content_at(head, path, root=root)
        if any(path not in changes for changes in side_changes):
            continue  # A merged side branch already owns this exact record.
        if current.startswith(HISTORICAL_PREFIX):
            if previous is not None and previous.startswith(HISTORICAL_PREFIX):
                continue  # Existing PR #1736 link stubs may be maintained.
            errors.append(f"{path}: cannot create a new historical-link stub to bypass proposal validation")
            continue
        inspected += 1
        if action == "A" and path.rsplit("/", 1)[-1] not in index:
            errors.append(f"{path}: register new proposals in docs/yoips/README.md")
        errors.extend(f"{path}: {e}" for e in validate_proposal(path, current, previous, occupied))
    return inspected, errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="PR base commit SHA")
    parser.add_argument("--head", required=True, help="PR head commit SHA")
    args = parser.parse_args()
    try:
        checked, errors = verify(args.base, args.head)
    except (OSError, ValueError, subprocess.CalledProcessError, UnicodeError) as exc:
        print(f"YOIP validation could not inspect the PR diff: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"::error::{error}", file=sys.stderr)
        print(f"YOIP validation failed: {len(errors)} issue(s).", file=sys.stderr)
        return 1
    print(f"YOIP validation passed: {checked} changed proposal(s) checked.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
