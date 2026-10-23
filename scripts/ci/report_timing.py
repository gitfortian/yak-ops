#!/usr/bin/env python3
"""Summarize GitHub Actions job queue and execution time in the run summary."""
from __future__ import annotations

from datetime import datetime
import json
import os
import sys
from urllib.request import Request, urlopen


def seconds_between(start: str | None, end: str | None) -> int | None:
    if not start or not end:
        return None
    try:
        first = datetime.fromisoformat(start.replace("Z", "+00:00"))
        second = datetime.fromisoformat(end.replace("Z", "+00:00"))
        return max(0, round((second - first).total_seconds()))
    except ValueError:
        return None


def duration(seconds: int | None) -> str:
    if seconds is None:
        return "-"
    minutes, secs = divmod(seconds, 60)
    return f"{minutes}m {secs:02d}s" if minutes else f"{secs}s"


def format_report(jobs: list[dict]) -> str:
    completed = [job for job in jobs if job.get("started_at") and job.get("completed_at")]
    completed.sort(key=lambda job: (
        seconds_between(job.get("created_at"), job.get("started_at")) or 0,
        job.get("name", "")
    ), reverse=True)
    lines = ["### CI job timings", "",
             "Queue time is measured from each job's creation to its start.",
             "",
             "| Job | Queue | Execution | Result |",
             "| --- | ---: | ---: | --- |"]
    for job in completed:
        name = str(job.get("name", "?")).replace("|", "/").replace("\n", " ")
        queue = seconds_between(job.get("created_at"), job.get("started_at"))
        execution = seconds_between(job.get("started_at"), job.get("completed_at"))
        result = str(job.get("conclusion") or "unknown").replace("|", "/")
        lines.append(f"| {name} | {duration(queue)} | {duration(execution)} | {result} |")
    if not completed:
        lines.append("| No completed jobs | - | - | - |")
    return "\n".join(lines) + "\n"


def fetch_jobs(repository: str, run_id: str, token: str) -> list[dict]:
    jobs = []
    for page in range(1, 21):
        url = f"https://api.github.com/repos/{repository}/actions/runs/{run_id}/jobs?per_page=100&page={page}"
        request = Request(url, headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "yak-ops-ci-timings"
        })
        with urlopen(request, timeout=10) as response:
            payload = json.load(response)
        batch = payload.get("jobs", [])
        jobs.extend(batch)
        if len(batch) < 100:
            break
    return jobs


def main() -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        print("No GitHub Actions summary file; skipping timing report.")
        return
    try:
        token = os.environ["GITHUB_TOKEN"]
        jobs = fetch_jobs(os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_RUN_ID"], token)
        report = format_report(jobs)
    except (KeyError, OSError, ValueError, json.JSONDecodeError) as exc:
        report = "### CI job timings\n\nTiming data unavailable: " + type(exc).__name__ + "\n"
        print(report, file=sys.stderr)
    with open(path, "a", encoding="utf-8") as output:
        output.write(report)


if __name__ == "__main__":
    main()
