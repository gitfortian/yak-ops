package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.source.assigners.state.BinlogPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import java.util.Map;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Verifies assignment and recovery using the same contract for both startup modes. */
class MySqlSplitAssignerContractTest {

    @Test
    void binlogOnlyReturnsOneSplitAndPersistsAssignedOwnership() throws Exception {
        MySqlSplitAssigner<MySqlBinlogSplit, BinlogPendingSplitsState> assigner =
                new MySqlBinlogSplitAssigner(StartupOptions.latest(), null);
        assertNull(assigner.getNext(1));
        MySqlBinlogSplit split = assigner.getNext(0);
        assertEquals(MySqlBinlogSplit.ID, split.splitId());
        assertInstanceOf(MySqlSplit.class, split);
        assertNull(assigner.getNext(0));
        assertFalse(assigner.notifyCheckpointComplete(1));

        BinlogPendingSplitsState assigned = assigner.snapshotState(2);
        assertInstanceOf(PendingSplitsState.class, assigned);
        assertTrue(assigned.splitAssigned());
        var serializer = new PendingSplitsStateSerializer(MySqlSplitSerializer.INSTANCE);
        var restored = new MySqlBinlogSplitAssigner(StartupOptions.latest(), (BinlogPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(assigned)));
        assertNull(restored.getNext(0));

        assigner.addSplitsBack(List.of(split), 0);
        assertFalse(assigner.snapshotState(3).splitAssigned());
        assertEquals(split, assigner.getNext(0));
        assertThrows(IllegalStateException.class, () -> assigner.addSplitsBack(List.of(split), 1));
        assertNull(new MySqlBinlogSplitAssigner(StartupOptions.latest(), assigned).getNext(0));
    }


    @Test
    void returnedBinlogSplitKeepsItsOffsetAcrossEnumeratorCheckpoint() throws Exception {
        var assigner = new MySqlBinlogSplitAssigner(StartupOptions.latest(), null);
        assigner.getNext(0);
        var offset = new DebeziumOffset(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", 256L));
        var returned = new MySqlBinlogSplit(offset, new byte[] {1, 2, 3});
        assigner.addSplitsBack(List.of(returned), 0);

        var serializer = new PendingSplitsStateSerializer(MySqlSplitSerializer.INSTANCE);
        var state = (BinlogPendingSplitsState) serializer.deserialize(
                serializer.getVersion(), serializer.serialize(assigner.snapshotState(4)));
        var restored = new MySqlBinlogSplitAssigner(StartupOptions.latest(), state);
        MySqlBinlogSplit resumed = restored.getNext(0);
        assertEquals(offset, resumed.offset());
        assertEquals(List.of((byte) 1, (byte) 2, (byte) 3),
                java.util.stream.IntStream.range(0, resumed.schemaHistory().length)
                        .mapToObj(i -> resumed.schemaHistory()[i]).toList());
    }

    @Test
    void pendingBinlogSurvivesCoordinatorCheckpointWithoutMovingItsOffset() throws Exception {
        var assigner = new MySqlBinlogSplitAssigner(StartupOptions.latest(), null);
        BinlogPendingSplitsState pending = assigner.snapshotState(1);
        assertFalse(pending.splitAssigned());
        var serializer = new PendingSplitsStateSerializer(MySqlSplitSerializer.INSTANCE);
        var restored = new MySqlBinlogSplitAssigner(StartupOptions.latest(), (BinlogPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(pending)));
        var split = restored.getNext(0);
        assertNull(split.offset());
        assertEquals(0, split.schemaHistory().length);
        assertThrows(IllegalArgumentException.class, () -> assigner.snapshotState(-1));
    }
}
