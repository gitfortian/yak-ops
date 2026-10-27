package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.task.MySqlSnapshotSplitReadTask;
import io.yak.ops.connector.cdc.mysql.debezium.reader.SnapshotSplitReader;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlChunkSplitter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real JDBC and Debezium with writes placed after scanning and before the per-chunk High. */
@Tag("cdc-snapshot")
@Timeout(180)
class MySqlBackfillIT {
    @RegisterExtension static final MySqlDatabase DATABASE = MySqlDatabase.cdc();
    private static final TableSchema SCHEMA = new TableSchema(List.of(
            new Column("id", LogicalTypes.BIGINT.copy(false)),
            new Column("name", LogicalTypes.varchar(100).copy(false))), List.of("id"));

    @Test
    void backfillsRepeatedUpdatesDeletesCrossChunkMovesAndIgnoresOtherTables() throws Exception {
        String table = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        String other = table + "_other";
        execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "CREATE TABLE " + other + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "INSERT INTO " + table + " VALUES(1,'old'),(2,'deleted'),(50,'moving')");
        TableId id = new TableId("yak_cdc_it", null, table);
        TableId another = new TableId("yak_cdc_it", null, other);
        try {
            var config = config(Map.of(id, SCHEMA, another, SCHEMA), 6501, table);
            try (var reader = new MySqlSnapshotSplitReadTask(config, 0, () -> {
                execute("UPDATE " + table + " SET name='intermediate' WHERE id=1",
                        "UPDATE " + table + " SET name='final' WHERE id=1",
                        "DELETE FROM " + table + " WHERE id=2",
                        "UPDATE " + table + " SET id=150 WHERE id=50",
                        "INSERT INTO " + table + " VALUES(5,'new')",
                        "INSERT INTO " + other + " VALUES(7,'not-this-chunk')");
                return null;
            })) {
                var split = reader.read(MySqlSnapshotSplit.typed("chunk", new ChunkRange(id, "id", LogicalTypeRoot.BIGINT, null, 100L)));
                Map<Long,String> values = new LinkedHashMap<>();
                for (var row : split.output().rows()) {
                    assertEquals(RowKind.INSERT, row.rowKind());
                    values.put(row.row().getLong(0), row.row().getString(1));
                }
                assertEquals(Map.of(1L,"final",5L,"new"), values);
                assertTrue(split.output().high().compareTo(split.output().low()) > 0);
                assertEquals(0, split.outputPosition());
            }
        } finally { execute("DROP TABLE " + table, "DROP TABLE " + other); }
    }

    @Test
    void quietEmptyChunkAndFollowingScanFinishFromActualWatermarks() throws Exception {
        String table = "quiet_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        TableId id = new TableId("yak_cdc_it", null, table);
        try (var reader = new MySqlSnapshotSplitReadTask(config(Map.of(id, SCHEMA), 6601, table), 0)) {
            var empty = reader.read(MySqlSnapshotSplit.typed("empty", new ChunkRange(id, "id", LogicalTypeRoot.BIGINT, null, 10L)));
            assertTrue(empty.output().rows().isEmpty());
            assertEquals(0, empty.output().low().compareTo(empty.output().high()));
            execute("INSERT INTO " + table + " VALUES(20,'present')");
            var second = reader.read(MySqlSnapshotSplit.typed("second", new ChunkRange(id, "id", LogicalTypeRoot.BIGINT, 10L, null)));
            assertEquals(1, second.output().rows().size());
            assertEquals(20L, second.output().rows().getFirst().row().getLong(0));
        } finally { execute("DROP TABLE " + table); }
    }

    @Test
    void plansKeylessStringChunksWithActualMySqlCollation() throws Exception {
        String table = "typed_" + UUID.randomUUID().toString().replace("-", "");
        execute(
                "CREATE TABLE " + table
                        + "(id INT NOT NULL, code VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL)",
                "INSERT INTO " + table + " VALUES (1,'a'),(2,'A'),(3,'b'),(4,'B'),(5,'c')");
        TableId id = new TableId("yak_cdc_it", null, table);
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.INTEGER.copy(false)),
                        new Column("code", LogicalTypes.varchar(40).copy(false))),
                List.of());
        var db = DATABASE.metadata();
        try {
            var splitter = new MySqlChunkSplitter(
                    () -> DriverManager.getConnection(db.url(), db.username(), db.password()),
                    JdbcDialects.forUrl(db.url()),
                    Map.of(id, schema),
                    Map.of(id, "code"),
                    1);
            List<ChunkRange> ranges = splitter.splitChunks(id);
            assertEquals(3, ranges.size());
            assertEquals(ranges.get(1), splitter.nextChunk(id, ranges.getFirst().end()));
            List<Integer> observed = new ArrayList<>();
            try (var connection = DriverManager.getConnection(db.url(), db.username(), db.password())) {
                for (ChunkRange range : ranges) {
                    String key = "`code`";
                    List<String> predicates = new ArrayList<>();
                    if (range.start() != null) predicates.add(key + " >= ?");
                    if (range.end() != null) predicates.add(key + " < ?");
                    String sql = "SELECT id FROM " + table
                            + (predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates));
                    try (var statement = connection.prepareStatement(sql)) {
                        int parameter = 1;
                        if (range.start() != null) statement.setObject(parameter++, range.start());
                        if (range.end() != null) statement.setObject(parameter, range.end());
                        try (var rows = statement.executeQuery()) {
                            while (rows.next()) observed.add(rows.getInt(1));
                        }
                    }
                }
            }
            observed.sort(Integer::compareTo);
            assertEquals(List.of(1, 2, 3, 4, 5), observed);
        } finally {
            execute("DROP TABLE " + table);
        }
    }


    @Test
    void typedKeyedChunkReconcilesTransactionCommittedBetweenLowAndReadView() throws Exception {
        String table = "typed_tx_" + UUID.randomUUID().toString().replace("-", "");
        execute(
                "CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "INSERT INTO " + table + " VALUES (1,'before'),(2,'removed'),(50,'moved')");
        TableId id = new TableId("yak_cdc_it", null, table);
        try (var reader = new MySqlSnapshotSplitReadTask(
                config(Map.of(id, SCHEMA), 6811, table),
                0,
                () -> null,
                () -> {
                    // Commit after Low but before the repeatable-read view is established.
                    // Snapshot may include these rows; Low-to-High Backfill must reconcile them.
                    var db = DATABASE.metadata();
                    try (var transaction = DriverManager.getConnection(db.url(), db.username(), db.password());
                            var statement = transaction.createStatement()) {
                        transaction.setAutoCommit(false);
                        statement.executeUpdate("UPDATE " + table + " SET name='after' WHERE id=1");
                        statement.executeUpdate("DELETE FROM " + table + " WHERE id=2");
                        statement.executeUpdate("UPDATE " + table + " SET id=150 WHERE id=50");
                        statement.executeUpdate("INSERT INTO " + table + " VALUES (3,'inserted')");
                        transaction.commit();
                    }
                    return null;
                },
                () -> null)) {
            var prepared = reader.read(MySqlSnapshotSplit.typed("typed-transaction", new ChunkRange(id, "id", LogicalTypeRoot.BIGINT, null, 100L)));
            Map<Long, String> actual = new LinkedHashMap<>();
            for (var row : prepared.output().rows()) {
                assertEquals(RowKind.INSERT, row.rowKind());
                actual.put(row.row().getLong(0), row.row().getString(1));
            }
            assertEquals(Map.of(1L, "after", 3L, "inserted"), actual);
            assertTrue(prepared.output().high().compareTo(prepared.output().low()) > 0);
        } finally {
            execute("DROP TABLE " + table);
        }
    }

    @Test
    void wideSnapshotChunkFailsBeforePublishingPartialOutputWhenByteBudgetIsExceeded() throws Exception {
        String table = "wide_chunk_" + UUID.randomUUID().toString().replace("-", "");
        execute(
                "CREATE TABLE " + table + "(id BIGINT PRIMARY KEY AUTO_INCREMENT, payload VARCHAR(12000) NOT NULL)",
                "INSERT INTO " + table + "(payload) VALUES (REPEAT('x', 12000))");
        TableId id = new TableId("yak_cdc_it", null, table);
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("payload", LogicalTypes.varchar(12000).copy(false))),
                List.of("id"));
        try {
            // Every INSERT doubles the number of rows; 256 wide rows exceed the 4 MiB
            // retained-payload budget without approaching the 100,000-row cap.
            for (int index = 0; index < 8; index++) {
                execute("INSERT INTO " + table + "(payload) SELECT payload FROM " + table);
            }
            try (var reader = new MySqlSnapshotSplitReadTask(config(Map.of(id, schema), 6821, table), 0)) {
                var failure = assertThrows(IllegalStateException.class, () -> reader.read(
                        MySqlSnapshotSplit.typed("wide", new ChunkRange(id, "id", LogicalTypeRoot.BIGINT, null, null))));
                assertTrue(failure.getMessage().contains("buffer limit"), failure.getMessage());
            }
        } finally {
            execute("DROP TABLE " + table);
        }
    }

    @Test
    void limitedReplicationPrivilegesPermitTypedSnapshotReadViewWithoutGlobalReadLock() throws Exception {
        String table = "limited_" + UUID.randomUUID().toString().replace("-", "");
        String user = "snap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        execute(
                "CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "INSERT INTO " + table + " VALUES (1,'original')",
                "CREATE USER '" + user + "'@'%' IDENTIFIED BY 'test-only-snapshot'",
                "GRANT SELECT ON yak_cdc_it.* TO '" + user + "'@'%'",
                "GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '" + user + "'@'%'");
        TableId id = new TableId("yak_cdc_it", null, table);
        try {
            var db = DATABASE.metadata();
            var config = new MySqlSourceConfig(
                    db.host(), db.port(), user, "test-only-snapshot", 6831, table, Map.of(id, SCHEMA), 256);
            // Verify the restricted account can scan real rows with ordinary JDBC SELECT.
            // Debezium no_data schema bootstrap has separate locking requirements and is
            // deliberately not part of this JDBC privilege-boundary assertion.
            try (var snapshots = new SnapshotSplitReader(config)) {
                snapshots.open(MySqlSnapshotSplit.typed("typed", new ChunkRange(id, "id", LogicalTypeRoot.BIGINT, null, null)));
                var batch = snapshots.fetch();
                assertEquals("typed", batch.nextSplit());
                var record = batch.nextRecordFromSplit();
                assertNotNull(record);
                assertEquals(1L, record.row().getLong(0));
                assertNull(batch.nextRecordFromSplit());
                assertTrue(batch.finishedSplits().contains("typed"));
                batch.recycle();
            }
            // Exercise the actual typed read-view code with no global read-lock privilege.
            // Ordinary SELECT plus Binlog position access is sufficient for this JDBC boundary.
            try (var reader = new MySqlSnapshotSplitReadTask(config, 1)) {
                reader.openTypedSnapshotReadView(
                        MySqlSnapshotSplit.typed("typed", new ChunkRange(id, "id", LogicalTypeRoot.BIGINT, null, null)));
            }
        } finally {
            execute("DROP USER IF EXISTS '" + user + "'@'%'", "DROP TABLE " + table);
        }
    }

    private static MySqlSourceConfig config(Map<TableId, TableSchema> schemas, int serverId, String topic) {
        var db = DATABASE.metadata();
        return new MySqlSourceConfig(db.host(), db.port(), db.username(), db.password(), serverId, topic, schemas, 256);
    }
    private static void execute(String... statements) throws Exception {
        var db = DATABASE.metadata();
        try (var connection = DriverManager.getConnection(db.url(), db.username(), db.password());
                var sql = connection.createStatement()) {
            for (String statement : statements) sql.execute(statement);
        }
    }
}
