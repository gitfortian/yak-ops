package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A typed Snapshot Chunk Key is not necessarily the row's primary key. */
class MySqlTypedKeyedBackfillTest {
    private static final TableId TABLE = new TableId("shop", null, "events");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("tenant", LogicalTypes.INTEGER.copy(false)),
                    new Column("id", LogicalTypes.INTEGER.copy(false)),
                    new Column("bucket", LogicalTypes.INTEGER.copy(false)),
                    new Column("payload", LogicalTypes.varchar(40))),
            List.of("tenant", "id"));

    @Test
    void compositePrimaryKeyReconcilesUpdateUsingIdentityRatherThanChunkKey() {
        var range = new ChunkRange(TABLE, "bucket", LogicalTypeRoot.INTEGER, null, 3L);
        var buffer = new MySqlChunkBackfill(range, SCHEMA, 10);
        buffer.addSnapshot(row(RowKind.INSERT, 7, 1, 1, "old"));
        buffer.addSnapshot(row(RowKind.INSERT, 7, 2, 1, "different"));
        buffer.apply(event(row(RowKind.UPDATE_BEFORE, 7, 1, 1, "old"),
                row(RowKind.UPDATE_AFTER, 7, 1, 1, "new")));
        assertEquals(List.of("different", "new"),
                buffer.finish().stream().map(r -> r.row().getString(3)).sorted().toList());
    }

    @Test
    void keyedRowMovesBetweenChunkBoundariesWithoutMakingDuplicateOutput() {
        var left = new MySqlChunkBackfill(
                new ChunkRange(TABLE, "bucket", LogicalTypeRoot.INTEGER, null, 3L), SCHEMA, 10);
        var right = new MySqlChunkBackfill(
                new ChunkRange(TABLE, "bucket", LogicalTypeRoot.INTEGER, 3L, null), SCHEMA, 10);
        left.addSnapshot(row(RowKind.INSERT, 7, 1, 1, "old"));
        right.addSnapshot(row(RowKind.INSERT, 7, 1, 4, "new"));
        var update = event(row(RowKind.UPDATE_BEFORE, 7, 1, 1, "old"),
                row(RowKind.UPDATE_AFTER, 7, 1, 4, "new"));
        left.apply(update);
        right.apply(update);
        assertEquals(0, left.finish().size());
        assertEquals(1, right.finish().size());
    }

    @Test
    void decimalPrimaryKeyBeyondLongRetainsSnapshotAcrossCheckpointReplay() throws Exception {
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.decimal(30, 8).copy(false)),
                        new Column("payload", LogicalTypes.varchar(40))),
                List.of("id"));
        BigDecimal key = new BigDecimal("9223372036854775808.00000000");
        var range = new ChunkRange(TABLE, "id", LogicalTypeRoot.DECIMAL, key, null);
        var buffer = new MySqlChunkBackfill(range, schema, 10);
        buffer.addSnapshot(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(key, "old")));
        buffer.apply(event(
                new TableRecord(TABLE, RowKind.UPDATE_BEFORE, GenericRowData.of(key, "old")),
                new TableRecord(TABLE, RowKind.UPDATE_AFTER,
                        GenericRowData.of(new BigDecimal("9223372036854775808.0"), "new"))));
        var frozen = MySqlSnapshotSplit.typed("typed-decimal", "fingerprint", range)
                .withOutput(new MySqlSnapshotOutput(offset(100), offset(200), buffer.finish()));
        var serializer = new MySqlHybridSplitSerializer();
        var restored = (MySqlSnapshotSplit)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(frozen));
        assertEquals(1, restored.output().rows().size());
        assertEquals("new", restored.output().rows().getFirst().row().getString(1));
        assertEquals(range, restored.withOutputPosition(1).finished().chunkRange());
    }

    @Test
    void failClosedWhenKeyedBeforeImageHasNullPhysicalKey() {
        var buffer = new MySqlChunkBackfill(
                new ChunkRange(TABLE, "bucket", LogicalTypeRoot.INTEGER, null, null), SCHEMA, 10);
        buffer.addSnapshot(row(RowKind.INSERT, 7, 1, 1, "old"));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.apply(event(
                        new TableRecord(TABLE, RowKind.UPDATE_BEFORE, GenericRowData.of(7, null, 1, "old")),
                        row(RowKind.UPDATE_AFTER, 7, 1, 1, "new"))));
        assertEquals("old", buffer.finish().getFirst().row().getString(3));
    }

    private static TableRecord row(RowKind kind, int tenant, int id, int bucket, String payload) {
        return new TableRecord(TABLE, kind, GenericRowData.of(tenant, id, bucket, payload));
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }

    private static BinlogEvent event(TableRecord... rows) {
        return new BinlogEvent(List.of(rows), offset(150));
    }
}
