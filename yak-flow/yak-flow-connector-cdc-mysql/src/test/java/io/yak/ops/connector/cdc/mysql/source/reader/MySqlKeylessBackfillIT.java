package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real MySQL no-PK snapshot, Debezium events, watermarks and frozen Checkpoint transport. */
@Tag("cdc")
@Timeout(180)
class MySqlKeylessBackfillIT {
    @RegisterExtension static final MySqlDatabase DATABASE = MySqlDatabase.cdc();

    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("payload", LogicalTypes.varchar(100))),
            List.of());

    @Test
    void reconcilesDuplicateRowsInTheLowHighWindowAndFreezesOneDurableChunk() throws Exception {
        String table = name("keyless_");
        String other = name("unrelated_");
        execute(
                "CREATE TABLE " + table + "(bucket BIGINT NOT NULL, payload VARCHAR(100))",
                "CREATE TABLE " + other + "(bucket BIGINT NOT NULL, payload VARCHAR(100))",
                "INSERT INTO " + table + " VALUES (1,'same'),(1,'same'),(1,'replace'),(20,'outside')");
        TableId id = new TableId("yak_cdc_it", null, table);
        TableId unrelated = new TableId("yak_cdc_it", null, other);
        try {
            var config = config(Map.of(id, SCHEMA, unrelated, SCHEMA), Map.of(id, "bucket", unrelated, "bucket"), 6731);
            var raw = MySqlSnapshotSplit.typed(
                    "keyless-chunk", "fingerprint",
                    new ChunkRange(id, "bucket", LogicalTypeRoot.BIGINT, null, 10L));
            try (var reader = new MySqlSnapshotBackfillReader(config, 0, () -> {
                execute(
                        "DELETE FROM " + table + " WHERE bucket=1 AND payload='same' LIMIT 1",
                        "UPDATE " + table + " SET payload='updated' WHERE bucket=1 AND payload='replace'",
                        "INSERT INTO " + table + " VALUES (1,'added')",
                        "INSERT INTO " + table + " VALUES (20,'not-this-chunk')",
                        "INSERT INTO " + other + " VALUES (1,'other-table')");
                return null;
            })) {
                MySqlSnapshotSplit prepared = reader.read(raw);
                assertEquals(raw.chunkRange(), prepared.chunkRange());
                assertEquals(0, prepared.outputPosition());
                assertTrue(prepared.output().high().compareTo(prepared.output().low()) > 0);
                var results = prepared.output().rows();
                assertEquals(3, results.size());
                assertEquals(1, results.stream().filter(r -> "same".equals(r.row().getString(1))).count());
                assertEquals(1, results.stream().filter(r -> "updated".equals(r.row().getString(1))).count());
                assertEquals(1, results.stream().filter(r -> "added".equals(r.row().getString(1))).count());
                assertTrue(results.stream().allMatch(r -> r.rowKind() == RowKind.INSERT));
                assertTrue(results.stream().allMatch(r -> r.row().getLong(0) == 1L));

                var serializer = new MySqlHybridSplitSerializer();
                var restored = (MySqlSnapshotSplit)
                        serializer.deserialize(serializer.getVersion(), serializer.serialize(prepared));
                assertEquals(prepared, restored);
                var state = new MySqlHybridSplitState(raw);
                state.prepareSnapshot(restored);
                assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(1));
                state.snapshotCheckpoint(41);
                assertEquals("keyless-chunk", state.completeCheckpoint(41));
                state.emitSnapshotRows(1);
                var checkpoint = (MySqlSnapshotSplit)
                        serializer.deserialize(serializer.getVersion(), serializer.serialize(state.checkpoint(null)));
                assertEquals(1, checkpoint.outputPosition());
                assertEquals(3, checkpoint.output().rows().size());
                var recovered = new MySqlHybridSplitState(checkpoint);
                recovered.emitSnapshotRows(3);
                var finished = (MySqlSnapshotSplit) recovered.checkpoint(null);
                assertEquals(raw.chunkRange(), finished.finished().chunkRange());
                assertEquals(prepared.output().high(), finished.finished().highWatermark());
            }
        } finally {
            execute("DROP TABLE " + table, "DROP TABLE " + other);
        }
    }

    @Test
    void preservesTwoIdenticalImagesWithoutAnyBinlogChanges() throws Exception {
        String table = name("quiet_keyless_");
        execute("CREATE TABLE " + table + "(bucket BIGINT NOT NULL, payload VARCHAR(100))",
                "INSERT INTO " + table + " VALUES (1,'duplicate'),(1,'duplicate')");
        TableId id = new TableId("yak_cdc_it", null, table);
        try (var reader = new MySqlSnapshotBackfillReader(config(Map.of(id, SCHEMA), Map.of(id, "bucket"), 6741), 0)) {
            var prepared = reader.read(MySqlSnapshotSplit.typed(
                    "quiet", "fp", new ChunkRange(id, "bucket", LogicalTypeRoot.BIGINT, null, null)));
            assertEquals(2, prepared.output().rows().size());
            assertEquals(0, prepared.output().low().compareTo(prepared.output().high()));
            assertEquals("duplicate", prepared.output().rows().getFirst().row().getString(1));
            assertEquals("duplicate", prepared.output().rows().getLast().row().getString(1));
        } finally {
            execute("DROP TABLE " + table);
        }
    }

    @Test
    void failsClosedIfTheChunkKeyChangesDuringSnapshot() throws Exception {
        String table = name("moving_keyless_");
        execute("CREATE TABLE " + table + "(bucket BIGINT NOT NULL, payload VARCHAR(100))",
                "INSERT INTO " + table + " VALUES (1,'original')");
        TableId id = new TableId("yak_cdc_it", null, table);
        try (var reader = new MySqlSnapshotBackfillReader(config(Map.of(id, SCHEMA), Map.of(id, "bucket"), 6751), 0,
                () -> {
                    execute("UPDATE " + table + " SET bucket=2 WHERE payload='original'");
                    return null;
                })) {
            var raw = MySqlSnapshotSplit.typed(
                    "move", "fp", new ChunkRange(id, "bucket", LogicalTypeRoot.BIGINT, null, 10L));
            var error = assertThrows(UnsupportedOperationException.class, () -> reader.read(raw));
            assertTrue(error.getMessage().contains("Chunk Key"));
        } finally {
            execute("DROP TABLE " + table);
        }
    }

    @Test
    void publicSourceStillCannotStartKeylessBinlogOrHybridExecution() {
        String table = name("closed_");
        TableId id = new TableId("yak_cdc_it", null, table);
        var config = config(Map.of(id, SCHEMA), Map.of(id, "bucket"), 6761);
        var source = MySqlCdcSource.builder()
                .hostname(DATABASE.metadata().host())
                .port(DATABASE.metadata().port())
                .username(DATABASE.metadata().username())
                .password(DATABASE.metadata().password())
                .serverId(6771)
                .topicPrefix("blocked-keyless")
                .table(id, SCHEMA, "bucket")
                .build();
        assertEquals(config.schemas(), source.config().schemas());
        assertThrows(UnsupportedOperationException.class, () -> source.createEnumerator(null));
        assertThrows(UnsupportedOperationException.class, () -> source.restoreEnumerator(null, null));
        assertThrows(IllegalArgumentException.class, () -> source.initialSnapshot(10));
    }

    private static MySqlCdcSourceConfig config(
            Map<TableId, TableSchema> schemas, Map<TableId, String> keys, int serverId) {
        var db = DATABASE.metadata();
        return new MySqlCdcSourceConfig(
                db.host(), db.port(), db.username(), db.password(), serverId,
                "keyless-backfill-" + serverId, schemas, 256, Map.of(), keys);
    }

    private static void execute(String... statements) throws Exception {
        var db = DATABASE.metadata();
        try (var connection = DriverManager.getConnection(db.url(), db.username(), db.password());
                var sql = connection.createStatement()) {
            for (String statement : statements) sql.execute(statement);
        }
    }

    private static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
