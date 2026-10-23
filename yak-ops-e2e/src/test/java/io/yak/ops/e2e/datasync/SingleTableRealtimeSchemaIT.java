package io.yak.ops.e2e.datasync;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.e2e.support.BackendTestBase;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Starts real Task Definitions and checks runtime DDL at the public product boundary. */
class SingleTableRealtimeSchemaIT extends BackendTestBase {
    @Test
    void autoCreatesTargetAndEvolvesColumnsDuringStreaming() throws Exception {
        fixture.source.execute(
                "CREATE TABLE rich_orders (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL,"
                        + " amount DECIMAL(12,2) NULL, placed_at DATETIME(3) NULL)");
        fixture.source.execute(
                "INSERT INTO rich_orders VALUES (1,'first',12.34,'2026-10-23 08:00:00.123'),"
                        + "(2,'second',56.78,NULL)");
        Map<String, Object> parameters = new LinkedHashMap<>(
                fixture.parameters(
                        "SINGLE_TABLE_REALTIME",
                        List.of("SCHEMA", "FULL", "INCREMENTAL"),
                        List.of(fixture.route("rich_orders", "rich_orders"))));
        parameters.put("schemaChangesEnabled", true);

        // Target intentionally does not exist. Task publication must not execute the DDL.
        String definition = fixture.create(parameters, true);
        try (Connection connection = fixture.target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                                + fixture.targetSchema + "' AND table_name='rich_orders'")) {
            rows.next();
            assertEquals(0, rows.getLong(1));
        }

        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("rich_orders", List.of(List.of(1L, "first"), List.of(2L, "second")));

        fixture.source.execute("ALTER TABLE rich_orders ADD COLUMN tracking_code VARCHAR(64) NULL");
        fixture.source.execute("UPDATE rich_orders SET tracking_code='tracking-1' WHERE id=1");
        awaitString("rich_orders", "tracking_code", 1L, "tracking-1");

        fixture.source.execute("ALTER TABLE rich_orders MODIFY COLUMN name VARCHAR(180) NOT NULL");
        fixture.source.execute("UPDATE rich_orders SET name='a-name-longer-than-thirty-two-characters' WHERE id=1");
        awaitString("rich_orders", "name", 1L, "a-name-longer-than-thirty-two-characters");
        await()
                .alias("PostgreSQL name type widened to VARCHAR(180)")
                .atMost(Duration.ofSeconds(120))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.createStatement();
                            var rows = statement.executeQuery(
                                    "SELECT character_maximum_length FROM information_schema.columns "
                                            + "WHERE table_schema='" + fixture.targetSchema
                                            + "' AND table_name='rich_orders' AND column_name='name'")) {
                        rows.next();
                        assertEquals(180, rows.getInt(1));
                    }
                });
        fixture.awaitState(instance, "RUNNING");
    }

    @Test
    void standaloneDropColumnRequiresExplicitConsentAndUpdatesFollowingRows() throws Exception {
        fixture.source.execute("CREATE TABLE drop_orders (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL, remark VARCHAR(200))");
        fixture.source.execute("INSERT INTO drop_orders VALUES (1,'before','original')");
        Map<String, Object> parameters = new LinkedHashMap<>(fixture.parameters(
                "SINGLE_TABLE_REALTIME", List.of("SCHEMA", "FULL", "INCREMENTAL"),
                List.of(fixture.route("drop_orders", "drop_orders"))));
        parameters.put("schemaChangesEnabled", true);
        parameters.put("allowDropColumns", true);
        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("drop_orders", List.of(List.of(1L, "before")));

        fixture.source.execute("ALTER TABLE drop_orders DROP COLUMN remark");
        await().alias("DROP COLUMN without following DML").atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var query = connection.prepareStatement(
                                    "SELECT COUNT(*) FROM information_schema.columns "
                                            + "WHERE table_schema=? AND table_name=? AND column_name=?")) {
                        query.setString(1, fixture.targetSchema);
                        query.setString(2, "drop_orders");
                        query.setString(3, "remark");
                        try (var rows = query.executeQuery()) {
                            rows.next();
                            assertEquals(0, rows.getInt(1));
                        }
                    }
                });
        fixture.source.execute("UPDATE drop_orders SET name='after-drop' WHERE id=1");
        fixture.awaitTarget("drop_orders", List.of(List.of(1L, "after-drop")));
        fixture.awaitState(instance, "RUNNING");
    }

    @Test
    void legacyRealtimeTaskDoesNotSilentlyApplySchemaChanges() throws Exception {
        // The field is intentionally absent. Existing persisted Task Definitions stay disabled.
        Map<String, Object> parameters = fixture.parameters(
                "SINGLE_TABLE_REALTIME",
                List.of("FULL", "INCREMENTAL"),
                List.of(fixture.route("orders", "orders")));
        fixture.createTarget("orders");
        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));

        fixture.source.execute("ALTER TABLE orders ADD COLUMN tracking_code VARCHAR(64) NULL");
        fixture.source.execute("UPDATE orders SET tracking_code='not-applied' WHERE id=1");
        fixture.awaitState(instance, "FAILED");
        try (Connection connection = fixture.target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='"
                                + fixture.targetSchema
                                + "' AND table_name='orders' AND column_name='tracking_code'")) {
            rows.next();
            assertEquals(0, rows.getLong(1));
        }
    }

    private void awaitString(String table, String column, long id, String expected) {
        // Source DDL is applied asynchronously. Wait for the target column before
        // querying it: PostgreSQL SQLSTATE 42703 is not an Awaitility assertion failure.
        await()
                .alias("Runtime target column " + table + "." + column)
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.prepareStatement(
                                    "SELECT COUNT(*) FROM information_schema.columns "
                                            + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
                        statement.setString(1, fixture.targetSchema);
                        statement.setString(2, table);
                        statement.setString(3, column);
                        try (var rows = statement.executeQuery()) {
                            rows.next();
                            assertEquals(1, rows.getLong(1));
                        }
                    }
                });
        await()
                .alias("Runtime DDL and CDC data " + table + "." + column)
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.createStatement();
                            var rows = statement.executeQuery(
                                    "SELECT " + column + " FROM " + fixture.table(table) + " WHERE id=" + id)) {
                        rows.next();
                        assertEquals(expected, rows.getString(1));
                    }
                });
    }
}
