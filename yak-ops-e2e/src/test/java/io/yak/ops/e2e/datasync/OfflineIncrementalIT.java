package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.e2e.support.BackendTestBase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfflineIncrementalIT extends BackendTestBase {
    private String definition() throws Exception {
        fixture.createTarget("orders");
        Map<String, Object> route = new LinkedHashMap<>(fixture.route("orders", "orders"));
        route.put("incremental", Map.of("mode", "INCREASING", "column", "id", "initialValue", "0"));
        return fixture.create(
                fixture.parameters("SINGLE_TABLE_OFFLINE", List.of("FULL", "INCREMENTAL"), List.of(route)), true);
    }

    @Test
    void initialLoadNextWindowAndEmptyWindowUseBoundedExecutions() throws Exception {
        String definition = definition();
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "three")));
    }

    @Test
    void failedWindowDoesNotAdvanceCursorAndRetryReusesIt() throws Exception {
        String definition = definition();
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.target.execute("ALTER TABLE " + fixture.table("orders") + " ADD CONSTRAINT reject_new CHECK(id < 3)");
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        String failed = fixture.submit(definition);
        fixture.awaitState(failed, "FAILED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        assertEquals(0, fixture.claimCount(failed));
        fixture.target.execute("ALTER TABLE " + fixture.table("orders") + " DROP CONSTRAINT reject_new");
        fixture.source.execute("INSERT INTO orders VALUES(4,'four')");
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "three")));
        fixture.awaitState(fixture.submit(definition), "FINISHED");
        fixture.assertTarget(
                "orders", List.of(List.of(1L, "one"), List.of(2L, "two"), List.of(3L, "three"), List.of(4L, "four")));
    }
}
