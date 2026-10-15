package io.yak.ops.boot.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Real offline initial-load and bounded-cursor tests, including failed progress commits. */
class OfflineIncrementalIT extends BackendE2ETest {
    @Test
    void initialLoadBoundedIncrementalEmptyRunAndMultiTableProgress() {
        String suffix = TaskFixtures.unique();
        String source = "offline_orders_" + suffix, target = "offline_orders_copy_" + suffix;
        env.mysql("CREATE TABLE app." + source
                + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)");
        env.mysql("INSERT INTO app." + source
                + " VALUES (1,'initial','2026-10-01 01:00:00.000000'),"
                + "(2,'other','2026-10-01 01:00:00.000000')");
        env.pg("CREATE TABLE public." + target
                + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)");

        String definition = create(List.of(mapping(source, target, null)), List.of("FULL", "INCREMENTAL"),
                "SINGLE_TABLE_OFFLINE");
        String first = run(definition, "FINISHED", "FULL");
        assertEquals(1, state(definition).size());
        assertTrue(state(definition).get(0).startsWith("1:1:"));
        assertEquals("2", env.pg("SELECT COUNT(*) FROM public." + target));

        env.mysql("UPDATE app." + source + " SET name='updated', updated_at=UTC_TIMESTAMP(6) WHERE id=1");
        env.mysql("INSERT INTO app." + source + " VALUES (3,'new',UTC_TIMESTAMP(6))");
        waitForSourceClock(source);
        String second = run(definition, "FINISHED", "INCREMENTAL");
        assertEquals("updated,other,new",
                env.pg("SELECT string_agg(name,',' ORDER BY id) FROM public." + target));
        assertTrue(state(definition).get(0).startsWith("1:2:"));
        String third = run(definition, "FINISHED", "INCREMENTAL");
        assertTrue(state(definition).get(0).startsWith("1:3:"));
        assertEquals("3", env.pg("SELECT COUNT(*) FROM public." + target));

        String secondSource = "offline_clients_" + suffix, secondTarget = "offline_clients_copy_" + suffix;
        env.mysql("CREATE TABLE app." + secondSource
                + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)");
        env.mysql("INSERT INTO app." + secondSource
                + " VALUES (10,'client','2026-10-01 01:00:00.000000')");
        env.pg("CREATE TABLE public." + secondTarget
                + " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)");
        String database = create(List.of(mapping(source, target, null), mapping(secondSource, secondTarget, null)),
                List.of("FULL", "INCREMENTAL"), "DATABASE_OFFLINE");
        run(database, "FINISHED", "FULL");
        assertEquals(2, state(database).size());
        assertTrue(state(database).stream().allMatch(row -> row.startsWith("1:1:")));
        env.mysql("UPDATE app." + secondSource + " SET name='changed', updated_at=UTC_TIMESTAMP(6) WHERE id=10");
        waitForSourceClock(secondSource);
        run(database, "FINISHED", "INCREMENTAL");
        assertTrue(state(database).stream().allMatch(row -> row.startsWith("1:2:")));
        assertEquals("changed", env.pg("SELECT name FROM public." + secondTarget + " WHERE id=10"));

        String bad = "offline_reject_" + suffix;
        env.pg("CREATE TABLE public." + bad + " (id BIGINT PRIMARY KEY CONSTRAINT reject_" + suffix
                + " CHECK (id < 0), name VARCHAR(32) NOT NULL, updated_at TIMESTAMP(6) NOT NULL)");
        String failing = create(List.of(mapping(source, bad, "2026-09-01T00:00:00")), List.of("INCREMENTAL"),
                "SINGLE_TABLE_OFFLINE");
        String failed = run(failing, "FAILED", null);
        assertEquals(1, state(failing).size());
        assertTrue(state(failing).get(0).startsWith("0:0:NULL:"));
        assertEquals("0", env.pg("SELECT COUNT(*) FROM public." + bad));
        env.pg("ALTER TABLE public." + bad + " DROP CONSTRAINT reject_" + suffix);
        String resumed = run(failing, "FINISHED", "INCREMENTAL");
        assertTrue(state(failing).get(0).startsWith("1:1:"));
        assertEquals("3", env.pg("SELECT COUNT(*) FROM public." + bad));
        assertEquals(5, Set.of(first, second, third, failed, resumed).size());

        String exactReport = api.call("GET", "/api/v1/task-instances/" + first).path("executionReport").asText();
        String workspace = api.workspace();
        env.restart(false);
        api = env.login();
        api.workspace(workspace);
        assertEquals(exactReport, api.call("GET", "/api/v1/task-instances/" + first)
                .path("executionReport").asText());
    }

    private void waitForSourceClock(String table) {
        BackendE2EEnvironment.until("Bounded cursor upper timestamp", Duration.ofSeconds(10),
                () -> env.mysql("SELECT UTC_TIMESTAMP(6) > MAX(updated_at) FROM app." + table), "1"::equals);
    }

    private Map<String, Object> mapping(String source, String target, String initial) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("mode", "TIMESTAMP");
        options.put("column", "updated_at");
        options.put("delaySeconds", 0);
        options.put("lookbackSeconds", 0);
        if (initial != null) options.put("initialValue", initial);
        return TaskFixtures.route(source, target, "UPSERT", options);
    }

    private String create(List<Map<String, Object>> mapping, List<String> steps, String solution) {
        return tasks.create("incremental", tasks.parameters(solution, steps, mapping), true, true);
    }

    private List<String> state(String definition) {
        String rows = env.mysql("SELECT CONCAT(initialized,':',revision,':',"
                + "IFNULL(cursor_value,'NULL'),':',IFNULL(pending_value,'NULL')) "
                + "FROM yak_ops.yak_ops_task_cursor WHERE definition_id='" + definition + "' ORDER BY table_key");
        return rows.isBlank() ? List.of() : List.of(rows.split("\n"));
    }

    private String run(String definition, String expected, String phase) {
        String id = tasks.run(definition, expected);
        if (phase != null && expected.equals("FINISHED")) {
            List<String> observed = TaskFixtures.texts(tasks.report(id).path("tables"), "step");
            assertFalse(observed.isEmpty());
            assertTrue(observed.stream().allMatch(phase::equals), "Unexpected execution phase: " + observed);
        }
        return id;
    }
}
