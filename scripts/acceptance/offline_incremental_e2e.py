"""End-to-end bounded offline cursors against disposable MySQL and PostgreSQL."""
import json
import time
import uuid


def verify_offline_incremental(api, client, source, target):
    mysql, postgres = api["query_mysql"], api["query_postgres"]
    submit, wait = api["submit"], api["wait_status"]
    suffix = uuid.uuid4().hex[:7]

    def create(mapping, steps, solution="SINGLE_TABLE_OFFLINE"):
        params = {
            "solutionType": solution,
            "steps": steps,
            "source": {"datasourceId": source},
            "sink": {"datasourceId": target},
            "tableSelection": {"mode": "EXPLICIT"},
            "tableMappings": mapping,
        }
        definition = client.call("POST", "/api/v1/task-definitions", {
            "name": "offline-incremental-" + uuid.uuid4().hex[:8],
            "taskType": "DATA_SYNC",
            "debug": True,
            "parameters": json.dumps(params),
        })["id"]
        client.call("POST", f"/api/v1/task-definitions/{definition}/publish")
        return definition

    def route(origin, destination, mode="TIMESTAMP", initial=None):
        increment = {"mode": mode, "column": "updated_at" if mode == "TIMESTAMP" else "id",
                     "delaySeconds": 0, "lookbackSeconds": 0}
        if initial is not None:
            increment["initialValue"] = initial
        return {"source": {"table": origin}, "target": {"schema": "public", "table": destination},
                "writeMode": "UPSERT", "incremental": increment}

    def state(definition):
        sql = ("SELECT CONCAT(initialized,':',revision,':',"
               "IFNULL(cursor_value,'NULL'),':',IFNULL(pending_value,'NULL')) "
               "FROM yak_ops.yak_ops_task_cursor WHERE definition_id='" + definition + "' ORDER BY table_key")
        return mysql(sql).splitlines()

    def run(definition, expected="FINISHED", phase=None):
        admitted, _ = submit(client, definition)
        instance = admitted["instanceId"]
        wait(client, instance, {expected})
        stored = client.call("GET", f"/api/v1/task-instances/{instance}")
        if phase is not None and expected == "FINISHED":
            report = json.loads(stored["executionReport"])
            actual = [table.get("step") for table in report["tables"]]
            assert actual == [phase] * len(actual), (phase, actual, report)
        return instance

    # Single table: first full initialization, next bounded incremental and an empty batch.
    origin, destination = "offline_orders_" + suffix, "offline_orders_copy_" + suffix
    mysql(f"CREATE TABLE app.{origin} (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)")
    mysql(f"INSERT INTO app.{origin} VALUES (1,'initial','2026-10-01 01:00:00.000000'),"
          "(2,'other','2026-10-01 01:00:00.000000')")
    postgres(f"CREATE TABLE public.{destination} (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)")
    definition = create([route(origin, destination)], ["FULL", "INCREMENTAL"])
    first = run(definition, phase="FULL")
    first_state = state(definition)
    assert len(first_state) == 1 and first_state[0].startswith("1:1:"), first_state
    assert postgres(f"SELECT COUNT(*) FROM public.{destination}") == "2"
    mysql(f"UPDATE app.{origin} SET name='updated', updated_at=UTC_TIMESTAMP(6) WHERE id=1")
    mysql(f"INSERT INTO app.{origin} VALUES (3,'new',UTC_TIMESTAMP(6))")
    time.sleep(0.15)  # Keep source timestamp values strictly before the batch upper bound.
    second = run(definition, phase="INCREMENTAL")
    assert postgres(f"SELECT string_agg(name,',' ORDER BY id) FROM public.{destination}") == "updated,other,new"
    assert state(definition)[0].startswith("1:2:"), state(definition)
    third = run(definition, phase="INCREMENTAL")
    assert state(definition)[0].startswith("1:3:"), state(definition)
    assert postgres(f"SELECT COUNT(*) FROM public.{destination}") == "3"

    # Explicit two-table batch: independent ranges but one logical progress commit.
    origin2, destination2 = "offline_clients_" + suffix, "offline_clients_copy_" + suffix
    mysql(f"CREATE TABLE app.{origin2} (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)")
    mysql(f"INSERT INTO app.{origin2} VALUES (10,'client','2026-10-01 01:00:00.000000')")
    postgres(f"CREATE TABLE public.{destination2} (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)")
    database = create([route(origin, destination), route(origin2, destination2)],
                      ["FULL", "INCREMENTAL"], "DATABASE_OFFLINE")
    run(database, phase="FULL")
    assert len(state(database)) == 2 and all(row.startswith("1:1:") for row in state(database))
    mysql(f"UPDATE app.{origin2} SET name='changed', updated_at=UTC_TIMESTAMP(6) WHERE id=10")
    time.sleep(0.15)
    run(database, phase="INCREMENTAL")
    assert len(state(database)) == 2 and all(row.startswith("1:2:") for row in state(database))
    assert postgres(f"SELECT name FROM public.{destination2} WHERE id=10") == "changed"

    # An acknowledged/partially successful target write must never advance the failed cursor.
    bad = "offline_reject_" + suffix
    postgres(f"CREATE TABLE public.{bad} (id BIGINT PRIMARY KEY CONSTRAINT reject_{suffix} CHECK (id < 0),"
             " name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)")
    failed = create([route(origin, bad, initial="2026-09-01T00:00:00")], ["INCREMENTAL"])
    failed_instance = run(failed, expected="FAILED")
    failed_state = state(failed)
    assert len(failed_state) == 1 and failed_state[0].startswith("0:0:NULL:"), failed_state
    assert postgres(f"SELECT COUNT(*) FROM public.{bad}") == "0"
    postgres(f"ALTER TABLE public.{bad} DROP CONSTRAINT reject_{suffix}")
    retried_instance = run(failed, phase="INCREMENTAL")
    assert state(failed)[0].startswith("1:1:"), state(failed)
    assert postgres(f"SELECT COUNT(*) FROM public.{bad}") == "3"
    assert len({first, second, third, failed_instance, retried_instance}) == 5
    print("OFFLINE INCREMENTAL PRODUCT PASS: initial load, timestamp cursor, multi-table,"
          " empty batch, failure and frozen retry", flush=True)
    return [(first, client.call("GET", f"/api/v1/task-instances/{first}")["executionReport"]),
            (second, client.call("GET", f"/api/v1/task-instances/{second}")["executionReport"])]
