package io.yak.ops.boot.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Real DDL planning, cross-vendor schema, injected DDL failure and CDC handoff. */
class SchemaMigrationIT extends BackendE2ETest {
    @Test
    void schemaMigrationPreservesDatabaseAndLifecycleContracts() {
        env.mysql("CREATE DATABASE sm_target");
        String mysqlTarget = tasks.datasource("MYSQL", "sm_target", "root", env.mysqlHost(), env.mysqlPort());
        boundedMatrix(mysqlTarget);
        rejectedSchemaAndWorkspace();
        partialDdlFailure();
        cancelServerSideDdl();
        schemaAndRealtimeHandoff(mysqlTarget);
    }

    private Map<String, Object> options(String source, String target, List<Map<String, Object>> mappings,
            boolean realtime) {
        String scope = mappings.size() > 1 ? "DATABASE" : "SINGLE_TABLE";
        return tasks.parameters(scope + (realtime ? "_REALTIME" : "_OFFLINE"),
                realtime ? List.of("SCHEMA", "FULL", "INCREMENTAL") : List.of("SCHEMA", "FULL"),
                mappings, source, target);
    }

    private Map<String, Object> route(String source, String dest, boolean pgSource, boolean pgTarget) {
        Map<String, String> from = new LinkedHashMap<>();
        from.put("table", source);
        if (pgSource) from.put("schema", "public");
        Map<String, String> to = new LinkedHashMap<>();
        to.put("table", dest);
        if (pgTarget) to.put("schema", "public");
        return Map.of("source", from, "target", to, "writeMode", "UPSERT");
    }

    private String sql(String kind, String query) {
        return "mysql".equals(kind) ? env.mysql(query) : env.pg(query);
    }

    private JsonNode preview(Map<String, Object> options, int expectedStatus) {
        return api.call("POST", "/api/v1/data-sync/schema/preview",
                Map.of("parameters", TaskFixtures.json(options)), expectedStatus);
    }

    private String create(Map<String, Object> options, boolean publish) {
        return tasks.create("schema", options, publish, true);
    }

    private JsonNode completed(String instance, Map<String, Object> options) {
        JsonNode record = tasks.await(instance, Set.of("FINISHED"));
        assertEquals("0", tasks.claimCount(instance));
        JsonNode report = tasks.report(instance);
        assertEquals(3, report.path("version").asInt());
        assertEquals(options.get("steps"), TaskFixtures.texts(report.path("stages"), "step"));
        assertEquals(((List<?>) options.get("tableMappings")).size(),
                report.path("stages").get(0).path("completedTables").asInt());
        assertEquals(100, report.path("stages").get(0).path("progressPercent").asInt());
        for (JsonNode table : report.path("tables")) {
            assertEquals("FINISHED", table.path("stages").get(0).path("status").asText());
        }
        assertEquals("YAKFLOW", record.path("runtimeEngine").asText());
        assertNotEquals(instance, record.path("runtimeId").asText());
        assertFalse(record.path("runtimeId").asText().isBlank());
        String serialized = record.path("executionReport").asText();
        assertFalse(serialized.contains(BackendE2EEnvironment.PASSWORD));
        assertFalse(serialized.contains("CREATE TABLE"));
        return report;
    }

    private void boundedMatrix(String mysqlTarget) {
        for (String sourceKind : List.of("mysql", "pg")) {
            for (String targetKind : List.of("mysql", "pg")) {
                String name = "sm_" + sourceKind + "_" + targetKind;
                String newName = name + ".copy";
                String srcQuote = "mysql".equals(sourceKind) ? String.valueOf((char) 96) : "\"";
                String dstQuote = "mysql".equals(targetKind) ? String.valueOf((char) 96) : "\"";
                String origin = ("mysql".equals(sourceKind) ? "app." : "public.")
                        + srcQuote + name + srcQuote;
                String destination = ("mysql".equals(targetKind) ? "sm_target." : "public.")
                        + dstQuote + newName + dstQuote;
                if ("mysql".equals(sourceKind)) {
                    env.mysql("CREATE TABLE " + origin
                            + " (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NULL COMMENT 'Name''s note') "
                            + "COMMENT='Orders'' comment'");
                } else {
                    env.pg("CREATE TABLE " + origin + " (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32))");
                    env.pg("COMMENT ON TABLE " + origin + " IS 'Orders'' comment'");
                    env.pg("COMMENT ON COLUMN " + origin + ".name IS 'Name''s note'");
                }
                sql(sourceKind, "INSERT INTO " + origin + " VALUES (1,'one'),(2,NULL)");
                String sourceId = "mysql".equals(sourceKind) ? tasks.source : tasks.target;
                String targetId = "mysql".equals(targetKind) ? mysqlTarget : tasks.target;
                Map<String, Object> params = options(sourceId, targetId,
                        List.of(route(name, newName, "pg".equals(sourceKind), "pg".equals(targetKind))), false);
                String definitions = env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_definition");
                String claims = env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim");
                JsonNode plans = preview(params, 200);
                assertEquals(1, plans.size());
                assertEquals("CREATE", plans.get(0).path("action").asText());
                assertTrue(plans.get(0).path("statements").toString().contains(newName));
                assertEquals(definitions, env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_definition"));
                assertEquals(claims, env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim"));
                assertEquals("0", sql(targetKind,
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                                + ("mysql".equals(targetKind) ? "sm_target" : "public")
                                + "' AND table_name='" + newName + "'"));
                String task = create(params, true);
                String first = tasks.run(task, "FINISHED");
                JsonNode report = completed(first, params);
                assertEquals("PREPARED", report.path("tables").get(0).path("stages").get(0)
                        .path("action").asText());
                assertEquals("2", sql(targetKind, "SELECT COUNT(*) FROM " + destination));
                assertEquals("1", sql(targetKind,
                        "SELECT COUNT(*) FROM " + destination + " WHERE id=2 AND name IS NULL"));
                if ("pg".equals(targetKind)) {
                    assertEquals("Orders' comment",
                            env.pg("SELECT obj_description('" + destination + "'::regclass)"));
                    assertEquals("Name's note",
                            env.pg("SELECT col_description('" + destination + "'::regclass,2)"));
                } else {
                    assertEquals("Orders' comment", env.mysql(
                            "SELECT table_comment FROM information_schema.tables WHERE "
                                    + "table_schema='sm_target' AND table_name='" + newName + "'"));
                }
                sql(targetKind, "INSERT INTO " + destination + " VALUES (999,'keep-existing')");
                assertEquals("VERIFY", preview(params, 200).get(0).path("action").asText());
                String second = tasks.run(task, "FINISHED");
                assertEquals("VERIFIED", tasks.report(second).path("tables").get(0)
                        .path("stages").get(0).path("action").asText());
                assertEquals("keep-existing", sql(targetKind,
                        "SELECT name FROM " + destination + " WHERE id=999"));
                assertEquals("3", sql(targetKind, "SELECT COUNT(*) FROM " + destination));
                assertEquals("0", tasks.claimCount(second));
            }
        }
    }

    private void rejectedSchemaAndWorkspace() {
        env.mysql("CREATE TABLE app.sm_default "
                + "(id BIGINT PRIMARY KEY, name VARCHAR(32) DEFAULT 'generated')");
        Map<String, Object> invalid = options(tasks.source, tasks.target,
                List.of(route("sm_default", "sm_default_target", false, true)), false);
        preview(invalid, 400);
        String task = create(invalid, false);
        api.call("POST", "/api/v1/task-definitions/" + task + "/publish", null, 400);
        assertEquals("0", env.pg("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema='public' AND table_name='sm_default_target'"));

        env.mysql("CREATE TABLE app.sm_plain (id BIGINT PRIMARY KEY, name VARCHAR(32))");
        env.mysql("INSERT INTO app.sm_plain VALUES (1,'plain')");
        Map<String, Object> mismatch = options(tasks.source, tasks.target,
                List.of(route("sm_plain", "sm_default_target", false, true)), false);
        env.pg("CREATE TABLE public.sm_default_target (id BIGINT PRIMARY KEY)");
        preview(mismatch, 400);
        assertEquals("1", env.pg("SELECT COUNT(*) FROM information_schema.columns WHERE "
                + "table_schema='public' AND table_name='sm_default_target'"));
        String previous = api.workspace();
        api.workspace(api.call("POST", "/api/v1/workspaces",
                Map.of("name", "schema-other-" + TaskFixtures.unique())).path("id").asText());
        try {
            preview(mismatch, 400);
        } finally {
            api.workspace(previous);
        }
    }

    private void partialDdlFailure() {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (String suffix : List.of("a", "b", "c")) {
            String name = "sm_partial_" + suffix;
            env.mysql("CREATE TABLE app." + name + " (id BIGINT PRIMARY KEY, name VARCHAR(32))");
            env.mysql("INSERT INTO app." + name + " VALUES (1,'" + suffix + "')");
            routes.add(route(name, name, false, true));
        }
        env.pg("CREATE FUNCTION public.sm_fail_create() RETURNS event_trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN IF current_query() LIKE '%sm_partial_b%' THEN "
                + "RAISE EXCEPTION 'injected DDL rejection' USING ERRCODE='42501'; END IF; END $$");
        env.pg("CREATE EVENT TRIGGER sm_fail_create ON ddl_command_start "
                + "WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION public.sm_fail_create()");
        String task = create(options(tasks.source, tasks.target, routes, false), true);
        try {
            String failed = tasks.run(task, "FAILED");
            JsonNode record = api.call("GET", "/api/v1/task-instances/" + failed);
            assertEquals("0", tasks.claimCount(failed));
            assertTrue(record.path("runtimeId").isNull() || record.path("runtimeId").isMissingNode());
            JsonNode report = tasks.report(failed);
            assertEquals(List.of("FINISHED", "FAILED", "CANCELED"), perTableSchema(report, "status"));
            assertEquals(1, report.path("stages").get(0).path("completedTables").asInt());
            assertEquals(33, report.path("stages").get(0).path("progressPercent").asInt());
            assertEquals("0", env.pg("SELECT COUNT(*) FROM public.sm_partial_a"));
            assertEquals("0", env.pg("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema='public' AND table_name IN ('sm_partial_b','sm_partial_c')"));
        } finally {
            env.pg("DROP EVENT TRIGGER sm_fail_create");
            env.pg("DROP FUNCTION public.sm_fail_create()");
        }
        String second = tasks.run(task, "FINISHED");
        assertEquals(List.of("VERIFIED", "PREPARED", "PREPARED"), perTableSchema(tasks.report(second), "action"));
        assertEquals("3", env.pg("SELECT (SELECT COUNT(*) FROM public.sm_partial_a)"
                + "+(SELECT COUNT(*) FROM public.sm_partial_b)"
                + "+(SELECT COUNT(*) FROM public.sm_partial_c)"));
        assertEquals("0", tasks.claimCount(second));
    }

    private static List<String> perTableSchema(JsonNode report, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode table : report.path("tables")) {
            result.add(table.path("stages").get(0).path(field).asText());
        }
        return result;
    }

    private void cancelServerSideDdl() {
        env.pg("CREATE FUNCTION public.sm_delay_create() RETURNS event_trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN IF current_query() LIKE '%sm_cancel_target%' THEN PERFORM pg_sleep(20); END IF; END $$");
        env.pg("CREATE EVENT TRIGGER sm_delay_create ON ddl_command_start "
                + "WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION public.sm_delay_create()");
        try {
            String task = create(options(tasks.source, tasks.target,
                    List.of(route("sm_plain", "sm_cancel_target", false, true)), false), true);
            String instance = api.submit(task).path("instanceId").asText();
            BackendE2EEnvironment.until("server-side sleeping DDL", Duration.ofSeconds(20),
                    () -> env.pg("SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event='PgSleep' "
                            + "AND query LIKE '%sm_cancel_target%'"), count -> Integer.parseInt(count) > 0);
            assertEquals("1", tasks.claimCount(instance));
            JsonNode running = api.call("GET", "/api/v1/task-instances/" + instance);
            assertEquals("RUNNING", running.path("status").asText());
            assertTrue(running.path("runtimeId").isNull() || running.path("runtimeId").isMissingNode());
            assertEquals("RUNNING", tasks.report(instance).path("stages").get(0).path("status").asText());
            api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
            tasks.await(instance, Set.of("CANCELED"));
            assertEquals("0", tasks.claimCount(instance));
            assertEquals("0", env.pg("SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event='PgSleep' "
                    + "AND query LIKE '%sm_cancel_target%'"));
            assertEquals("0", env.pg("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema='public' AND table_name='sm_cancel_target'"));
            assertEquals(List.of("CANCELED", "CANCELED"),
                    TaskFixtures.texts(tasks.report(instance).path("stages"), "status"));
        } finally {
            env.pg("DROP EVENT TRIGGER sm_delay_create");
            env.pg("DROP FUNCTION public.sm_delay_create()");
        }
    }

    private void schemaAndRealtimeHandoff(String mysqlTarget) {
        for (String kind : List.of("mysql", "pg")) {
            String target = "mysql".equals(kind) ? mysqlTarget : tasks.target;
            String namespace = "mysql".equals(kind) ? "sm_target" : "public";
            List<Map<String, Object>> routes = new ArrayList<>();
            for (String suffix : List.of("a", "empty")) {
                String table = "sm_rt_" + kind + "_" + suffix;
                env.mysql("CREATE TABLE app." + table + " (id BIGINT PRIMARY KEY, name VARCHAR(32))");
                if ("a".equals(suffix)) env.mysql("INSERT INTO app." + table
                        + " VALUES (1,'one'),(2,'two'),(3,'three')");
                routes.add(route(table, table + "_copy", false, "pg".equals(kind)));
            }
            Map<String, Object> params = options(tasks.source, target, routes, true);
            String task = create(params, true);
            String instance = api.submit(task).path("instanceId").asText();
            JsonNode pinned;
            try {
                JsonNode streaming = BackendE2EEnvironment.until("schema + CDC handoff",
                        Duration.ofSeconds(170), () -> live(instance),
                        current -> current != null && "STREAMING".equals(current.path("phase").asText()));
                assertEquals(3, streaming.path("version").asInt());
                assertEquals(3, streaming.path("stages").size());
                for (int index = 0; index < 2; index++) {
                    assertEquals("FINISHED", streaming.path("stages").get(index).path("status").asText());
                    assertEquals(100, streaming.path("stages").get(index).path("progressPercent").asInt());
                }
                assertTrue(streaming.path("stages").get(2).path("progressPercent").isNull());
                assertFalse(streaming.path("checkpoints").path("handoffId").isNull());
                assertEquals("3", tasks.claimCount(instance));
                String job = api.call("GET", "/api/v1/task-instances/" + instance).path("runtimeId").asText();
                assertFalse(job.isBlank());
                assertNotEquals(instance, job);
                String a = "sm_rt_" + kind + "_a", empty = "sm_rt_" + kind + "_empty";
                env.mysql("UPDATE app." + a + " SET name='changed' WHERE id=1");
                env.mysql("DELETE FROM app." + a + " WHERE id=2");
                env.mysql("UPDATE app." + a + " SET id=30 WHERE id=3");
                env.mysql("INSERT INTO app." + a + " VALUES (4,'four')");
                env.mysql("INSERT INTO app." + empty + " VALUES (1,'born')");
                BackendE2EEnvironment.until("schema target CDC rows", Duration.ofSeconds(100),
                        () -> sql(kind, "SELECT COUNT(*) FROM " + namespace + "." + a
                                + "_copy WHERE (id=1 AND name='changed') OR id IN (30,4)"), "3"::equals);
                assertEquals("0", sql(kind, "SELECT COUNT(*) FROM " + namespace + "." + a
                        + "_copy WHERE id IN (2,3)"));
                BackendE2EEnvironment.until("schema empty-table CDC", Duration.ofSeconds(95),
                        () -> sql(kind, "SELECT name FROM " + namespace + "." + empty + "_copy WHERE id=1"),
                        "born"::equals);
                JsonNode list = api.call("POST", "/api/v1/task-instances/page",
                        Map.of("definitionId", task, "pageNo", 1, "pageSize", 20));
                JsonNode compact = TaskFixtures.parse(list.path("bizData").get(0)
                        .path("executionReport").asText());
                assertEquals(3, compact.path("version").asInt());
                assertFalse(compact.has("tables"));
                if ("pg".equals(kind)) recoveredSchemaJob(instance, job, a);
                pinned = api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
            } finally {
                JsonNode current = api.call("GET", "/api/v1/task-instances/" + instance);
                if (Set.of("SUBMITTED", "LAUNCHING", "RUNNING", "CANCELLING")
                        .contains(current.path("status").asText())) {
                    api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
                    tasks.await(instance, Set.of("CANCELED"));
                }
            }
            assertEquals("0", tasks.claimCount(instance));
            JsonNode terminal = tasks.report(instance);
            assertEquals(List.of("FINISHED", "FINISHED", "CANCELED"),
                    TaskFixtures.texts(terminal.path("stages"), "status"));
            String exact = api.call("GET", "/api/v1/task-instances/" + instance)
                    .path("executionReport").asText();
            api.call("POST", "/api/v1/task-definitions/" + task + "/unpublish");
            List<Map<String, Object>> changed = new ArrayList<>(routes);
            Collections.reverse(changed);
            api.call("PUT", "/api/v1/task-definitions/" + task,
                    Map.of("name", pinned.path("name").asText(),
                            "parameters", TaskFixtures.json(options(tasks.source, target, changed, true))));
            assertEquals(pinned, api.call("GET", "/api/v1/task-instances/" + instance + "/definition"));
            assertEquals(exact, api.call("GET", "/api/v1/task-instances/" + instance)
                    .path("executionReport").asText());
        }
    }

    private JsonNode live(String instance) {
        JsonNode row = api.call("GET", "/api/v1/task-instances/" + instance);
        assertFalse(Set.of("FAILED", "CANCELED", "RECONCILING").contains(row.path("status").asText()));
        String report = row.path("executionReport").asText(null);
        return report == null ? null : TaskFixtures.parse(report);
    }

    private void recoveredSchemaJob(String instance, String job, String source) {
        long checkpoint = live(instance).path("checkpoints").path("completedId").asLong();
        BackendE2EEnvironment.until("schema completed checkpoint", Duration.ofSeconds(40),
                () -> live(instance).path("checkpoints").path("completedId").asLong(), now -> now > checkpoint);
        env.pg("CREATE SEQUENCE public.sm_retry_once");
        env.pg("CREATE FUNCTION public.sm_retry_write() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN IF NEW.name='schema-retry' AND nextval('public.sm_retry_once')=1 "
                + "THEN RAISE EXCEPTION 'injected row failure'; END IF; RETURN NEW; END $$");
        env.pg("CREATE TRIGGER sm_retry_write BEFORE INSERT ON public." + source
                + "_copy FOR EACH ROW EXECUTE FUNCTION public.sm_retry_write()");
        try {
            env.mysql("INSERT INTO app." + source + " VALUES (888,'schema-retry')");
            JsonNode recovered = BackendE2EEnvironment.until("schema CDC same-Job recovery",
                    Duration.ofSeconds(145), () -> live(instance),
                    row -> row != null && row.path("generation").asInt() == 2
                            && "STREAMING".equals(row.path("phase").asText()));
            assertTrue(recovered.path("restored").asBoolean());
            assertEquals("FINISHED", recovered.path("stages").get(0).path("status").asText());
            assertEquals(2, recovered.path("stages").get(0).path("completedTables").asInt());
            assertTrue(recovered.path("stages").get(1).path("readRows").isNull());
            assertEquals(job, api.call("GET", "/api/v1/task-instances/" + instance)
                    .path("runtimeId").asText());
            BackendE2EEnvironment.until("schema CDC restored target write", Duration.ofSeconds(100),
                    () -> env.pg("SELECT name FROM public." + source + "_copy WHERE id=888"),
                    "schema-retry"::equals);
        } finally {
            env.pg("DROP TRIGGER sm_retry_write ON public." + source + "_copy");
            env.pg("DROP FUNCTION public.sm_retry_write()");
            env.pg("DROP SEQUENCE public.sm_retry_once");
        }
    }
}
