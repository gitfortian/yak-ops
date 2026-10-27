package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
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
    void snapshotStateRetainsPreparedOutputOnlyAfterCompletedCheckpoint() {
        MySqlSnapshotSplit split = new MySqlSnapshotSplit("shop.orders:0", "definition", TABLE, 0L, 100L, null);
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        assertInstanceOf(MySqlSnapshotSplitState.class, state);
        assertTrue(state.isSnapshotSplitState());
        assertFalse(state.isBinlogSplitState());
        assertFalse(state.binlog());
        assertEquals("definition", state.sourceId());
        assertEquals(split, state.toMySqlSplit());

        MySqlSnapshotOutput output = new MySqlSnapshotOutput(
                offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(42L))));
        state.prepareSnapshot(split.withOutput(output));
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(1));
        state.snapshotCheckpoint(10);
        assertNull(state.completeCheckpoint(9));
        assertEquals("shop.orders:0", state.completeCheckpoint(10));
        state.emitSnapshotRows(1);

        MySqlSnapshotSplit completed = state.asSnapshotSplitState().toMySqlSplit();
        assertTrue(completed.isSnapshotReadFinished());
        assertEquals(offset(200), completed.getHighWatermark());
        assertNull(completed.output());
        assertEquals(completed, state.checkpoint(null));
    }

    @Test
    void binlogStateNeverCheckpointsAnOffsetWithoutItsSchemaHistory() {
        MySqlBinlogSplit split = new MySqlBinlogSplit("definition", null, new byte[0]);
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        assertInstanceOf(MySqlBinlogSplitState.class, state);
        assertTrue(state.isBinlogSplitState());
        assertFalse(state.isSnapshotSplitState());
        assertTrue(state.binlog());
        assertEquals("definition", state.sourceId());
        assertThrows(IllegalStateException.class, () -> state.checkpoint(new byte[0]));

        state.emitBinlog(offset(300));
        assertThrows(IllegalStateException.class, () -> state.checkpoint(new byte[0]));
        MySqlBinlogSplit restored = (MySqlBinlogSplit) state.checkpoint(new byte[] {1, 2, 3});
        assertEquals(offset(300), restored.offset());
        assertEquals(split.splitId(), restored.splitId());
    }

    @Test
    void binlogBootstrapRequiresCompletedSnapshotBeforeStreaming() {
        MySqlHybridBinlogSplit split = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit("definition", null, new byte[0]),
                MySqlHybridBinlogSplit.Phase.BOOTSTRAP,
                null);
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        assertTrue(state.isBinlogSplitState());
        assertThrows(IllegalStateException.class, () -> state.emitBinlog(offset(200)));
        state.captureLow(offset(100), new byte[] {1, 2, 3});
        assertEquals(MySqlHybridBinlogSplit.Phase.PAUSED,
                ((MySqlHybridBinlogSplit) state.checkpoint(new byte[] {1, 2, 3})).phase());

        FinishedSnapshotSplitInfo info =
                new FinishedSnapshotSplitInfo(TABLE, "shop.orders:0", null, null, offset(200));
        state.resume(offset(200), List.of(info));
        state.emitBinlog(offset(201));
        MySqlHybridBinlogSplit streaming =
                assertInstanceOf(MySqlHybridBinlogSplit.class, state.checkpoint(new byte[] {1, 2, 3}));
        assertEquals(MySqlHybridBinlogSplit.Phase.STREAMING, streaming.phase());
        assertEquals(offset(201), streaming.binlog().offset());
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
