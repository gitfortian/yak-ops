package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.e2e.support.ApiClient;
import io.yak.ops.e2e.support.BackendTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaMigrationIT extends BackendTestBase {
    @Test
    void previewDoesNotCreateTablesButExecutionDoes() throws Exception {
        var parameters = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("SCHEMA", "FULL"), List.of(fixture.route("orders", "created_orders")));
        var preview = fixture.api.call(
                "POST", "/api/v1/data-sync/schema/preview", Map.of("parameters", ApiClient.json(parameters)));
        assertFalse(preview.isEmpty());
        try (var connection = fixture.target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT to_regclass('" + fixture.table("created_orders") + "')")) {
            assertTrue(rows.next());
            assertEquals(null, rows.getObject(1));
        }
        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "FINISHED");
        fixture.assertTarget("created_orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
    }

    @Test
    void incompatibleExistingTargetFailsWithoutPartialData() throws Exception {
        fixture.target.execute("CREATE TABLE " + fixture.table("bad_orders") + " (id BIGINT PRIMARY KEY)");
        var parameters = fixture.parameters(
                "SINGLE_TABLE_OFFLINE", List.of("SCHEMA", "FULL"), List.of(fixture.route("orders", "bad_orders")));
        String definition = fixture.create(parameters, true);
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FAILED");
        assertEquals(0, fixture.claimCount(instance));
    }
}
