package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/** Characterizes full-row multiset reconciliation without enabling keyless hybrid execution. */
class MySqlKeylessBackfillTest {
    private static final TableId TABLE = new TableId("shop", null, "entries");
    private static final TableSchema KEYLESS = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(40)),
                    new Column("amount", LogicalTypes.decimal(12, 2)),
                    new Column("payload", LogicalTypes.varbinary(64))),
            List.of());

    @Test
    void retainsIdenticalRowsAndDeletesExactlyOneImage() {
        var buffer = keyless(null, null, 10, 64 * 1024);
        buffer.addSnapshot(row(RowKind.INSERT, 7, "same", "1.00", new byte[] {1, 2}));
        buffer.addSnapshot(row(RowKind.INSERT, 7, "same", "1.00", new byte[] {1, 2}));
        buffer.addSnapshot(row(RowKind.INSERT, 7, "other", "2.00", new byte[] {3}));
        buffer.apply(change(row(RowKind.DELETE, 7, "same", "1.0", new byte[] {1, 2})));
        List<TableRecord> result = buffer.finish();
        assertEquals(2, result.size());
        assertEquals(List.of("same", "other"), result.stream().map(x -> x.row().getString(1)).toList());
    }

    @Test
    void updatesWithdrawBeforeImageAndRetainAfterImageWithoutUniqueKey() {
        var buffer = keyless(null, null, 10, 64 * 1024);
        buffer.addSnapshot(row(RowKind.INSERT, 5, "old", "1.00", new byte[] {4}));
        buffer.addSnapshot(row(RowKind.INSERT, 5, "old", "1.00", new byte[] {4}));
        buffer.apply(change(
                row(RowKind.UPDATE_BEFORE, 5, "old", "1.00", new byte[] {4}),
                row(RowKind.UPDATE_AFTER, 5, "new", "1.00", new byte[] {4})));
        buffer.apply(change(row(RowKind.INSERT, 5, "inserted", "5.00", new byte[] {5})));
        List<String> names = buffer.finish().stream().map(x -> x.row().getString(1)).sorted().toList();
        assertEquals(List.of("inserted", "new", "old"), names);
    }

    @Test
    void appliesEachHalfOfCrossChunkChangeToItsOwnRange() {
        var first = keyless(null, 10L, 10, 64 * 1024);
        var second = keyless(10L, null, 10, 64 * 1024);
        var old = row(RowKind.UPDATE_BEFORE, 5, "moving", "3.00", new byte[] {1});
        var next = row(RowKind.UPDATE_AFTER, 15, "moved", "3.00", new byte[] {1});
        first.addSnapshot(row(RowKind.INSERT, 5, "moving", "3.00", new byte[] {1}));
        BinlogEvent move = change(old, next);
        first.apply(move);
        second.apply(move);
        assertEquals(0, first.finish().size());
        List<TableRecord> moved = second.finish();
        assertEquals(1, moved.size());
        assertEquals(15L, moved.getFirst().row().getLong(0));
        assertEquals("moved", moved.getFirst().row().getString(1));
    }

    @Test
    void detachesBinaryValuesFromMutableInputArrays() {
        var buffer = keyless(null, null, 10, 64 * 1024);
        byte[] source = new byte[] {3, 4, 5};
        buffer.addSnapshot(row(RowKind.INSERT, 1, "image", "1.00", source));
        source[0] = 99;
        assertArrayEquals(new byte[] {3, 4, 5}, (byte[]) buffer.finish().getFirst().row().getField(3));
    }

    @Test
    void failsClosedWhenRowOrByteBudgetIsExceeded() {
        var rows = keyless(null, null, 1, 64 * 1024);
        rows.addSnapshot(row(RowKind.INSERT, 1, "one", "1.00", new byte[] {1}));
        assertThrows(IllegalStateException.class,
                () -> rows.addSnapshot(row(RowKind.INSERT, 1, "one", "1.00", new byte[] {1})));
        assertThrows(IllegalStateException.class, rows::finish);

        var bytes = keyless(null, null, 100, 800);
        bytes.addSnapshot(row(RowKind.INSERT, 1, "small", "1.00", new byte[] {1}));
        assertThrows(IllegalStateException.class,
                () -> bytes.apply(change(row(RowKind.INSERT, 1, "huge", "1.00", new byte[400]))));
        assertThrows(IllegalStateException.class, bytes::finish);
    }

    @Test
    void rejectsIncompleteUpdatesInvalidSchemasAndDatabaseDependentBoundedStrings() {
        var buffer = keyless(null, null, 10, 64 * 1024);
        assertThrows(IllegalArgumentException.class,
                () -> buffer.apply(change(row(RowKind.UPDATE_AFTER, 1, "after", "1.00", new byte[] {1}))));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.addSnapshot(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, "short"))));
        assertEquals(0, buffer.finish().size());

        var stringSchema = new TableSchema(
                List.of(new Column("code", LogicalTypes.varchar(20).copy(false))), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new MySqlChunkBackfill(
                        new ChunkRange(TABLE, "code", LogicalTypeRoot.VARCHAR, "a", "z"), stringSchema, 10));
        var physicalKey = new TableSchema(KEYLESS.columns(), List.of("bucket"));
        assertThrows(IllegalArgumentException.class,
                () -> new MySqlChunkBackfill(range(null, null), physicalKey, 10));
    }

    @Test
    void frozenSnapshotOutputPreservesKeylessMultiplicityAfterSerialization() throws Exception {
        var buffer = keyless(null, null, 10, 64 * 1024);
        buffer.addSnapshot(row(RowKind.INSERT, 3, "same", "1.00", new byte[] {1}));
        buffer.addSnapshot(row(RowKind.INSERT, 3, "same", "1.00", new byte[] {1}));
        var split = new MySqlSnapshotSplit("chunk", "frozen", TABLE, null, null, null)
                .withOutput(new MySqlSnapshotOutput(offset(10), offset(20), buffer.finish()));
        var serializer = new MySqlHybridSplitSerializer();
        var restored = (MySqlSnapshotSplit)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(split));
        assertEquals(2, restored.output().rows().size());
        assertEquals("same", restored.output().rows().getFirst().row().getString(1));
        assertEquals("same", restored.output().rows().getLast().row().getString(1));
    }

    private static MySqlChunkBackfill keyless(Long lower, Long upper, int rows, long bytes) {
        return new MySqlChunkBackfill(range(lower, upper), KEYLESS, rows, bytes);
    }

    private static ChunkRange range(Long lower, Long upper) {
        return new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, lower, upper);
    }

    private static TableRecord row(RowKind kind, long bucket, String name, String amount, byte[] payload) {
        return new TableRecord(
                TABLE, kind, GenericRowData.of(bucket, name, new BigDecimal(amount), payload));
    }

    private static BinlogEvent change(TableRecord... changes) {
        return new BinlogEvent(List.of(changes), offset(100));
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
