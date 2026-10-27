package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Initial snapshot and standalone streaming share one binlog split class. */
class MySqlBinlogSplitTest {

    @Test
    void suspendsAndResumesInitialBinlogWithFinishedSplitWatermarks() throws Exception {
        byte[] history = {1, 2, 3};
        var bootstrap = MySqlBinlogSplit.forInitialSnapshot();
        assertTrue(bootstrap.isSuspended());
        assertEquals(MySqlBinlogSplit.Phase.BOOTSTRAP, bootstrap.phase());
        var paused = bootstrap.withAnchor(offset(100), history);
        history[0] = 99;
        assertEquals(MySqlBinlogSplit.Phase.PAUSED, paused.phase());
        assertArrayEquals(new byte[] {1, 2, 3}, paused.schemaHistory());
        assertThrows(IllegalStateException.class, () -> paused.withEmittedOffset(offset(110), new byte[] {1}));
        var completed = List.of(
                new FinishedSnapshotSplitInfo(TABLE, "chunk-1", null, 10L, offset(200)),
                new FinishedSnapshotSplitInfo(TABLE, "chunk-2", 10L, null, offset(300)));
        var running = paused.startStreaming(offset(300), completed);
        assertFalse(running.isSuspended());
        assertTrue(running.isCompletedSplit());
        assertEquals(offset(200), running.getStartingOffset());
        assertEquals(offset(300), running.getHighWatermark());
        assertEquals(completed, running.getFinishedSnapshotSplitInfos());
        var progressed = running.withEmittedOffset(offset(400), new byte[] {4, 5});
        assertNotSame(running, progressed);
        assertEquals(offset(200), running.offset());
        assertEquals(offset(400), progressed.offset());
    }

    @Test
    void binlogOnlyModeDoesNotRequireSnapshotHandoff() {
        var standalone = new MySqlBinlogSplit(null, new byte[0]);
        assertEquals(MySqlBinlogSplit.Phase.STANDALONE, standalone.phase());
        assertFalse(standalone.isSuspended());
        assertEquals(offset(100), standalone.withProgress(offset(100), new byte[] {1}).offset());
        assertThrows(IllegalStateException.class, () -> standalone.withAnchor(offset(100), new byte[] {1}));
    }

    @Test
    void suspendedStreamingSplitDoesNotLoseOffsetOrFinishedMetadata() {
        var high = offset(300);
        var finished = List.of(new FinishedSnapshotSplitInfo(TABLE, "chunk", null, null, high));
        var streaming = MySqlBinlogSplit.forPausedSnapshot(offset(100), new byte[] {1, 2})
                .startStreaming(high, finished).withEmittedOffset(offset(350), new byte[] {1, 2});
        var suspended = MySqlBinlogSplit.toSuspendedBinlogSplit(streaming);
        assertTrue(suspended.isSuspended());
        assertEquals(offset(350), suspended.offset());
        assertEquals(finished, suspended.finishedSplits());
        var resumed = MySqlBinlogSplit.toNormalBinlogSplit(suspended);
        assertEquals(offset(350), resumed.offset());
        assertEquals(finished, resumed.finishedSplits());
        assertEquals(MySqlBinlogSplit.Phase.STREAMING, resumed.phase());
    }

    @Test
    void serializerPersistsPhaseAndCompletedMetadata() throws Exception {
        var high = offset(200);
        var finished = List.of(new FinishedSnapshotSplitInfo(TABLE, "chunk", null, null, high));
        var streaming = MySqlBinlogSplit.forPausedSnapshot(offset(100), new byte[] {1})
                .startStreaming(high, finished);
        var serializer = new MySqlSplitSerializer();
        var restored = (MySqlBinlogSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(streaming));
        assertEquals(MySqlBinlogSplit.Phase.STREAMING, restored.phase());
        assertEquals(finished, restored.finishedSplits());
        assertEquals(high, restored.highWatermark());
        assertArrayEquals(streaming.schemaHistory(), restored.schemaHistory());
    }

    private static final TableId TABLE = new TableId("shop", null, "orders");

    private static DebeziumOffset offset(long pos) {
        return new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", pos));
    }
}
