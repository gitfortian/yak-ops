"""Initial schema preparation through public APIs, real databases and the existing Task lifecycle."""
import json
import os
import uuid


def parameters(source, target, routes, realtime=False):
    scope = "DATABASE" if len(routes) > 1 else "SINGLE_TABLE"
    return {"solutionType": scope + ("_REALTIME" if realtime else "_OFFLINE"),
            "steps": ["SCHEMA", "FULL"] + (["INCREMENTAL"] if realtime else []),
            "source": {"datasourceId": source}, "sink": {"datasourceId": target},
            "tableSelection": {"mode": "EXPLICIT"}, "tableMappings": routes}


def create(client, params, publish=True):
    task = client.call("POST", "/api/v1/task-definitions", {
        "name": "schema-" + uuid.uuid4().hex[:10], "taskType": "DATA_SYNC", "debug": True,
        "parameters": json.dumps(params)})["id"]
    if publish:
        client.call("POST", f"/api/v1/task-definitions/{task}/publish")
    return task


def preview(client, params, status=200):
    return client.call("POST", "/api/v1/data-sync/schema/preview", {"parameters": json.dumps(params)}, status=status)


def verify_schema_migration(api, client, mysql_source, postgres):
    mysql, pg = api["query_mysql"], api["query_postgres"]
    submit, until, claims = api["submit"], api["until"], api["claim_count"]

    def wait(client, identity, allowed, timeout=150):
        api["wait_status"](client, identity, allowed, timeout=timeout)
        return client.call("GET", f"/api/v1/task-instances/{identity}")
    mysql("CREATE DATABASE sm_target")
    mysql_target = api["create_datasource"](client, "schema-mysql-" + uuid.uuid4().hex[:8], "MYSQL", "sm_target", "root", 3306)
    evidence = []

    def run(params, expected="FINISHED"):
        task = create(client, params)
        accepted, _ = submit(client, task)
        identity = accepted["instanceId"]
        instance = wait(client, identity, {expected})
        assert claims(identity) == 0
        report = json.loads(instance["executionReport"])
        assert report["version"] == 3
        assert [stage["step"] for stage in report["stages"]] == params["steps"]
        assert report["stages"][0]["completedTables"] == len(params["tableMappings"])
        assert report["stages"][0]["progressPercent"] == 100
        assert all(table["stages"][0]["status"] == "FINISHED" for table in report["tables"])
        assert instance["runtimeEngine"] == "YAKFLOW" and instance["runtimeId"]
        assert instance["runtimeId"] != identity
        assert api["DB_PASSWORD"] not in instance["executionReport"] and "CREATE TABLE" not in instance["executionReport"]
        evidence.append((identity, instance["executionReport"]))
        return task, instance, report

    # Same-vendor and cross-vendor bounded paths share the same expected schema and real readback.
    for source_kind, source, source_query, source_namespace, source_quote in (
            ("mysql", mysql_source, mysql, "app", "`"), ("pg", postgres, pg, "public", '"')):
        for target_kind, target, query, namespace, quote in (
                ("mysql", mysql_target, mysql, "sm_target", "`"), ("pg", postgres, pg, "public", '"')):
            source_name = f"sm_{source_kind}_{target_kind}"
            target_name = source_name + ".copy"
            source_path = f"{source_namespace}.{source_quote}{source_name}{source_quote}"
            target_path = f"{namespace}.{quote}{target_name}{quote}"
            if source_kind == "mysql":
                source_query(f"CREATE TABLE {source_path} (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NULL COMMENT 'Name''s note') COMMENT='Orders'' comment'; INSERT INTO {source_path} VALUES (1,'one'),(2,NULL)")
            else:
                source_query(f"CREATE TABLE {source_path} (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32)); COMMENT ON TABLE {source_path} IS 'Orders'' comment'; COMMENT ON COLUMN {source_path}.name IS 'Name''s note'; INSERT INTO {source_path} VALUES (1,'one'),(2,NULL)")
            route = {"source": {"table": source_name, **({"schema": "public"} if source_kind == "pg" else {})},
                     "target": {"table": target_name, **({"schema": "public"} if target_kind == "pg" else {})}, "writeMode": "UPSERT"}
            params = parameters(source, target, [route])
            before_tasks = mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_definition")
            before_claims = mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim")
            plans = preview(client, params)
            assert len(plans) == 1 and plans[0]["action"] == "CREATE"
            assert target_name in "\n".join(plans[0]["statements"])
            assert mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_definition") == before_tasks
            assert mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim") == before_claims
            absent = (f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{namespace}' AND table_name='{target_name}'")
            assert query(absent) == "0", "Preview must not create a table"
            task, instance, report = run(params)
            assert report["tables"][0]["stages"][0]["action"] == "PREPARED"
            assert query(f"SELECT COUNT(*) FROM {target_path}") == "2"
            assert query(f"SELECT COUNT(*) FROM {target_path} WHERE id=2 AND name IS NULL") == "1"
            if target_kind == "pg":
                assert pg(f"SELECT obj_description('{target_path}'::regclass)") == "Orders' comment"
                assert pg(f"SELECT col_description('{target_path}'::regclass,2)") == "Name's note"
            else:
                assert mysql(f"SELECT table_comment FROM information_schema.tables WHERE table_schema='{namespace}' AND table_name='{target_name}'") == "Orders' comment"
            query(f"INSERT INTO {target_path} VALUES (999,'keep-existing')")
            assert preview(client, params)[0]["action"] == "VERIFY"
            second, _ = submit(client, task)
            repeated = wait(client, second["instanceId"], {"FINISHED"})
            repeated_report = json.loads(repeated["executionReport"])
            assert repeated_report["tables"][0]["stages"][0]["action"] == "VERIFIED"
            assert query(f"SELECT name FROM {target_path} WHERE id=999") == "keep-existing"
            assert query(f"SELECT COUNT(*) FROM {target_path}") == "3"
            assert claims(second["instanceId"]) == 0
            evidence.append((second["instanceId"], repeated["executionReport"]))
            print(f"SCHEMA MATRIX PASS: {source_kind} -> {target_kind}, absent/dotted target, comments, real rows and preserved existing data", flush=True)

    # Invalid definitions and cross-Workspace requests cannot execute any DDL.
    mysql("CREATE TABLE app.sm_default (id BIGINT PRIMARY KEY, name VARCHAR(32) DEFAULT 'generated')")
    bad = parameters(mysql_source, postgres, [{"source": {"table": "sm_default"}, "target": {"schema": "public", "table": "sm_default_target"}, "writeMode": "UPSERT"}])
    preview(client, bad, status=400)
    invalid = create(client, bad, publish=False)
    client.call("POST", f"/api/v1/task-definitions/{invalid}/publish", status=400)
    assert pg("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='sm_default_target'") == "0"
    mysql("CREATE TABLE app.sm_plain (id BIGINT PRIMARY KEY, name VARCHAR(32)); INSERT INTO app.sm_plain VALUES (1,'plain')")
    bad["tableMappings"][0]["source"]["table"] = "sm_plain"
    pg("CREATE TABLE public.sm_default_target (id BIGINT PRIMARY KEY)")
    preview(client, bad, status=400)
    assert pg("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='sm_default_target'") == "1"
    workspace = client.workspace
    client.workspace = client.call("POST", "/api/v1/workspaces", {"name": "schema-isolation-" + uuid.uuid4().hex[:8]})["id"]
    try:
        preview(client, bad, status=400)
    finally:
        client.workspace = workspace
    print("SCHEMA VALIDATION PASS: defaults, incomplete targets and Workspace isolation", flush=True)

    # A real server-confirmed DDL failure leaves previous successful tables intact and reports every mapping.
    routes = []
    for suffix in "abc":
        mysql(f"CREATE TABLE app.sm_partial_{suffix} (id BIGINT PRIMARY KEY, name VARCHAR(32)); INSERT INTO app.sm_partial_{suffix} VALUES (1,'{suffix}')")
        routes.append({"source": {"table": f"sm_partial_{suffix}"}, "target": {"schema": "public", "table": f"sm_partial_{suffix}"}, "writeMode": "UPSERT"})
    pg("CREATE FUNCTION public.sm_fail_create() RETURNS event_trigger LANGUAGE plpgsql AS $$ BEGIN IF current_query() LIKE '%sm_partial_b%' THEN RAISE EXCEPTION 'injected DDL rejection' USING ERRCODE='42501'; END IF; END $$; CREATE EVENT TRIGGER sm_fail_create ON ddl_command_start WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION public.sm_fail_create()")
    try:
        task = create(client, parameters(mysql_source, postgres, routes))
        accepted, _ = submit(client, task)
        identity = accepted["instanceId"]
        failed = wait(client, identity, {"FAILED"})
        assert claims(identity) == 0 and not failed.get("runtimeId")
        result = json.loads(failed["executionReport"])
        assert [row["stages"][0]["status"] for row in result["tables"]] == ["FINISHED", "FAILED", "CANCELED"]
        assert result["stages"][0]["completedTables"] == 1 and result["stages"][0]["progressPercent"] == 33
        assert pg("SELECT COUNT(*) FROM public.sm_partial_a") == "0"
        assert pg("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('sm_partial_b','sm_partial_c')") == "0"
        evidence.append((identity, failed["executionReport"]))
    finally:
        pg("DROP EVENT TRIGGER sm_fail_create; DROP FUNCTION public.sm_fail_create()")
    accepted, _ = submit(client, task)
    retried = wait(client, accepted["instanceId"], {"FINISHED"})
    result = json.loads(retried["executionReport"])
    assert [row["stages"][0]["action"] for row in result["tables"]] == ["VERIFIED", "PREPARED", "PREPARED"]
    assert pg("SELECT (SELECT COUNT(*) FROM public.sm_partial_a)+(SELECT COUNT(*) FROM public.sm_partial_b)+(SELECT COUNT(*) FROM public.sm_partial_c)") == "3"
    assert claims(accepted["instanceId"]) == 0
    evidence.append((accepted["instanceId"], retried["executionReport"]))
    print("SCHEMA PARTIAL FAILURE PASS: one prepared, one failed, one canceled; next Instance verifies and completes remaining targets", flush=True)

    # Cancellation runs against a real sleeping CREATE TABLE, not an artificial completed Future.
    pg("CREATE FUNCTION public.sm_delay_create() RETURNS event_trigger LANGUAGE plpgsql AS $$ BEGIN IF current_query() LIKE '%sm_cancel_target%' THEN PERFORM pg_sleep(20); END IF; END $$; CREATE EVENT TRIGGER sm_delay_create ON ddl_command_start WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION public.sm_delay_create()")
    try:
        routes = [{"source": {"table": "sm_plain"}, "target": {"schema": "public", "table": "sm_cancel_target"}, "writeMode": "UPSERT"}]
        task = create(client, parameters(mysql_source, postgres, routes))
        accepted, _ = submit(client, task)
        identity = accepted["instanceId"]
        until("CREATE TABLE entered server-side wait", lambda: pg("SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event='PgSleep' AND query LIKE '%sm_cancel_target%'"), lambda value: int(value) > 0, timeout=15)
        assert claims(identity) == 1
        before = client.call("GET", f"/api/v1/task-instances/{identity}")
        assert before["status"] == "RUNNING" and not before.get("runtimeId")
        assert json.loads(before["executionReport"])["stages"][0]["status"] == "RUNNING"
        client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
        stopped = wait(client, identity, {"CANCELED"}, timeout=35)
        assert claims(identity) == 0 and not stopped.get("runtimeId")
        assert pg("SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event='PgSleep' AND query LIKE '%sm_cancel_target%'") == "0"
        assert pg("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='sm_cancel_target'") == "0"
        result = json.loads(stopped["executionReport"])
        assert [stage["status"] for stage in result["stages"]] == ["CANCELED", "CANCELED"]
        evidence.append((identity, stopped["executionReport"]))
    finally:
        pg("DROP EVENT TRIGGER sm_delay_create; DROP FUNCTION public.sm_delay_create()")
    print("SCHEMA CANCELLATION PASS: active database DDL canceled and observed before claim release; no YakFlow submission", flush=True)

    for kind, target, query, namespace in (("mysql", mysql_target, mysql, "sm_target"), ("pg", postgres, pg, "public")):
        routes = []
        for suffix in ("a", "empty"):
            source_name = f"sm_rt_{kind}_{suffix}"
            mysql(f"CREATE TABLE app.{source_name} (id BIGINT PRIMARY KEY, name VARCHAR(32))")
            if suffix == "a":
                mysql(f"INSERT INTO app.{source_name} VALUES (1,'one'),(2,'two'),(3,'three')")
            routes.append({"source": {"table": source_name}, "target": {"table": source_name + "_copy", **({"schema": "public"} if kind == "pg" else {})}, "writeMode": "UPSERT"})
        params = parameters(mysql_source, target, routes, realtime=True)
        task = create(client, params)
        accepted, _ = submit(client, task)
        identity = accepted["instanceId"]

        def live():
            instance = client.call("GET", f"/api/v1/task-instances/{identity}")
            assert instance["status"] not in ("FAILED", "CANCELED", "RECONCILING"), instance
            return json.loads(instance["executionReport"]) if instance.get("executionReport") else None

        try:
            streaming = until("Schema prepared and CDC handoff committed", live, lambda value: value and value["phase"] == "STREAMING", timeout=160)
            assert streaming["version"] == 3 and len(streaming["stages"]) == 3
            assert all(stage["status"] == "FINISHED" and stage["progressPercent"] == 100 for stage in streaming["stages"][:2])
            assert streaming["stages"][2]["progressPercent"] is None
            assert streaming["checkpoints"]["handoffId"] is not None
            assert claims(identity) == 3
            job_id = client.call("GET", f"/api/v1/task-instances/{identity}")["runtimeId"]
            assert job_id and job_id != identity
            a = routes[0]["source"]["table"]
            empty = routes[1]["source"]["table"]
            mysql(f"UPDATE app.{a} SET name='changed' WHERE id=1; DELETE FROM app.{a} WHERE id=2; UPDATE app.{a} SET id=30 WHERE id=3; INSERT INTO app.{a} VALUES (4,'four'); INSERT INTO app.{empty} VALUES (1,'born')")
            until("Created target receives ordered CDC changes", lambda: query(f"SELECT COUNT(*) FROM {namespace}.{a}_copy WHERE (id=1 AND name='changed') OR id IN (30,4)"), lambda value: value == "3")
            assert query(f"SELECT COUNT(*) FROM {namespace}.{a}_copy WHERE id IN (2,3)") == "0"
            until("Created empty target receives Binlog", lambda: query(f"SELECT name FROM {namespace}.{empty}_copy WHERE id=1"), lambda value: value == "born")
            summary = client.call("POST", "/api/v1/task-instances/page", {"definitionId": task, "pageNo": 1, "pageSize": 20})["bizData"][0]
            compact = json.loads(summary["executionReport"])
            assert compact["version"] == 3 and "tables" not in compact
            if kind == "pg":
                checkpoint = live()["checkpoints"]["completedId"]
                until("Checkpoint after schema handoff", live, lambda value: value and value["checkpoints"]["completedId"] > checkpoint, timeout=35)
                pg("CREATE SEQUENCE public.sm_retry_once; CREATE FUNCTION public.sm_retry_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.name='schema-retry' AND nextval('public.sm_retry_once')=1 THEN RAISE EXCEPTION 'injected row failure'; END IF; RETURN NEW; END $$")
                pg(f"CREATE TRIGGER sm_retry_write BEFORE INSERT ON public.{a}_copy FOR EACH ROW EXECUTE FUNCTION public.sm_retry_write()")
                try:
                    mysql(f"INSERT INTO app.{a} VALUES (888,'schema-retry')")
                    recovered = until("Schema report survives same-Job recovery", live, lambda value: value and value["generation"] == 2 and value["phase"] == "STREAMING", timeout=130)
                    assert recovered["restored"] and recovered["stages"][0]["status"] == "FINISHED"
                    assert recovered["stages"][0]["completedTables"] == 2
                    assert recovered["stages"][1]["readRows"] is None
                    assert client.call("GET", f"/api/v1/task-instances/{identity}")["runtimeId"] == job_id
                    until("Recovered rows committed", lambda: pg(f"SELECT name FROM public.{a}_copy WHERE id=888"), lambda value: value == "schema-retry")
                finally:
                    pg(f"DROP TRIGGER sm_retry_write ON public.{a}_copy; DROP FUNCTION public.sm_retry_write(); DROP SEQUENCE public.sm_retry_once")
            pinned = client.call("GET", f"/api/v1/task-instances/{identity}/definition")
        finally:
            current = client.call("GET", f"/api/v1/task-instances/{identity}")
            if current["status"] in ("SUBMITTED", "LAUNCHING", "RUNNING", "CANCELLING"):
                client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
                wait(client, identity, {"CANCELED"}, timeout=45)
        terminal = client.call("GET", f"/api/v1/task-instances/{identity}")
        assert claims(identity) == 0
        result = json.loads(terminal["executionReport"])
        assert [stage["status"] for stage in result["stages"]] == ["FINISHED", "FINISHED", "CANCELED"]
        evidence.append((identity, terminal["executionReport"]))
        client.call("POST", f"/api/v1/task-definitions/{task}/unpublish")
        changed = parameters(mysql_source, target, list(reversed(routes)), realtime=True)
        client.call("PUT", f"/api/v1/task-definitions/{task}", {"name": pinned["name"], "parameters": json.dumps(changed)})
        assert client.call("GET", f"/api/v1/task-instances/{identity}/definition") == pinned
        assert client.call("GET", f"/api/v1/task-instances/{identity}")["executionReport"] == terminal["executionReport"]
        print("SCHEMA REALTIME PASS: MySQL -> " + kind + "; create two targets, one Job, handoff, ordered CDC, empty table, cancellation and pinned history", flush=True)

    return evidence
