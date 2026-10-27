package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.BinlogPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checkpoint serializer tests for resume positions, defensive byte copies and version validation. */
class MySqlBinlogSplitCheckpointTest {

    @Test
    void retainsFineGrainedOffsetsAndHistoryWithoutSharingArrays() throws Exception {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("file", "mysql-bin.000003");
        original.put("pos", 100L);
        original.put("event", 2);
        original.put("row", 3);
        original.put("snapshot", false);
        original.put("gtids", "uuid:1-20");
        DebeziumOffset offset = new DebeziumOffset(Map.of("server", "yak"), original);
        byte[] history = {1, 2, 3};
        MySqlBinlogSplit split = new MySqlBinlogSplit(offset, history);
        history[0] = 99;

        MySqlSplitSerializer serializer = new MySqlSplitSerializer();
        MySqlBinlogSplit copy = (MySqlBinlogSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(split));
        assertEquals(split.splitId(), copy.splitId());
        assertEquals(offset, copy.offset());
        assertArrayEquals(new byte[] {1, 2, 3}, copy.schemaHistory());
        assertNotSame(split.schemaHistory(), copy.schemaHistory());
        assertThrows(IOException.class, () -> serializer.deserialize(99, serializer.serialize(split)));
    }

    @Test
    void roundTripsPendingAndAssignedEnumeratorStates() throws Exception {
        PendingSplitsStateSerializer serializer = new PendingSplitsStateSerializer(MySqlSplitSerializer.INSTANCE);
        BinlogPendingSplitsState pending =
                new BinlogPendingSplitsState(false, new MySqlBinlogSplit(null, new byte[0]));
        BinlogPendingSplitsState restored = (BinlogPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(pending));
        assertEquals(false, restored.splitAssigned());
        assertEquals(null, restored.pendingSplit().offset());

        BinlogPendingSplitsState assigned = new BinlogPendingSplitsState(true, null);
        BinlogPendingSplitsState restoredAssigned = (BinlogPendingSplitsState) serializer.deserialize(serializer.getVersion(), serializer.serialize(assigned));
        assertEquals(true, restoredAssigned.splitAssigned());
        assertEquals(null, restoredAssigned.pendingSplit());
    }

    @Test
    void rejectsIncompleteRestoredOffsetAndCorruptData() throws Exception {
        MySqlSplitSerializer serializer = new MySqlSplitSerializer();
        var offset = new DebeziumOffset(Map.of("server", "yak"), Map.of("pos", 5L));
        var missingHistory = new MySqlBinlogSplit(offset, new byte[0]);
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), serializer.serialize(missingHistory)));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), new byte[] {0, 1}));
    }
}
