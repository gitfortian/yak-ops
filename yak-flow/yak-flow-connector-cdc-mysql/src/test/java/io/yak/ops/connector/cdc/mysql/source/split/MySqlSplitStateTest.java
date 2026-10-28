package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Split state dispatch and checkpoint ownership contracts, following Flink CDC's split state tests. */
class MySqlSplitStateTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void snapshotStatePublishesCompletedChunkOnlyAfterAtomicEmission() throws Exception {
        var split = new MySqlSnapshotSplit("chunk", TABLE, 0L, 100L, null);
        var state = MySqlSplitState.fromSplit(split);
        assertInstanceOf(MySqlSnapshotSplitState.class, state);
        assertTrue(state.isSnapshotSplitState());
        assertEquals(split, state.checkpoint(null));
        var prepared = split.withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(42L)))));
        state.prepareSnapshot(prepared);
        var completed = (MySqlSnapshotSplit) state.checkpoint(null);
        assertEquals(offset(200), completed.finished().highWatermark());
        assertNull(completed.output());
        assertEquals(completed, state.toMySqlSplit());
    }

    @Test
    void binlogStateNeverCheckpointsAnOffsetWithoutItsSchemaHistory() {
        MySqlBinlogSplit split = new MySqlBinlogSplit(null, new byte[0]);
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        assertInstanceOf(MySqlBinlogSplitState.class, state);
        assertTrue(state.isBinlogSplitState());
        assertFalse(state.isSnapshotSplitState());
        assertTrue(state.binlog());
        // An unresolved stream-only startup is a legitimate durable state: the
        // Debezium schema bootstrap must replay from scratch on recovery.
        assertEquals(split, state.checkpoint(new byte[0]));

        state.emitBinlog(offset(300));
        assertThrows(IllegalStateException.class, () -> state.checkpoint(new byte[0]));
        MySqlBinlogSplit restored = (MySqlBinlogSplit) state.checkpoint(new byte[] {1, 2, 3});
        assertEquals(offset(300), restored.offset());
        assertEquals(split.splitId(), restored.splitId());
    }

    @Test
    void binlogBootstrapRequiresCompletedSnapshotBeforeStreaming() {
        MySqlBinlogSplit split = MySqlBinlogSplit.forInitialSnapshot();
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        assertTrue(state.isBinlogSplitState());
        assertThrows(IllegalStateException.class, () -> state.emitBinlog(offset(200)));
        state.captureLow(offset(100), new byte[] {1, 2, 3});
        assertEquals(MySqlBinlogSplit.Phase.PAUSED,
                ((MySqlBinlogSplit) state.checkpoint(new byte[] {1, 2, 3})).phase());

        FinishedSnapshotSplitInfo info =
                new FinishedSnapshotSplitInfo(TABLE, "shop.orders:0", null, null, offset(200));
        state.resume(offset(200), List.of(info));
        state.emitBinlog(offset(201));
        MySqlBinlogSplit streaming =
                assertInstanceOf(MySqlBinlogSplit.class, state.checkpoint(new byte[] {1, 2, 3}));
        assertEquals(MySqlBinlogSplit.Phase.STREAMING, streaming.phase());
        assertEquals(offset(201), streaming.offset());
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
    @Test
    void exposesTypedSnapshotBoundaries() {
        ChunkRange range = new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 100L, 200L);
        MySqlSnapshotSplit split = MySqlSnapshotSplit.typed("orders:0", range);
        assertEquals(TABLE, split.getTableId());
        assertArrayEquals(new Object[] {100L}, split.getSplitStart());
        assertArrayEquals(new Object[] {200L}, split.getSplitEnd());
        assertFalse(split.isSnapshotReadFinished());
        assertNull(split.getHighWatermark());
        assertEquals(split, MySqlSnapshotSplit.typed("orders:0", range));
        assertEquals(split.hashCode(), MySqlSnapshotSplit.typed("orders:0", range).hashCode());
    }
}
