package io.yak.ops.boot.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Real Task admission, versions, metrics, logs and Workspace isolation. */
class TaskExecutionIT extends BackendE2ETest {
    @Test
    void taskLifecycleAndPinnedVersionsSurviveUpdates() {
        JsonNode choices = api.call("GET", "/api/v1/data-sync/solutions?sourceType=MYSQL&targetType=POSTGRE_SQL");
        assertEquals(List.of("SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME", "DATABASE_OFFLINE", "DATABASE_REALTIME"),
                TaskFixtures.texts(choices, "solutionType"));
        assertEquals("FULL", choices.get(1).path("defaultSteps").get(0).asText());
        assertEquals(2, api.call("GET", "/api/v1/data-sync/solutions?sourceType=POSTGRE_SQL&targetType=MYSQL").size());
        api.call("GET", "/api/v1/data-sync/solutions?sourceType=JDBC&targetType=MYSQL", null, 400);

        String definition = tasks.single(false, true);
        String key = UUID.randomUUID().toString();
        JsonNode submitted = api.submit(definition, key, 202);
        assertFalse(submitted.path("replayed").asBoolean());
        assertTrue(api.submit(definition, key, 202).path("replayed").asBoolean());
        String instance = submitted.path("instanceId").asText();
        tasks.await(instance, Set.of("FINISHED"));
        assertEquals("2", env.pg("SELECT COUNT(*) FROM public.ods_orders"));
        assertEquals("0", tasks.claimCount(instance));

        String dashboard = "/api/v1/data-sync/operations/dashboard";
        JsonNode metrics = api.call("GET", dashboard + "?syncType=OFFLINE&range=LAST_7_DAYS");
        assertEquals(1, metrics.path("summary").path("executionCount").asInt());
        assertEquals(1, metrics.path("summary").path("succeededCount").asInt());
        assertEquals(0, metrics.path("summary").path("failedCount").asInt());
        assertEquals(7, metrics.path("trend").size());
        assertEquals(0, api.call("GET", dashboard + "?syncType=REALTIME&range=LAST_7_DAYS")
                .path("summary").path("executionCount").asInt());
        api.call("GET", dashboard + "?syncType=OTHER", null, 400);
        api.call("GET", dashboard + "?syncType=OFFLINE&range=FOREVER", null, 400);

        String workspace = api.workspace();
        String another = api.call("POST", "/api/v1/workspaces", Map.of("name", "isolated-" + TaskFixtures.unique()))
                .path("id").asText();
        api.workspace(another);
        assertEquals(0, api.call("GET", dashboard + "?syncType=OFFLINE&range=LAST_7_DAYS")
                .path("summary").path("executionCount").asInt());
        api.call("GET", "/api/v1/task-instances/" + instance, null, 404);
        api.workspace(workspace);

        JsonNode record = api.call("GET", "/api/v1/task-instances/" + instance);
        assertEquals(definition, record.path("definitionId").asText());
        assertEquals("MANUAL", record.path("triggerType").asText());
        assertEquals("FINISHED", record.path("status").asText());
        assertEquals("YAKFLOW", record.path("runtimeEngine").asText());
        assertTrue(record.path("debug").asBoolean());
        assertFalse(record.path("runtimeId").asText().isEmpty());
        assertEquals(100, record.path("progressPercent").asInt());
        JsonNode latest = api.call("POST", "/api/v1/task-instances/latest", Map.of("definitionIds", List.of(definition)));
        assertEquals(instance, latest.get(0).path("id").asText());
        assertEquals(0, api.call("POST", "/api/v1/task-instances/latest",
                Map.of("definitionIds", List.of(definition), "activeOnly", true)).size());

        BackendE2EEnvironment.until("debug task logs", Duration.ofSeconds(35), () ->
                api.call("GET", "/api/v1/task-instances/" + instance + "/logs?limit=200"),
                page -> page.path("content").asText().contains("YakFlow Job started"));
        String logs = api.download("/api/v1/task-instances/" + instance + "/logs/download");
        for (String event : List.of("YakFlow Job started", "JDBC query executed",
                "JDBC fetch completed", "JDBC statement batch executed", "JDBC flush completed",
                "Source summary", "JDBC sink summary")) {
            assertTrue(logs.contains(event), "Expected execution event: " + event);
        }
        assertFalse(logs.contains(BackendE2EEnvironment.PASSWORD));
        api.call("GET", "/api/v1/task-instances/" + instance + "/logs?cursor=bad!!", null, 400);

        JsonNode snapshot = api.call("GET", "/api/v1/task-instances/" + instance + "/definition");
        assertEquals(definition, snapshot.path("definitionId").asText());
        assertEquals(submitted.path("definitionVersion").asInt(), snapshot.path("version").asInt());
        assertEquals(3, snapshot.path("parameterSchemaVersion").asInt());
        assertEquals("SINGLE_TABLE_OFFLINE", TaskFixtures.parse(snapshot.path("parameters").asText())
                .path("solutionType").asText());
        assertEquals(1, api.call("POST", "/api/v1/task-instances/page",
                Map.of("pageNo", 1, "pageSize", 20, "definitionId", definition,
                        "taskType", "DATA_SYNC", "status", "FINISHED"))
                .path("pagination").path("total").asInt());
        assertEquals(0, api.call("POST", "/api/v1/task-instances/page",
                Map.of("pageNo", 1, "pageSize", 20, "definitionId", definition, "status", "RUNNING"))
                .path("pagination").path("total").asInt());

        legacyVersionContract(definition);
    }

    private void legacyVersionContract(String v3) {
        env.pg("CREATE TABLE public.version_orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        Map<String, Object> parameters = tasks.parameters("SINGLE_TABLE_OFFLINE", List.of("FULL"),
                List.of(TaskFixtures.route("orders", "version_orders", "UPSERT")));
        Map<String, Object> old = Map.of(
                "source", Map.of("type", "JDBC", "datasourceId", tasks.source, "table", "orders"),
                "transform", List.of(),
                "sink", Map.of("type", "JDBC", "datasourceId", tasks.target, "schema", "public",
                        "table", "version_orders", "writeMode", "UPSERT"));
        String name = "version-" + TaskFixtures.unique();
        String definition = api.call("POST", "/api/v1/task-definitions",
                Map.of("name", name, "taskType", "DATA_SYNC", "parameters", TaskFixtures.json(parameters)))
                .path("id").asText();
        String hex = HexFormat.of().formatHex(TaskFixtures.json(old).getBytes(StandardCharsets.UTF_8));
        env.mysql("UPDATE yak_ops.yak_ops_task_definition_version SET parameter_schema_version=2, "
                + "parameters=CONVERT(0x" + hex + " USING utf8mb4) "
                + "WHERE definition_id='" + definition + "' AND version=1");
        assertEquals(2, api.call("GET", "/api/v1/task-definitions/" + definition)
                .path("parameterSchemaVersion").asInt());
        api.call("POST", "/api/v1/task-definitions/" + definition + "/publish");
        String first = tasks.run(definition, "FINISHED");
        JsonNode oldVersion = api.call("GET", "/api/v1/task-instances/" + first + "/definition");
        assertEquals(2, oldVersion.path("parameterSchemaVersion").asInt());
        assertEquals(TaskFixtures.parse(TaskFixtures.json(old)), TaskFixtures.parse(oldVersion.path("parameters").asText()));
        JsonNode selected = api.call("POST", "/api/v1/task-definitions/page",
                Map.of("taskType", "DATA_SYNC", "pageNo", 1, "pageSize", 200,
                        "parameterFilters", Map.of("solutionType", "SINGLE_TABLE_OFFLINE")));
        assertTrue(TaskFixtures.texts(selected.path("bizData"), "id").containsAll(List.of(definition, v3)));

        api.call("POST", "/api/v1/task-definitions/" + definition + "/unpublish");
        JsonNode metadata = api.call("PUT", "/api/v1/task-definitions/" + definition,
                Map.of("name", name + "-renamed", "debug", true));
        assertEquals(1, metadata.path("definitionVersion").asInt());
        assertEquals(2, metadata.path("parameterSchemaVersion").asInt());
        JsonNode migrated = api.call("PUT", "/api/v1/task-definitions/" + definition,
                Map.of("name", name + "-renamed", "debug", true, "parameters", TaskFixtures.json(parameters)));
        assertEquals(2, migrated.path("definitionVersion").asInt());
        assertEquals(3, migrated.path("parameterSchemaVersion").asInt());
        JsonNode historical = api.call("GET", "/api/v1/task-instances/" + first + "/definition");
        assertEquals(2, historical.path("parameterSchemaVersion").asInt());
        assertEquals(1, historical.path("version").asInt());
        api.call("POST", "/api/v1/task-definitions/" + definition + "/publish");
        tasks.run(definition, "FINISHED");
        assertEquals("2", env.pg("SELECT COUNT(*) FROM public.version_orders"));

        api.call("POST", "/api/v1/task-definitions", Map.of("name", "reject-" + TaskFixtures.unique(),
                "taskType", "DATA_SYNC", "parameters", TaskFixtures.json(old)), 400);
        for (List<String> steps : List.of(List.<String>of(), List.of("SCHEMA"), List.of("SCHEMA", "INCREMENTAL"),
                List.of("SCHEMA", "FULL", "FULL"))) {
            Map<String, Object> invalid = new java.util.LinkedHashMap<>(parameters);
            invalid.put("steps", steps);
            api.call("POST", "/api/v1/task-definitions",
                    Map.of("name", "reject-" + TaskFixtures.unique(), "taskType", "DATA_SYNC",
                            "parameters", TaskFixtures.json(invalid)), 400);
        }
    }
}
