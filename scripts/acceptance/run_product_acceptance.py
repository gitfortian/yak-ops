#!/usr/bin/env python3
"""Run a backend E2E suite against disposable databases."""

import argparse
from contextlib import contextmanager
import importlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import time
import uuid

SUITES = ("full", "batch", "schedule", "realtime", "schema")


def load_harness():
    spec = importlib.util.spec_from_file_location(
        "task_execution_e2e", Path(__file__).with_name("task-execution-e2e.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@contextmanager
def phase(name, timings):
    started = time.monotonic()
    result = "FAILED"
    print("START " + name, flush=True)
    try:
        yield
        result = "PASSED"
    finally:
        elapsed = round(time.monotonic() - started, 3)
        timings.append({"phase": name, "result": result, "seconds": elapsed})
        print(f"{result} {name} ({elapsed}s)", flush=True)


def bootstrap(harness):
    harness.STATE_ROOT.mkdir(parents=True, exist_ok=True)
    # Fail on dirty fixtures instead of clearing an existing database implicitly.
    harness.query_mysql("CREATE DATABASE IF NOT EXISTS app; "
                        "CREATE TABLE app.orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL); "
                        "INSERT INTO app.orders VALUES (1,'one'),(2,'two')")
    harness.query_postgres("CREATE TABLE public.ods_orders "
                           "(id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    harness.boot()
    client = harness.Client()
    client.login()
    client.workspace = client.call("POST", "/api/v1/workspaces", {
        "name": "focused-" + uuid.uuid4().hex[:10]})["id"]
    source = harness.create_datasource(client, "mysql-" + uuid.uuid4().hex[:8], "MYSQL", "app", "root", 3306)
    target = harness.create_datasource(client, "postgres-" + uuid.uuid4().hex[:8], "POSTGRE_SQL", "ods", "yak", 5432)
    return client, source, target


@contextmanager
def occupied_target(harness, client, source, target):
    definition = harness.create_task(client, "busy-" + uuid.uuid4().hex[:8], source, target, True)
    accepted, _ = harness.submit(client, definition)
    identity = accepted["instanceId"]
    try:
        harness.wait_status(client, identity, {"RUNNING"})
        assert harness.claim_count(identity) == 2
        yield
    finally:
        client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
        harness.wait_status(client, identity, {"CANCELED"}, timeout=45)
        assert harness.claim_count(identity) == 0


def run_suite(suite, harness, client, source, target):
    api = vars(harness)
    if suite == "batch":
        module = importlib.import_module("multitable_batch")
        evidence = module.verify_multitable_batch(api, client, source, target)
        with occupied_target(harness, client, source, target):
            module.verify_multitable_conflict(api, client, source, target)
        return [evidence]
    if suite == "schedule":
        definition = harness.create_task(client, "schedule-" + uuid.uuid4().hex[:8], source, target, False)
        harness.verify_schedule_configuration(client, definition)
        harness.verify_parameter_versions(client, source, target, definition)
        harness.verify_periodic_jdbc(client, source, target)
        with occupied_target(harness, client, source, target):
            harness.verify_busy_schedule(client, source, target)
        return []
    if suite == "realtime":
        return importlib.import_module("database_realtime").verify_database_realtime(api, client, source, target)
    if suite == "schema":
        return importlib.import_module("schema_migration_e2e").verify_schema_migration(api, client, source, target)
    raise ValueError("Unknown focused acceptance suite: " + suite)


def report_failures(client):
    """Emit only allowlisted state, never credentials, parameters, SQL or raw error messages."""
    try:
        page = client.call("POST", "/api/v1/task-instances/page", {"pageNo": 1, "pageSize": 100})
        for instance in page["bizData"]:
            if instance["status"] in {"FAILED", "RECONCILING"}:
                print("FAILURE STATE " + json.dumps({key: instance.get(key) for key in
                      ("id", "status", "failureCode", "runtimeEngine", "runtimeId")}), flush=True)
    except Exception as error:
        print("Failure-state lookup unavailable: " + type(error).__name__, flush=True)


def execute(suite, harness, timings):
    if suite == "full":
        # Preserve the complete original sequence and every cross-scenario/restart assertion.
        with phase("full", timings):
            harness.acceptance()
        return
    client = None
    try:
        with phase("bootstrap", timings):
            client, source, target = bootstrap(harness)
        with phase(suite, timings):
            evidence = run_suite(suite, harness, client, source, target)
        if evidence:
            with phase("restart-report-readback", timings):
                workspace = client.workspace
                harness.shutdown(harness.APP)
                harness.boot()
                client = harness.Client()
                client.login()
                client.workspace = workspace
                for identity, expected in evidence:
                    actual = client.call("GET", f"/api/v1/task-instances/{identity}")
                    assert actual["executionReport"] == expected, identity
    except Exception:
        if client is not None:
            report_failures(client)
        raise
    finally:
        if harness.APP is not None:
            harness.shutdown(harness.APP)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=SUITES, default="full")
    args = parser.parse_args()
    timings = []
    started = time.monotonic()
    result = "FAILED"
    try:
        execute(args.suite, load_harness(), timings)
        result = "PASSED"
    finally:
        summary = {"suite": args.suite, "result": result,
                   "seconds": round(time.monotonic() - started, 3),
                   "testedSha": os.environ.get("GITHUB_SHA"), "phases": timings}
        output = Path("/tmp/yak-acceptance-results")
        output.mkdir(parents=True, exist_ok=True)
        (output / (args.suite + ".json")).write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(json.dumps(summary), flush=True)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as target:
                target.write("## Product suite timing\n\n```json\n" + json.dumps(summary, indent=2) + "\n```\n")


if __name__ == "__main__":
    main()
