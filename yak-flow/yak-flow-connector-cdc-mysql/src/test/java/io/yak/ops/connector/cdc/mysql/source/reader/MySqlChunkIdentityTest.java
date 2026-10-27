package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
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

/** Chunk Key partitions work; the physical primary key identifies keyed row changes. */
class MySqlChunkIdentityTest {

    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("bucket", LogicalTypes.decimal(12, 2).copy(false)),
                    new Column("payload", LogicalTypes.varchar(100))),
            List.of("id"));

    @Test
    void keepsDistinctPrimaryKeysThatShareOneNonUniqueChunkKey() {
        var backfill = new MySqlChunkBackfill(range(null, null), SCHEMA, 100);
        backfill.addSnapshot(row(RowKind.INSERT, 1, "1.00", "one"));
        backfill.addSnapshot(row(RowKind.INSERT, 2, "1.00", "two"));
        backfill.apply(change(
                row(RowKind.UPDATE_BEFORE, 2, "1.00", "two"),
                row(RowKind.UPDATE_AFTER, 2, "1.00", "updated")));
        var result = backfill.finish();
        assertEquals(2, result.size());
        assertEquals(List.of(1L, 2L), result.stream().map(r -> r.row().getLong(0)).toList());
        assertEquals(List.of("one", "updated"), result.stream().map(r -> r.row().getString(2)).toList());
    }

    @Test
    void movesOnePhysicalRowBetweenChunksWithoutConflatingChunkKeyAndIdentity() {
        var first = new MySqlChunkBackfill(range(null, "10.00"), SCHEMA, 100);
        var second = new MySqlChunkBackfill(range("10.00", null), SCHEMA, 100);
        first.addSnapshot(row(RowKind.INSERT, 1, "1.00", "moving"));
        first.addSnapshot(row(RowKind.INSERT, 2, "1.00", "staying"));
        var move = change(
                row(RowKind.UPDATE_BEFORE, 1, "1.00", "moving"),
                row(RowKind.UPDATE_AFTER, 1, "20.00", "moving"));
        first.apply(move);
        second.apply(move);
        assertEquals(List.of(2L), first.finish().stream().map(r -> r.row().getLong(0)).toList());
        assertEquals(List.of(1L), second.finish().stream().map(r -> r.row().getLong(0)).toList());
    }

    private static ChunkRange range(String start, String end) {
        return new ChunkRange(TABLE, "bucket", LogicalTypeRoot.DECIMAL,
                start == null ? null : new BigDecimal(start),
                end == null ? null : new BigDecimal(end));
    }

    private static TableRecord row(RowKind kind, long id, String bucket, String payload) {
        return new TableRecord(TABLE, kind, GenericRowData.of(id, new BigDecimal(bucket), payload));
    }

    private static BinlogEvent change(TableRecord... rows) {
        return new BinlogEvent(List.of(rows),
                new DebeziumOffset(Map.of("server", "shop"),
                        Map.of("file", "mysql-bin.000001", "pos", 120L)));
    }
}
