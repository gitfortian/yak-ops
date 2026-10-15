package io.yak.ops.boot.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates actual data-source and Task definitions in the disposable test Workspace. */
final class TaskFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();
    final BackendE2EEnvironment env;
    final TaskApiClient api;
    final String source;
    final String target;

    TaskFixtures(BackendE2EEnvironment env, TaskApiClient api) {
        this.env = env;
        this.api = api;
        String workspace = api.call("POST", "/api/v1/workspaces", Map.of("name", "e2e-" + unique())).path("id")
                .asText();
        api.workspace(workspace);
        source = datasource("MYSQL", "app", "root", env.mysqlHost(), env.mysqlPort());
        target = datasource("POSTGRE_SQL", "ods", "yak", env.postgresHost(), env.postgresPort());
        // Prove the actual Boot datasource plugin sees both physical tables, rather than
        // assuming the JDBC connection used by the test harness proves publication access.
        requireTable(source, Map.of("database", "app"), "orders");
        requireTable(target, Map.of("database", "ods", "schema", "public"), "ods_orders");
    }

    String datasource(String kind, String db, String username, String host, int port) {
        String name = kind + "-" + unique();
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("host", host);
        connection.put("port", port);
        connection.put("database", db);
        connection.put("username", username);
        connection.put("password", BackendE2EEnvironment.PASSWORD);
        // The official MySQL 8 container uses caching_sha2_password without an SSL setup.
        if ("MYSQL".equals(kind)) {
            connection.put("properties", Map.of("allowPublicKeyRetrieval", "true", "useSSL", "false"));
        }
        api.call("POST", "/api/v1/data-source", Map.of(
                "name", name, "dbType", kind, "environment", "TEST", "connectionParams", connection));
        JsonNode records = api.call("POST", "/api/v1/data-source/page",
                Map.of("pageNo", 1, "pageSize", 100, "sorts", List.of())).path("bizData");
        for (JsonNode record : records) {
            if (record.path("name").asText().equals(name)) return record.path("id").asText();
        }
        throw new AssertionError("Expected test data source was not created for " + kind);
    }

    private void requireTable(String id, Map<String, String> query, String name) {
        JsonNode result = api.call("POST", "/api/v1/data-source/" + id + "/catalog/tables", query);
        for (JsonNode row : result) {
            if (name.equals(row.path("name").asText())) return;
        }
        throw new AssertionError("Configured datasource did not expose expected E2E table: " + name);
    }

    static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    static Map<String, Object> route(String from, String to, String mode) {
        return Map.of("source", Map.of("table", from), "target", Map.of("schema", "public", "table", to),
                "writeMode", mode);
    }

    static Map<String, Object> route(String from, String to, String mode, Map<String, Object> incremental) {
        Map<String, Object> mapping = new LinkedHashMap<>(route(from, to, mode));
        mapping.put("incremental", incremental);
        return mapping;
    }

    Map<String, Object> parameters(String solution, List<String> steps, List<Map<String, Object>> mappings) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("solutionType", solution);
        parameters.put("steps", steps);
        parameters.put("source", Map.of("datasourceId", source));
        parameters.put("sink", Map.of("datasourceId", target));
        parameters.put("tableSelection", Map.of("mode", "EXPLICIT"));
        parameters.put("tableMappings", mappings);
        return parameters;
    }

    Map<String, Object> parameters(String solution, List<String> steps, List<Map<String, Object>> mappings,
            String sourceId, String targetId) {
        Map<String, Object> values = parameters(solution, steps, mappings);
        values.put("source", Map.of("datasourceId", sourceId));
        values.put("sink", Map.of("datasourceId", targetId));
        return values;
    }

    String create(String name, Map<String, Object> parameters, boolean publish, boolean debug) {
        JsonNode data = api.call("POST", "/api/v1/task-definitions", Map.of("name", name + "-" + unique(),
                "taskType", "DATA_SYNC", "debug", debug, "parameters", json(parameters)));
        String id = data.path("id").asText();
        if (publish) api.call("POST", "/api/v1/task-definitions/" + id + "/publish");
        return id;
    }

    String single(boolean realtime, boolean debug) {
        return create("task", parameters(realtime ? "SINGLE_TABLE_REALTIME" : "SINGLE_TABLE_OFFLINE",
                realtime ? List.of("FULL", "INCREMENTAL") : List.of("FULL"),
                List.of(route("orders", "ods_orders", realtime ? "UPSERT" : "APPEND"))), true, debug);
    }

    String run(String definition, String expected) {
        String id = api.submit(definition).path("instanceId").asText();
        await(id, Set.of(expected));
        return id;
    }

    JsonNode await(String id, Set<String> expected) {
        return BackendE2EEnvironment.until("Task " + id, Duration.ofSeconds(180), () -> {
            JsonNode item = api.call("GET", "/api/v1/task-instances/" + id);
            String actual = item.path("status").asText();
            if (Set.of("FAILED", "FINISHED", "CANCELED").contains(actual) && !expected.contains(actual)) {
                throw new AssertionError("Task " + id + " ended in unexpected state " + actual);
            }
            return item;
        }, item -> expected.contains(item.path("status").asText()));
    }

    String claimCount(String instance) {
        return env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim WHERE instance_id='" + instance + "'");
    }

    JsonNode report(String instance) {
        try {
            return JSON.readTree(api.call("GET", "/api/v1/task-instances/" + instance)
                    .path("executionReport").asText());
        } catch (Exception failure) {
            throw new IllegalStateException("Instance report missing or invalid: " + instance, failure);
        }
    }

    static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode row : array) values.add(row.path(field).asText());
        return values;
    }
}
