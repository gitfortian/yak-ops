package io.yak.ops.boot.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Runs real multi-table JDBC batches, partial failures, cancellation and atomic claims. */
class MultiTableBatchIT extends BackendE2ETest {
    @Test
    void batchesRetainTableReportsAndFailWithoutStealingUnrelatedClaims() {
        for (String name : List.of("batch_a", "batch_b", "batch_empty", "batch_slow")) {
            env.mysql("CREATE TABLE app." + name + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        }
        env.mysql("INSERT INTO app.batch_a VALUES (1,'a'),(2,'b')");
        env.mysql("INSERT INTO app.batch_b VALUES (11,'c'),(12,'d'),(13,'e')");
        StringBuilder rows = new StringBuilder("INSERT INTO app.batch_slow VALUES ");
        for (int i = 1; i <= 1000; i++) {
            if (i > 1) rows.append(",");
            rows.append("(").append(i).append(",'value')");
        }
        env.mysql(rows.toString());
        for (String name : List.of("batch_a_copy", "batch_b_copy", "batch_empty_copy", "batch_empty_second",
                "batch_slow_copy", "batch_good_copy", "aaa_orders")) {
            env.pg("CREATE TABLE public." + name + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        }
        env.pg("CREATE TABLE public.batch_bad_copy "
                + "(id BIGINT PRIMARY KEY CHECK (id < 0), name VARCHAR(32) NOT NULL)");

        List<Map<String, Object>> routes = List.of(
                TaskFixtures.route("batch_a", "batch_a_copy", "APPEND"),
                TaskFixtures.route("batch_b", "batch_b_copy", "UPSERT"),
                TaskFixtures.route("batch_empty", "batch_empty_copy", "UPSERT"));
        String task = create(routes, true);
        String instance = tasks.run(task, "FINISHED");
        JsonNode finished = api.call("GET", "/api/v1/task-instances/" + instance);
        assertFalse(finished.path("runtimeId").asText().isBlank());
        assertEquals(100, finished.path("progressPercent").asInt());
        JsonNode report = tasks.report(instance);
        assertEquals(1, report.path("version").asInt());
        assertFalse(report.path("observedAt").asText().isBlank());
        JsonNode tableReports = report.path("tables");
        assertEquals(3, tableReports.size());
        for (int index = 0; index < 3; index++) {
            assertEquals(index, tableReports.get(index).path("mappingIndex").asInt());
            assertEquals(List.of(2, 3, 0).get(index).intValue(), tableReports.get(index).path("readRows").asInt());
            assertEquals(List.of(2, 3, 0).get(index).intValue(), tableReports.get(index).path("committedRows").asInt());
            assertEquals("FINISHED", tableReports.get(index).path("status").asText());
            assertEquals(100, tableReports.get(index).path("progressPercent").asInt());
        }
        assertEquals("0", tasks.claimCount(instance));
        assertEquals("a,b", env.pg("SELECT string_agg(name,',' ORDER BY id) FROM public.batch_a_copy"));
        assertEquals("c,d,e", env.pg("SELECT string_agg(name,',' ORDER BY id) FROM public.batch_b_copy"));
        assertEquals("0", env.pg("SELECT COUNT(*) FROM public.batch_empty_copy"));
        assertFalse(finished.path("executionReport").asText().contains(BackendE2EEnvironment.PASSWORD));
        assertEquals(1, api.call("POST", "/api/v1/task-instances/page", Map.of(
                "definitionId", task, "pageNo", 1, "pageSize", 20)).path("pagination").path("total").asInt());
        assertTrue(TaskFixtures.texts(api.call("POST", "/api/v1/task-definitions/page", Map.of(
                "taskType", "DATA_SYNC", "parameterFilters", Map.of("solutionType", "DATABASE_OFFLINE"),
                "pageNo", 1, "pageSize", 100)).path("bizData"), "id").contains(task));

        JsonNode pinned = api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
        api.call("POST", "/api/v1/task-definitions/" + task + "/unpublish");
        String updated = TaskFixtures.json(tasks.parameters("DATABASE_OFFLINE", List.of("FULL"),
                List.of(TaskFixtures.route("batch_a", "batch_a_copy", "UPSERT"),
                        TaskFixtures.route("batch_b", "batch_b_copy", "UPSERT"),
                        TaskFixtures.route("batch_empty", "batch_empty_second", "UPSERT"))));
        JsonNode version = api.call("PUT", "/api/v1/task-definitions/" + task,
                Map.of("name", pinned.path("name").asText(), "parameters", updated));
        assertEquals(2, version.path("definitionVersion").asInt());
        assertEquals(pinned, api.call("GET", "/api/v1/task-instances/" + instance + "/definition"));
        assertEquals(finished.path("executionReport"), api.call("GET",
                "/api/v1/task-instances/" + instance).path("executionReport"));

        String workspace = api.workspace();
        api.workspace(api.call("POST", "/api/v1/workspaces",
                Map.of("name", "batch-other-" + TaskFixtures.unique())).path("id").asText());
        try {
            api.call("GET", "/api/v1/task-instances/" + instance, null, 404);
        } finally {
            api.workspace(workspace);
        }

        env.pg("CREATE TABLE public.batch_wrong_schema (id BIGINT PRIMARY KEY, name INTEGER NOT NULL)");
        String invalid = create(List.of(TaskFixtures.route("batch_a", "batch_a_copy", "UPSERT"),
                TaskFixtures.route("batch_b", "batch_wrong_schema", "UPSERT")), false);
        api.call("POST", "/api/v1/task-definitions/" + invalid + "/publish", null, 400);
        assertEquals(0, api.call("POST", "/api/v1/task-instances/page",
                Map.of("definitionId", invalid, "pageNo", 1, "pageSize", 20))
                .path("pagination").path("total").asInt());
        for (List<Map<String, Object>> bad : List.of(
                List.<Map<String, Object>>of(), List.of(routes.get(0), routes.get(0)),
                List.of(routes.get(0), TaskFixtures.route("batch_b", "batch_a_copy", "UPSERT")))) {
            api.call("POST", "/api/v1/task-definitions",
                    Map.of("name", "bad-batch-" + TaskFixtures.unique(), "taskType", "DATA_SYNC",
                            "parameters", TaskFixtures.json(tasks.parameters("DATABASE_OFFLINE", List.of("FULL"), bad))),
                    400);
        }

        String failing = create(List.of(TaskFixtures.route("batch_slow", "batch_good_copy", "UPSERT"),
                TaskFixtures.route("batch_b", "batch_bad_copy", "UPSERT")), true);
        String failure = tasks.run(failing, "FAILED");
        JsonNode failedReports = tasks.report(failure).path("tables");
        assertEquals("FINISHED", failedReports.get(0).path("status").asText());
        assertEquals(1000, failedReports.get(0).path("committedRows").asInt());
        assertEquals("FAILED", failedReports.get(1).path("status").asText());
        assertEquals("WRITE_FAILED", failedReports.get(1).path("failureCode").asText());
        assertEquals(0, failedReports.get(1).path("committedRows").asInt());
        assertEquals("0", env.pg("SELECT COUNT(*) FROM public.batch_bad_copy"));
        assertEquals("1000", env.pg("SELECT COUNT(*) FROM public.batch_good_copy"));
        assertEquals("0", tasks.claimCount(failure));

        env.pg("CREATE FUNCTION batch_delay() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN PERFORM pg_sleep(0.02); RETURN NEW; END $$");
        env.pg("CREATE TRIGGER batch_delay BEFORE INSERT ON public.batch_slow_copy "
                + "FOR EACH ROW EXECUTE FUNCTION batch_delay()");
        try {
            String slow = create(List.of(TaskFixtures.route("batch_slow", "batch_slow_copy", "UPSERT"),
                    TaskFixtures.route("batch_empty", "batch_empty_second", "UPSERT")), true);
            String running = tasks.run(slow, "RUNNING");
            try {
                assertEquals("2", tasks.claimCount(running));
                BackendE2EEnvironment.until("Batch reader progress", Duration.ofSeconds(35),
                        () -> tasks.report(running).path("tables").get(0).path("readRows").asInt(),
                        count -> count > 0);
            } finally {
                api.call("POST", "/api/v1/task-instances/" + running + "/cancel");
                tasks.await(running, Set.of("CANCELED"));
            }
            assertEquals("0", tasks.claimCount(running));
            JsonNode stopped = tasks.report(running).path("tables");
            assertTrue(TaskFixtures.texts(stopped, "status").contains("CANCELED"));
        } finally {
            env.pg("DROP TRIGGER batch_delay ON public.batch_slow_copy");
            env.pg("DROP FUNCTION batch_delay()");
        }

        scheduledBatch();
        atomicResourceConflict();
    }

    private String create(List<Map<String, Object>> routes, boolean publish) {
        return tasks.create("batch", tasks.parameters("DATABASE_OFFLINE", List.of("FULL"), routes), publish, true);
    }

    private void scheduledBatch() {
        String definition = create(List.of(TaskFixtures.route("batch_a", "batch_a_copy", "UPSERT")), true);
        String id = api.call("POST", "/api/v1/schedules",
                Map.of("targetType", "TASK", "targetId", definition,
                        "cronExpression", "0/10 * * * * ?", "timeZone", "UTC")).path("id").asText();
        api.call("POST", "/api/v1/schedules/" + id + "/enable", Map.of("revision", 1));
        try {
            JsonNode page = BackendE2EEnvironment.until("Batch Quartz admission", Duration.ofSeconds(65),
                    () -> api.call("POST", "/api/v1/schedules/" + id + "/fires/page",
                            Map.of("pageNo", 1, "pageSize", 100)),
                    data -> TaskFixtures.texts(data.path("bizData"), "outcome").contains("ADMITTED"));
            String accepted = null;
            for (JsonNode row : page.path("bizData")) {
                if ("ADMITTED".equals(row.path("outcome").asText())) accepted = row.path("instanceId").asText();
            }
            assertNotNull(accepted);
            tasks.await(accepted, Set.of("FINISHED"));
            assertEquals("DATABASE_OFFLINE", TaskFixtures.parse(api.call("GET",
                    "/api/v1/task-instances/" + accepted + "/definition")
                    .path("parameters").asText()).path("solutionType").asText());
            assertEquals(1, tasks.report(accepted).path("tables").size());
        } finally {
            api.call("POST", "/api/v1/schedules/" + id + "/disable", Map.of("revision", 2));
        }
    }

    private void atomicResourceConflict() {
        String realtime = tasks.single(true, false);
        String running = tasks.run(realtime, "RUNNING");
        try {
            String conflict = create(List.of(TaskFixtures.route("batch_a", "aaa_orders", "UPSERT"),
                    TaskFixtures.route("batch_b", "ods_orders", "UPSERT")), true);
            String claims = env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim");
            api.submit(conflict, UUID.randomUUID().toString(), 409);
            assertEquals(claims, env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim"));
            assertEquals(0, api.call("POST", "/api/v1/task-instances/page", Map.of(
                    "definitionId", conflict, "pageNo", 1, "pageSize", 20))
                    .path("pagination").path("total").asInt());
            assertEquals("0", env.pg("SELECT COUNT(*) FROM public.aaa_orders"));
        } finally {
            api.call("POST", "/api/v1/task-instances/" + running + "/cancel");
            tasks.await(running, Set.of("CANCELED"));
        }
    }
}
