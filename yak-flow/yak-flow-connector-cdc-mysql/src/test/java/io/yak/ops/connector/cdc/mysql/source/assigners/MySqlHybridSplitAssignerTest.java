package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Proves Binlog handoff needs a completed snapshot checkpoint, not only an assignment ACK. */
class MySqlHybridSplitAssignerTest {

    @Test
    void gatesHandoffAndRestoresCompletedPendingTransition() throws Exception {
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var high = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 300L));
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assertEquals(HybridPendingSplitsState.Phase.BOOTSTRAP, assigner.phase());
        assertEquals("mysql-binlog", assigner.nextBinlog().splitId());
        assigner.captureLow(low);
        assertFalse(assigner.snapshotState(10).getSnapshotPendingSplits().snapshotPlanned());
        var split = new MySqlSnapshotSplit("snap-1", new TableId("shop", null, "orders"),
                null, null, null);
        assigner.plan(List.of(split));
        assertFalse(assigner.snapshotComplete());
        assertEquals(split, assigner.nextSnapshot());
        assigner.finishSnapshot(finished(split, 300));
        assertTrue(assigner.snapshotComplete());
        assigner.captureHigh(high);
        assigner.notifyCheckpointComplete(11);
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());
        HybridPendingSplitsState waiting = assigner.snapshotState(12);
        assigner.notifyCheckpointComplete(11);
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, assigner.phase());

        var serializer = new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);
        var checkpoint = (HybridPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(waiting));
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), checkpoint);
        restored.resumeRestoredHandoff();
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, restored.phase());
        assigner.snapshotState(13);
        assigner.notifyCheckpointComplete(12);
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, assigner.phase());
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, assigner.phase());
    }


    @Test
    void checkpointDuringAsyncSnapshotPlanningRestartsPlanFromDurableState() throws Exception {
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assigner.nextBinlog();
        assigner.captureLow(low);

        // The planning callback has not run yet. A periodic checkpoint must not fail the job.
        var serializer = new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);
        var saved = assigner.snapshotState(21);
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), (HybridPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(saved)));
        assertEquals(HybridPendingSplitsState.Phase.SNAPSHOT, restored.phase());
        assertFalse(restored.snapshotPlanned());
        restored.plan(List.of(new MySqlSnapshotSplit("snap-1", new TableId("shop", null, "orders"), null, null, null)));
        assertTrue(restored.snapshotPlanned());
    }

    @Test
    void restoresCompletedWatermarksBeforeHandoff() throws Exception {
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assigner.nextBinlog();
        assigner.captureLow(low);
        var split = new MySqlSnapshotSplit("snap-1", new TableId("shop", null, "orders"), null, null, null);
        assigner.plan(List.of(split));
        assigner.nextSnapshot();
        assigner.finishSnapshot(finished(split, 200));

        var saved = assigner.snapshotState(22);
        var serializer = new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), (HybridPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(saved)));
        assertEquals(HybridPendingSplitsState.Phase.SNAPSHOT, restored.phase());
        assertTrue(restored.snapshotComplete());
        restored.captureHigh(
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", 200L)));
        assertEquals(HybridPendingSplitsState.Phase.HANDOFF, restored.phase());
    }

    @Test
    void restoresSnapshotAssignerFromItsCheckpointState() {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var state = assigner.snapshotState(1);
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), state);
        assertEquals(state, restored.snapshotState(2));
    }

    private static io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo finished(
            MySqlSnapshotSplit split, long position) {
        return new io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo(split.tableId(),
                split.splitId(), split.lowerInclusive(), split.upperExclusive(),
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000001", "pos", position)));
    }
}
