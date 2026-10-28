package io.yak.ops.connector.cdc.mysql.debezium.task;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.debezium.reader.SnapshotChunkBackfill;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flink CDC-style LOW / snapshot / HIGH / bounded Binlog backfill contracts. */
class MySqlSnapshotSplitReadTaskTest {

    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableId OTHER_TABLE = new TableId("shop", null, "items");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of("id"));

    @Test
    void replaysOnlyLowExclusiveHighInclusiveThenStopsAtBinlogEnd() {
        SnapshotChunkBackfill buffer = buffer(null, null);
        buffer.addSnapshot(row(TABLE, RowKind.INSERT, 1L, "snapshot"));

        assertThat(replay(buffer, 100, 200, event(100, row(TABLE, RowKind.INSERT, 1L, "low"))))
                .isFalse();
        assertThat(replay(buffer, 100, 200, event(150,
                row(TABLE, RowKind.UPDATE_BEFORE, 1L, "snapshot"),
                row(TABLE, RowKind.UPDATE_AFTER, 1L, "updated")))).isFalse();
        assertThat(replay(buffer, 100, 200, event(160,
                row(OTHER_TABLE, RowKind.INSERT, 99L, "unrelated")))).isFalse();
        assertThat(replay(buffer, 100, 200, event(200,
                row(TABLE, RowKind.INSERT, 2L, "at high")))).isTrue();
        assertThat(replay(buffer, 100, 200, event(201,
                row(TABLE, RowKind.DELETE, 1L, "after high")))).isTrue();

        assertThat(buffer.finish())
                .extracting(record -> record.row().getField(1))
                .containsExactlyInAnyOrder("updated", "at high");
    }

    @Test
    void rejectsMixedTableUpdatesWithoutDroppingCurrentTableChange() {
        SnapshotChunkBackfill buffer = buffer(null, null);
        BinlogEvent corrupt = event(150,
                row(OTHER_TABLE, RowKind.INSERT, 99L, "other"),
                row(TABLE, RowKind.INSERT, 1L, "ours"));
        assertThatThrownBy(() -> replay(buffer, 100, 200, corrupt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the current Snapshot table");
    }

    @Test
    void usesSameNumericChunkBoundsForSnapshotAndBackfill() {
        // Values beyond signed BIGINT must never be narrowed to longValue().
        BigInteger from = BigInteger.valueOf(Long.MAX_VALUE - 1L);
        BigInteger inside = new BigInteger("9223372036854775808");
        BigInteger to = new BigInteger("9223372036854775810");
        SnapshotChunkBackfill buffer = buffer(from, to);
        buffer.addSnapshot(row(TABLE, RowKind.INSERT, inside, "unsigned snapshot"));
        buffer.addSnapshot(row(TABLE, RowKind.INSERT, from, "at low"));
        assertThat(replay(buffer, 100, 200, event(150,
                row(TABLE, RowKind.UPDATE_BEFORE, inside, "unsigned snapshot"),
                row(TABLE, RowKind.UPDATE_AFTER, inside, "unsigned updated"))))
                .isFalse();
        assertThat(replay(buffer, 100, 200, event(160,
                row(TABLE, RowKind.INSERT, to, "outside"))))
                .isFalse();

        assertThat(buffer.finish())
                .extracting(record -> record.row().getField(1))
                .containsExactlyInAnyOrder("unsigned updated", "at low");
    }

    @Test
    void appliesCrossChunkPrimaryKeyMoveToOnlyTheMatchingHalf() {
        SnapshotChunkBackfill buffer = buffer(10L, 20L);
        buffer.addSnapshot(row(TABLE, RowKind.INSERT, 12L, "old"));
        assertThat(replay(buffer, 100, 200, event(150,
                row(TABLE, RowKind.UPDATE_BEFORE, 12L, "old"),
                row(TABLE, RowKind.UPDATE_AFTER, 22L, "new")))).isFalse();
        assertThat(replay(buffer, 100, 200, event(160,
                row(TABLE, RowKind.UPDATE_BEFORE, 8L, "old"),
                row(TABLE, RowKind.UPDATE_AFTER, 13L, "moved in")))).isFalse();
        assertThat(buffer.finish())
                .extracting(record -> record.row().getField(0))
                .containsExactly(13L);
    }

    private static boolean replay(
            SnapshotChunkBackfill buffer, long low, long high, BinlogEvent event) {
        return MySqlSnapshotSplitReadTask.backfillEvent(
                event, offset(low), offset(high), TABLE, buffer);
    }

    private static SnapshotChunkBackfill buffer(Object start, Object end) {
        return new SnapshotChunkBackfill(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, start, end),
                SCHEMA, 100, 1024 * 1024);
    }

    private static BinlogEvent event(long position, TableRecord... records) {
        return new BinlogEvent(List.of(records), offset(position));
    }

    private static TableRecord row(TableId table, RowKind kind, Object id, String name) {
        return new TableRecord(table, kind, GenericRowData.of(id, name));
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"),
                Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
