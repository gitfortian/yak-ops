package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.TableId;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures the hybrid transport persists both split kinds with complete normalized snapshot metadata. */
class MySqlSplitSerializerTest {

    @Test
    void roundTripsUnfinishedSnapshotBoundariesWithoutCursorImages() throws Exception {
        var serializer = new MySqlSplitSerializer();
        var original = new MySqlSnapshotSplit("snapshot-0", new TableId("shop", null, "orders"), 100L, 200L, null);
        MySqlSnapshotSplit restored =
                (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(original));
        assertEquals(original, restored);
        assertThrows(IOException.class, () -> serializer.deserialize(99, serializer.serialize(original)));
        assertThrows(IOException.class, () -> serializer.deserialize(2, serializer.serialize(original)));
        assertEquals(100L, restored.getSplitStart()[0]);
        assertEquals(200L, restored.getSplitEnd()[0]);
        assertEquals(null, restored.output());
    }

    @Test
    void roundTripsPausedAndRunningBinlogWithSchemaHistory() throws Exception {
        var serializer = new MySqlSplitSerializer();
        var low = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 100L));
        var high = new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.000010", "pos", 300L));
        var bootstrap = MySqlBinlogSplit.forInitialSnapshot();
        var paused = bootstrap.withAnchor(low, new byte[] {1, 2, 3});
        var running = paused.startStreaming(high, completed(high)).withEmittedOffset(high, new byte[] {1, 2, 3});
        var restored = (MySqlBinlogSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(running));
        assertEquals(MySqlBinlogSplit.Phase.STREAMING, restored.phase());
        assertEquals(high, restored.highWatermark());
        assertEquals(high, restored.offset());
        assertArrayEquals(new byte[] {1, 2, 3}, restored.schemaHistory());
        assertArrayEquals(serializer.serialize(running), serializer.serialize(restored));
        assertThrows(IllegalStateException.class, () -> paused.withEmittedOffset(high, new byte[] {1}));
    }

    @Test
    void rejectsTruncatedOrUnknownSplitKind() {
        var serializer = new MySqlSplitSerializer();
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), new byte[] {9}));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), new byte[] {1}));
    }

    private static java.util.List<FinishedSnapshotSplitInfo> completed(DebeziumOffset high) {
        return java.util.List.of(new FinishedSnapshotSplitInfo(
                new io.yak.ops.core.data.TableId("shop", null, "orders"), "chunk", null, null, high));
    }
    /** The repeated-serialization contract from Flink CDC's MySqlSplitSerializerTest. */
    @Test
    void testRepeatedSerializationCache() throws Exception {
        var serializer = MySqlSplitSerializer.INSTANCE;
        MySqlSnapshotSplit snapshot = new MySqlSnapshotSplit(
                "repeated", new TableId("shop", null, "orders"), null, 100L, null);
        byte[] original = serializer.serialize(snapshot);
        for (int index = 0; index < 4; index++) {
            assertArrayEquals(original, serializer.serialize(snapshot));
            assertArrayEquals(
                    original,
                    serializer.serialize(
                            serializer.deserialize(serializer.getVersion(), original)));
        }
    }

}
