package io.yak.ops.boot.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Real MySQL CDC snapshot, multi-table handoff, same-Job recovery and cancellation. */
class DatabaseRealtimeIT extends BackendE2ETest {
    @Test
    void fullToIncrementalAndRecoveryAcrossMysqlAndPostgres() {
        env.mysql("CREATE DATABASE rt_target");
        String mysqlTarget = tasks.datasource("MYSQL", "rt_target", "root", env.mysqlHost(), env.mysqlPort());
        JsonNode unsupported = api.call("GET", "/api/v1/data-sync/solutions?sourceType=MYSQL&targetType=ORACLE");
        assertFalse(TaskFixtures.texts(unsupported, "solutionType").contains("DATABASE_REALTIME"));

        for (String kind : List.of("mysql", "pg")) {
            verifyTarget(kind, "mysql".equals(kind) ? mysqlTarget : tasks.target);
        }
        cancellationDuringSnapshot();
        invalidPrimaryKey();
    }

    private void verifyTarget(String kind, String targetId) {
        String namespace = "mysql".equals(kind) ? "rt_target" : "public";
        String prefix = "rt_" + kind;
        List<String> tables = List.of(prefix + "_a", prefix + "_b", prefix + "_empty");
        List<Map<String, Object>> mappings = new ArrayList<>();
        for (int index = 0; index < tables.size(); index++) {
            String table = tables.get(index);
            env.mysql("CREATE TABLE app." + table + " (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL)");
            if (index < 2) {
                StringBuilder sql = new StringBuilder("INSERT INTO app." + table + " VALUES ");
                for (int id = 1; id <= 200; id++) {
                    if (id > 1) sql.append(",");
                    sql.append("(").append(id).append(",'initial')");
                }
                env.mysql(sql.toString());
            }
            sql(kind, "CREATE TABLE " + namespace + "." + table
                    + "_copy (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL)");
            Map<String, String> target = "mysql".equals(kind)
                    ? Map.of("database", namespace, "table", table + "_copy")
                    : Map.of("schema", namespace, "table", table + "_copy");
            mappings.add(Map.of("source", Map.of("database", "app", "table", table),
                    "target", target, "writeMode", "UPSERT"));
        }
        Map<String, Object> options = tasks.parameters("DATABASE_REALTIME", List.of("FULL", "INCREMENTAL"),
                mappings, tasks.source, targetId);
        String task = tasks.create("realtime", options, true, false);
        JsonNode schedule = api.call("POST", "/api/v1/schedules", Map.of("targetType", "TASK", "targetId", task,
                "cronExpression", "0 * * * * ?", "timeZone", "UTC"));
        assertFalse(schedule.path("schedulable").asBoolean());
        assertEquals("NOT_SCHEDULABLE", schedule.path("eligibilityReason").asText());
        api.call("POST", "/api/v1/schedules/" + schedule.path("id").asText() + "/enable",
                Map.of("revision", 1), 400);

        String instance = tasks.run(task, "RUNNING");
        try {
            assertEquals("4", tasks.claimCount(instance));
            assertEquals("DATA_SYNC_TARGET,DATA_SYNC_TARGET,DATA_SYNC_TARGET,MYSQL_CDC_SERVER_ID",
                    env.mysql("SELECT GROUP_CONCAT(resource_type ORDER BY resource_type) "
                            + "FROM yak_ops.yak_ops_task_execution_claim WHERE instance_id='" + instance + "'"));
            JsonNode initial = BackendE2EEnvironment.until("CDC anchored snapshot", Duration.ofSeconds(40),
                    () -> liveReport(instance),
                    report -> report != null && Set.of("SNAPSHOT", "HANDOFF")
                            .contains(report.path("phase").asText()));
            assertEquals("RUNNING", initial.path("stages").get(0).path("status").asText());
            assertNotEquals(100, initial.path("stages").get(0).path("progressPercent").asInt());
            assertEquals("WAITING", initial.path("stages").get(1).path("status").asText());
            assertTrue(initial.path("stages").get(1).path("progressPercent").isNull());

            for (String table : tables.subList(0, 2)) {
                env.mysql("UPDATE app." + table + " SET name='updated' WHERE id=1");
                env.mysql("DELETE FROM app." + table + " WHERE id=2");
                env.mysql("UPDATE app." + table + " SET id=300, name='moved' WHERE id=3");
                env.mysql("INSERT INTO app." + table + " VALUES (400,'inserted')");
            }
            env.mysql("INSERT INTO app." + tables.get(2) + " VALUES (1,'from-binlog')");
            JsonNode streaming = BackendE2EEnvironment.until("CDC snapshot handoff", Duration.ofSeconds(150),
                    () -> liveReport(instance),
                    report -> report != null && "STREAMING".equals(report.path("phase").asText()));
            assertEquals(2, streaming.path("version").asInt());
            assertEquals(1, streaming.path("generation").asInt());
            assertEquals("FINISHED", streaming.path("stages").get(0).path("status").asText());
            assertEquals(100, streaming.path("stages").get(0).path("progressPercent").asInt());
            assertFalse(streaming.path("checkpoints").path("handoffId").isNull());
            assertEquals("RUNNING", streaming.path("stages").get(1).path("status").asText());
            assertTrue(streaming.path("stages").get(1).path("progressPercent").isNull());
            for (JsonNode table : streaming.path("tables")) {
                assertEquals("FINISHED", table.path("stages").get(0).path("status").asText());
            }

            for (String table : tables.subList(0, 2)) {
                BackendE2EEnvironment.until("CDC target converged " + table, Duration.ofSeconds(100), () ->
                        "200".equals(sql(kind, "SELECT COUNT(*) FROM " + namespace + "." + table + "_copy"))
                                && "3".equals(sql(kind, "SELECT COUNT(*) FROM " + namespace + "." + table
                                        + "_copy WHERE (id=1 AND name='updated') OR (id=300 AND name='moved') "
                                        + "OR (id=400 AND name='inserted')"))
                                && "0".equals(sql(kind, "SELECT COUNT(*) FROM " + namespace + "." + table
                                        + "_copy WHERE id IN (2,3)")), good -> good);
            }
            BackendE2EEnvironment.until("Empty table CDC", Duration.ofSeconds(90),
                    () -> sql(kind, "SELECT name FROM " + namespace + "." + tables.get(2) + "_copy WHERE id=1"),
                    "from-binlog"::equals);
            JsonNode page = api.call("POST", "/api/v1/task-instances/page", Map.of(
                    "definitionId", task, "pageNo", 1, "pageSize", 20));
            String compact = page.path("bizData").get(0).path("executionReport").asText();
            assertEquals(2, TaskFixtures.parse(compact).path("version").asInt());
            assertFalse(TaskFixtures.parse(compact).has("tables"));
            assertTrue(compact.length() < 16384);
            assertTrue(TaskFixtures.texts(api.call("POST", "/api/v1/task-definitions/page",
                    Map.of("taskType", "DATA_SYNC", "parameterFilters",
                            Map.of("solutionType", "DATABASE_REALTIME"), "pageNo", 1, "pageSize", 100))
                    .path("bizData"), "id").contains(task));

            String conflict = tasks.create("conflict", options, true, false);
            String countBefore = env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim");
            api.submit(conflict, UUID.randomUUID().toString(), 409);
            assertEquals(countBefore, env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim"));
            assertEquals(0, api.call("POST", "/api/v1/task-instances/page",
                    Map.of("definitionId", conflict, "pageNo", 1, "pageSize", 20))
                    .path("pagination").path("total").asInt());

            String observedAt = liveReport(instance).path("observedAt").asText();
            BackendE2EEnvironment.until("CDC idle observation", Duration.ofSeconds(20), () ->
                    liveReport(instance).path("observedAt").asText(), next -> !next.equals(observedAt));
            if ("pg".equals(kind)) injectRecoverableWriteFailure(instance, tables.get(0));

            JsonNode pinned = api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
            api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
            tasks.await(instance, Set.of("CANCELED"));
            assertEquals("0", tasks.claimCount(instance));
            String terminal = api.call("GET", "/api/v1/task-instances/" + instance)
                    .path("executionReport").asText();
            JsonNode finished = TaskFixtures.parse(terminal);
            assertEquals("FINISHED", finished.path("stages").get(0).path("status").asText());
            assertEquals("CANCELED", finished.path("stages").get(1).path("status").asText());
            assertFalse(terminal.contains(BackendE2EEnvironment.PASSWORD));
            api.call("POST", "/api/v1/task-definitions/" + task + "/unpublish");
            List<Map<String, Object>> changed = new ArrayList<>(mappings);
            Collections.reverse(changed);
            api.call("PUT", "/api/v1/task-definitions/" + task,
                    Map.of("name", pinned.path("name").asText(),
                            "parameters", TaskFixtures.json(tasks.parameters(
                                    "DATABASE_REALTIME", List.of("FULL", "INCREMENTAL"),
                                    changed, tasks.source, targetId))));
            assertEquals(pinned, api.call("GET", "/api/v1/task-instances/" + instance + "/definition"));
            assertEquals(terminal, api.call("GET", "/api/v1/task-instances/" + instance)
                    .path("executionReport").asText());
        } finally {
            JsonNode current = api.call("GET", "/api/v1/task-instances/" + instance);
            if (Set.of("SUBMITTED", "LAUNCHING", "RUNNING", "CANCELLING").contains(
                    current.path("status").asText())) {
                api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
                tasks.await(instance, Set.of("CANCELED"));
            }
        }
    }

    private JsonNode liveReport(String id) {
        JsonNode row = api.call("GET", "/api/v1/task-instances/" + id);
        assertFalse(Set.of("FAILED", "CANCELED", "RECONCILING").contains(row.path("status").asText()));
        assertTrue(row.path("progressPercent").isNull() || row.path("progressPercent").isMissingNode());
        String content = row.path("executionReport").asText(null);
        return content == null ? null : TaskFixtures.parse(content);
    }

    private String sql(String kind, String query) {
        return "mysql".equals(kind) ? env.mysql(query) : env.pg(query);
    }

    private void injectRecoverableWriteFailure(String instance, String table) {
        long checkpoint = liveReport(instance).path("checkpoints").path("completedId").asLong();
        BackendE2EEnvironment.until("CDC completed checkpoint", Duration.ofSeconds(45),
                () -> liveReport(instance).path("checkpoints").path("completedId").asLong(),
                value -> value > checkpoint);
        env.pg("CREATE SEQUENCE rt_pg_once START 1");
        env.pg("CREATE FUNCTION rt_pg_retry() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN IF NEW.name='retry' AND nextval('rt_pg_once')=1 "
                + "THEN RAISE EXCEPTION 'injected write failure'; END IF; RETURN NEW; END $$");
        env.pg("CREATE TRIGGER rt_pg_retry BEFORE INSERT ON public." + table
                + "_copy FOR EACH ROW EXECUTE FUNCTION rt_pg_retry()");
        try {
            env.mysql("INSERT INTO app." + table + " VALUES (7001,'retry')");
            JsonNode recovered = BackendE2EEnvironment.until("CDC same-Job recovery", Duration.ofSeconds(135),
                    () -> liveReport(instance),
                    report -> report != null && report.path("generation").asInt() == 2
                            && "STREAMING".equals(report.path("phase").asText()));
            assertTrue(recovered.path("restored").asBoolean());
            assertEquals("GENERATION", recovered.path("counterScope").asText());
            assertEquals(100, recovered.path("stages").get(0).path("progressPercent").asInt());
            assertTrue(recovered.path("stages").get(0).path("readRows").isNull());
            for (JsonNode observed : recovered.path("tables")) {
                assertTrue(observed.path("stages").get(0).path("readRows").isNull());
            }
            BackendE2EEnvironment.until("Recovered generation commits replay", Duration.ofSeconds(90),
                    () -> env.pg("SELECT name FROM public." + table + "_copy WHERE id=7001"), "retry"::equals);
        } finally {
            env.pg("DROP TRIGGER rt_pg_retry ON public." + table + "_copy");
            env.pg("DROP FUNCTION rt_pg_retry()");
            env.pg("DROP SEQUENCE rt_pg_once");
        }
    }

    private void cancellationDuringSnapshot() {
        env.pg("CREATE TABLE public.rt_cancel (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        env.pg("CREATE FUNCTION rt_cancel_delay() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN PERFORM pg_sleep(0.04); RETURN NEW; END $$");
        env.pg("CREATE TRIGGER rt_cancel_delay BEFORE INSERT ON public.rt_cancel "
                + "FOR EACH ROW EXECUTE FUNCTION rt_cancel_delay()");
        try {
            Map<String, Object> route = Map.of("source", Map.of("database", "app", "table", "rt_pg_a"),
                    "target", Map.of("schema", "public", "table", "rt_cancel"), "writeMode", "UPSERT");
            String task = tasks.create("cancel-snapshot", tasks.parameters(
                    "DATABASE_REALTIME", List.of("FULL", "INCREMENTAL"), List.of(route)), true, false);
            String id = api.submit(task).path("instanceId").asText();
            BackendE2EEnvironment.until("slow Snapshot reached", Duration.ofSeconds(80),
                    () -> liveReport(id),
                    report -> report != null && Set.of("SNAPSHOT", "HANDOFF")
                            .contains(report.path("phase").asText()));
            api.call("POST", "/api/v1/task-instances/" + id + "/cancel");
            tasks.await(id, Set.of("CANCELED"));
            assertEquals("0", tasks.claimCount(id));
            JsonNode report = tasks.report(id);
            assertEquals("CANCELED", report.path("stages").get(0).path("status").asText());
            assertNotEquals(100, report.path("stages").get(0).path("progressPercent").asInt());
            assertEquals("CANCELED", report.path("stages").get(1).path("status").asText());
            assertEquals("DATABASE_REALTIME", TaskFixtures.parse(api.call("GET",
                    "/api/v1/task-instances/" + id + "/definition")
                    .path("parameters").asText()).path("solutionType").asText());
        } finally {
            env.pg("DROP TRIGGER rt_cancel_delay ON public.rt_cancel");
            env.pg("DROP FUNCTION rt_cancel_delay()");
        }
    }

    private void invalidPrimaryKey() {
        env.mysql("CREATE TABLE app.rt_invalid_key (id VARCHAR(32) NOT NULL PRIMARY KEY, "
                + "name VARCHAR(32) NOT NULL)");
        String task = tasks.create("invalid-key", tasks.parameters("DATABASE_REALTIME",
                List.of("FULL", "INCREMENTAL"), List.of(
                        TaskFixtures.route("rt_invalid_key", "rt_cancel", "UPSERT"))), false, false);
        api.call("POST", "/api/v1/task-definitions/" + task + "/publish", null, 400);
        assertEquals(0, api.call("POST", "/api/v1/task-instances/page",
                Map.of("definitionId", task, "pageNo", 1, "pageSize", 20))
                .path("pagination").path("total").asInt());
    }
}
