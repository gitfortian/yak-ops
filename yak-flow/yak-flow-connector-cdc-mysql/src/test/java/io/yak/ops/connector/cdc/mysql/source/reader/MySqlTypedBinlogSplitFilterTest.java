package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlBinlogSplitFilter.FinishedChunk;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Proves chunk-key Binlog fencing without enabling uncheckpointed typed source execution. */
class MySqlTypedBinlogSplitFilterTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema KEYLESS = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of());

    @Test
    void filtersNonUniqueKeylessChunksAtIndividualHighWatermarks() {
        var filter = numericFilter();
        assertEquals(0, filter.filter(event(200, row(RowKind.INSERT, 5, "duplicate"))).records().size());
        assertEquals(1, filter.filter(event(201, row(RowKind.INSERT, 5, "duplicate"))).records().size());
        assertEquals(0, filter.filter(event(201, row(RowKind.INSERT, 10, "duplicate"))).records().size());
        assertEquals(0, filter.filter(event(300, row(RowKind.DELETE, 10, "duplicate"))).records().size());
        assertEquals(1, filter.filter(event(301, row(RowKind.DELETE, 10, "duplicate"))).records().size());
        assertEquals(1, filter.filter(event(350, row(RowKind.INSERT, Long.MIN_VALUE, "min")))
                .records().size());
        assertEquals(1, filter.filter(event(350, row(RowKind.INSERT, Long.MAX_VALUE, "max")))
                .records().size());
        assertEquals(event(201).offset(), filter.filter(event(201, row(RowKind.INSERT, 10, "covered")))
                .offset());
    }

    @Test
    void rejectsKeylessChunkKeyMovesAndKeepsSameKeyUpdatesAtomic() {
        var filter = numericFilter();
        for (long position : List.of(150L, 250L, 350L)) {
            assertThrows(UnsupportedOperationException.class, () -> filter.filter(event(
                    position, row(RowKind.UPDATE_BEFORE, 5, "old"), row(RowKind.UPDATE_AFTER, 15, "new"))));
        }
        assertEquals(List.of(RowKind.UPDATE_BEFORE, RowKind.UPDATE_AFTER), filter.filter(event(
                250, row(RowKind.UPDATE_BEFORE, 5, "old"), row(RowKind.UPDATE_AFTER, 5, "new")))
                .records().stream().map(TableRecord::rowKind).toList());
        assertEquals(0, filter.filter(event(
                150, row(RowKind.UPDATE_BEFORE, 5, "old"), row(RowKind.UPDATE_AFTER, 5, "new")))
                .records().size());
    }

    @Test
    void matchesWideDecimalChunkKeysWithoutConvertingToLong() {
        var schema = new TableSchema(
                List.of(
                        new Column("amount", LogicalTypes.decimal(30, 8).copy(false)),
                        new Column("name", LogicalTypes.varchar(100))),
                List.of());
        BigDecimal edge = new BigDecimal("9223372036854775808.00000000");
        var first = new FinishedChunk("first", new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL, null, edge),
                offset(150));
        var second = new FinishedChunk("second", new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL, edge, null),
                offset(300));
        var filter = MySqlBinlogSplitFilter.forChunks(Map.of(TABLE, schema), List.of(second, first));
        assertEquals(1, filter.filter(event(200, decimal("1.00"))).records().size());
        assertEquals(0, filter.filter(event(200, decimal("9223372036854775808.00000000"))).records().size());
        assertEquals(1, filter.filter(event(301, decimal("9223372036854775808.0"))).records().size());
    }

    @Test
    void supportsCompositePhysicalPrimaryKeysWithAnIndependentNumericChunkColumn() {
        var schema = new TableSchema(
                List.of(
                        new Column("tenant", LogicalTypes.BIGINT.copy(false)),
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("bucket", LogicalTypes.INTEGER.copy(false))),
                List.of("tenant", "id"));
        var one = new FinishedChunk(
                "all", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.INTEGER, null, null), offset(100));
        var filter = MySqlBinlogSplitFilter.forChunks(Map.of(TABLE, schema), List.of(one));
        var record = new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, 2L, 5));
        assertEquals(1, filter.filter(event(101, record)).records().size());
        assertEquals(0, filter.filter(event(100, record)).records().size());
    }

    @Test
    void rejectsBoundedStringsInsteadOfUsingJavaStringCollation() {
        var schema = new TableSchema(
                List.of(new Column("code", LogicalTypes.varchar(40).copy(false))), List.of());
        var bounded = new FinishedChunk(
                "first", new ChunkRange(TABLE, "code", LogicalTypeRoot.VARCHAR, null, "z"), offset(100));
        var last = new FinishedChunk(
                "last", new ChunkRange(TABLE, "code", LogicalTypeRoot.VARCHAR, "z", null), offset(200));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlBinlogSplitFilter.forChunks(Map.of(TABLE, schema), List.of(bounded, last)));
        var full = MySqlBinlogSplitFilter.forChunks(
                Map.of(TABLE, schema),
                List.of(new FinishedChunk(
                        "full", new ChunkRange(TABLE, "code", LogicalTypeRoot.VARCHAR, null, null), offset(100))));
        var row = new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of("İ"));
        assertEquals(1, full.filter(event(101, row)).records().size());
        assertEquals(0, full.filter(event(100, row)).records().size());
    }

    @Test
    void rejectsGapsOverlapsMixedChunkKeysAndMalformedUpdatePairs() {
        var first = new FinishedChunk(
                "first", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, 10L), offset(100));
        var gap = new FinishedChunk(
                "gap", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, 11L, null), offset(200));
        var overlap = new FinishedChunk(
                "overlap", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, 9L, null), offset(200));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlBinlogSplitFilter.forChunks(Map.of(TABLE, KEYLESS), List.of(first, gap)));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlBinlogSplitFilter.forChunks(Map.of(TABLE, KEYLESS), List.of(first, overlap)));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlBinlogSplitFilter.forChunks(Map.of(TABLE, KEYLESS), List.of(first, first)));
        var wrong = new FinishedChunk(
                "other", new ChunkRange(TABLE, "name", LogicalTypeRoot.VARCHAR, null, null), offset(200));
        assertThrows(IllegalArgumentException.class,
                () -> MySqlBinlogSplitFilter.forChunks(Map.of(TABLE, KEYLESS), List.of(first, wrong)));

        var filter = numericFilter();
        assertThrows(IllegalArgumentException.class,
                () -> filter.filter(event(350, row(RowKind.UPDATE_BEFORE, 5, "partial"))));
        assertThrows(IllegalArgumentException.class,
                () -> filter.filter(event(350, row(RowKind.UPDATE_AFTER, 5, "partial"))));
        assertThrows(IllegalArgumentException.class,
                () -> filter.filter(event(350, new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(null, "missing")))));
        assertThrows(IllegalArgumentException.class,
                () -> filter.filter(event(350, new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(5L)))));
    }

    @Test
    void rejectsKeylessSchemaChangesBeforeChangingItsActiveColumnMetadata() {
        var filter = numericFilter();
        Schema original = new Schema(
                List.of(
                        new PhysicalColumn("bucket", LogicalTypes.BIGINT.copy(false)),
                        new PhysicalColumn("name", LogicalTypes.varchar(100))),
                List.of());
        Schema evolved = new Schema(
                List.of(
                        new PhysicalColumn("name", LogicalTypes.varchar(100)),
                        new PhysicalColumn("bucket", LogicalTypes.BIGINT.copy(false))),
                List.of());
        var ddl = new BinlogEvent(
                List.of(), offset(350), new SourceSchemaChange("ddl-1", TABLE, original, evolved));
        assertThrows(UnsupportedOperationException.class, () -> filter.filter(ddl));
        assertEquals(1, filter.filter(event(
                        350, new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(5L, "unchanged"))))
                .records().size());
        Schema removed = new Schema(
                List.of(new PhysicalColumn("name", LogicalTypes.varchar(100))), List.of());
        assertThrows(UnsupportedOperationException.class,
                () -> filter.filter(new BinlogEvent(
                        List.of(), offset(351), new SourceSchemaChange("ddl-2", TABLE, evolved, removed))));
        assertEquals(1, filter.filter(event(
                        351, new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(5L, "unchanged"))))
                .records().size());
    }

    @Test
    void checkpointedTypedHandoffUsesEachChunkHighAndPreservesResumeOffsets() {
        var finished = List.of(
                new FinishedSnapshotSplitInfo(
                        TABLE, "first", null, null, offset(200),
                        new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, 10L)),
                new FinishedSnapshotSplitInfo(
                        TABLE, "second", null, null, offset(300),
                        new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, 10L, null)));
        var filter = MySqlBinlogSplitFilter.fromFinishedSplits(Map.of(TABLE, KEYLESS), finished);
        assertEquals(0, filter.filter(event(250, row(RowKind.INSERT, 10, "covered"))).records().size());
        assertEquals(1, filter.filter(event(250, row(RowKind.INSERT, 5, "new"))).records().size());
        assertEquals(offset(250), filter.filter(event(250, row(RowKind.INSERT, 10, "covered"))).offset());

        var anchored = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit("binlog", offset(100), new byte[] {1}),
                MySqlHybridBinlogSplit.Phase.PAUSED, null);
        var streaming = anchored.startStreaming(offset(300), finished);
        assertEquals(offset(200), streaming.binlog().offset());
        assertEquals(offset(300), streaming.highWatermark());
        assertEquals(finished, streaming.finishedSplits());
    }

    @Test
    void mixedKeyedAndKeylessTablesUseCheckpointRangesWithoutLosingLegacyFences() {
        var keyedTable = new TableId("shop", null, "keyed");
        var keyedSchema = new TableSchema(KEYLESS.columns(), List.of("bucket"));
        var finished = List.of(
                new FinishedSnapshotSplitInfo(
                        TABLE, "typed", null, null, offset(200),
                        new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, null)),
                new FinishedSnapshotSplitInfo(keyedTable, "legacy", null, null, offset(300)));
        var filter = MySqlBinlogSplitFilter.fromFinishedSplits(
                Map.of(TABLE, KEYLESS, keyedTable, keyedSchema), finished);
        assertEquals(0, filter.filter(event(200, row(RowKind.INSERT, 5, "duplicate"))).records().size());
        assertEquals(1, filter.filter(event(201, row(RowKind.INSERT, 5, "duplicate"))).records().size());
        var keyedRow = new TableRecord(keyedTable, RowKind.INSERT, GenericRowData.of(5L, "keyed"));
        assertEquals(0, filter.filter(event(250, keyedRow)).records().size());
        assertEquals(1, filter.filter(event(301, keyedRow)).records().size());
    }

    private static MySqlBinlogSplitFilter numericFilter() {
        return MySqlBinlogSplitFilter.forChunks(
                Map.of(TABLE, KEYLESS),
                List.of(
                        new FinishedChunk(
                                "second", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, 10L, null),
                                offset(300)),
                        new FinishedChunk(
                                "first", new ChunkRange(TABLE, "bucket", LogicalTypeRoot.BIGINT, null, 10L),
                                offset(200))));
    }

    private static TableRecord row(RowKind kind, long bucket, String name) {
        return new TableRecord(TABLE, kind, GenericRowData.of(bucket, name));
    }

    private static TableRecord decimal(String amount) {
        return new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(new BigDecimal(amount), "money"));
    }

    private static BinlogEvent event(long position, TableRecord... changes) {
        return new BinlogEvent(List.of(changes), offset(position));
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
