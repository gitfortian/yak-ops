package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotRows;
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

/** Keyless Backfill retains the full multiplicity and freezes only after ordered reconciliation. */
class MySqlKeylessBackfillTest {
    private static final TableId TABLE = new TableId("shop", null, "events");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("payload", LogicalTypes.varchar(80))),
            List.of());
    private static final ChunkRange RANGE = new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, 10L);

    @Test
    void retainsDuplicateImagesAcrossDeleteUpdateAndInsert() {
        var buffer = new MySqlChunkBackfill(RANGE, SCHEMA, 10);
        buffer.addSnapshot(row(RowKind.INSERT, 1, "same"));
        buffer.addSnapshot(row(RowKind.INSERT, 1, "same"));
        buffer.addSnapshot(row(RowKind.INSERT, 1, "same"));
        buffer.addSnapshot(row(RowKind.INSERT, 2, "other"));
        buffer.apply(event(row(RowKind.DELETE, 1, "same")));
        buffer.apply(event(row(RowKind.UPDATE_BEFORE, 1, "same"), row(RowKind.UPDATE_AFTER, 1, "edited")));
        buffer.apply(event(row(RowKind.INSERT, 1, "same")));
        buffer.apply(event(row(RowKind.INSERT, 20, "outside")));
        var values = buffer.finish().stream().map(x -> x.row().getString(1)).toList();
        assertEquals(4, values.size());
        assertEquals(2, values.stream().filter("same"::equals).count());
        assertTrue(values.containsAll(List.of("edited", "other")));
        assertThrows(IllegalStateException.class, buffer::finish);
    }

    @Test
    void failsClosedOnChunkKeyMutationWithoutPartiallyApplyingTheUpdate() {
        var buffer = new MySqlChunkBackfill(RANGE, SCHEMA, 10);
        buffer.addSnapshot(row(RowKind.INSERT, 1, "before"));
        assertThrows(UnsupportedOperationException.class,
                () -> buffer.apply(event(
                        row(RowKind.UPDATE_BEFORE, 1, "before"),
                        row(RowKind.UPDATE_AFTER, 3, "after"))));
        assertEquals("before", buffer.finish().getFirst().row().getString(1));
    }

    @Test
    void handlesDecimalRangesBeyondSignedLongWithoutLosingDuplicates() {
        var schema = new TableSchema(
                List.of(
                        new Column("bucket", LogicalTypes.decimal(30, 8).copy(false)),
                        new Column("payload", LogicalTypes.varchar(80))),
                List.of());
        var edge = new BigDecimal("9223372036854775808.00000000");
        var range = new ChunkRange(TABLE, "bucket", LogicalTypeRoot.DECIMAL, edge, null);
        var buffer = new MySqlChunkBackfill(range, schema, 10);
        buffer.addSnapshot(decimal(RowKind.INSERT, edge, "twice"));
        buffer.addSnapshot(decimal(RowKind.INSERT, edge, "twice"));
        buffer.apply(event(decimal(RowKind.DELETE, edge, "twice")));
        assertEquals(1, buffer.finish().size());
    }

    @Test
    void failsClosedOnPayloadBudgetAndDoesNotExposePartialResults() {
        var buffer = new MySqlChunkBackfill(RANGE, SCHEMA, 2, 1200);
        buffer.addSnapshot(row(RowKind.INSERT, 1, "one"));
        buffer.addSnapshot(row(RowKind.INSERT, 1, "two"));
        assertThrows(IllegalStateException.class, () -> buffer.addSnapshot(row(RowKind.INSERT, 1, "three")));
        assertThrows(IllegalStateException.class, buffer::finish);
    }

    @Test
    void freezesMoreThanOneHundredThousandIdenticalImagesWithinTheBoundedCheckpoint() throws Exception {
        var buffer = new MySqlChunkBackfill(RANGE, SCHEMA, 2, 4096);
        for (int i = 0; i < 200_000; i++) {
            buffer.addSnapshot(row(RowKind.INSERT, 1, "same"));
        }
        buffer.apply(event(row(RowKind.DELETE, 1, "same")));
        buffer.apply(event(
                row(RowKind.UPDATE_BEFORE, 1, "same"),
                row(RowKind.UPDATE_AFTER, 1, "edited")));
        buffer.apply(event(row(RowKind.INSERT, 1, "same")));

        var output = new MySqlSnapshotOutput(offset(100), offset(200), buffer.finish());
        assertEquals(200_000, output.rows().size());
        assertTrue(output.rows() instanceof MySqlSnapshotRows);
        assertEquals(2, ((MySqlSnapshotRows) output.rows()).runs().size());
        assertEquals("same", output.rows().getFirst().row().getString(1));
        assertEquals("edited", output.rows().getLast().row().getString(1));

        var serializer = new MySqlHybridSplitSerializer();
        var frozen = MySqlSnapshotSplit.typed("large-group", "fp", RANGE).withOutput(output);
        byte[] bytes = serializer.serialize(frozen);
        assertTrue(bytes.length < 4096, "Same-row multiplicity must not inflate the split checkpoint");
        var restored = (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), bytes);
        assertEquals(200_000, restored.output().rows().size());
        assertEquals("edited", restored.output().rows().getLast().row().getString(1));

        var partiallyEmitted = restored.withOutputPosition(120_000);
        var resumed = (MySqlSnapshotSplit)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(partiallyEmitted));
        assertEquals(120_000, resumed.outputPosition());
        assertEquals("same", resumed.output().rows().get(120_000).row().getString(1));
        assertEquals("edited", resumed.output().rows().getLast().row().getString(1));
    }

    @Test
    void rejectsInvalidUpdateGroupsOrKeysOutsideTheFrozenRange() {
        var buffer = new MySqlChunkBackfill(RANGE, SCHEMA, 10);
        assertThrows(IllegalArgumentException.class, () -> buffer.addSnapshot(row(RowKind.INSERT, 10, "outside")));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.apply(event(row(RowKind.UPDATE_BEFORE, 1, "partial"))));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.apply(event(row(RowKind.INSERT, 1, "a"), row(RowKind.INSERT, 1, "b"))));
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }

    private static TableRecord row(RowKind kind, long bucket, String payload) {
        return new TableRecord(TABLE, kind, GenericRowData.of(bucket, payload));
    }

    private static TableRecord decimal(RowKind kind, BigDecimal bucket, String payload) {
        return new TableRecord(TABLE, kind, GenericRowData.of(bucket, payload));
    }

    private static BinlogEvent event(TableRecord... rows) {
        return new BinlogEvent(
                List.of(rows),
                new BinlogOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", 200L)));
    }
}
