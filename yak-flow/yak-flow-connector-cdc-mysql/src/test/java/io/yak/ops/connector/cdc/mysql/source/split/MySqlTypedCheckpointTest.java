package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Snapshot recovery uses only chunk ranges and completed high watermarks. */
class MySqlTypedCheckpointTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final MySqlSplitSerializer SERIALIZER = new MySqlSplitSerializer();

    @Test
    void unfinishedTypedChunksResumeFromTheirOriginalRanges() throws Exception {
        var ranges = List.of(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 12L, 40L),
                new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL,
                        new BigDecimal("9223372036854775808.00000000"),
                        new BigDecimal("9223372036854775809.00000000")),
                new ChunkRange(TABLE, "code", LogicalTypeRoot.VARCHAR, "a", "z"),
                new ChunkRange(TABLE, "code", LogicalTypeRoot.CHAR, null, null));
        for (ChunkRange range : ranges) {
            var raw = MySqlSnapshotSplit.typed("typed", "definition", range);
            var restored = roundTrip(raw);
            assertEquals(range, restored.chunkRange());
            assertFalse(restored.prepared());
            assertNull(restored.output());
        }
    }

    @Test
    void finishedWatermarkSurvivesWithoutSerializedRows() throws Exception {
        var range = new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL,
                new BigDecimal("1.00"), new BigDecimal("9.00"));
        var original = MySqlSnapshotSplit.typed("chunk", "definition", range);
        var high = offset(200);
        var prepared = original.withOutput(new MySqlSnapshotOutput(offset(100), high, List.of()));
        assertThrows(IOException.class, () -> SERIALIZER.serialize(prepared));
        var state = MySqlSplitState.fromSplit(original);
        state.prepareSnapshot(prepared);
        var restored = roundTrip((MySqlSnapshotSplit) state.checkpoint(null));
        assertEquals(range, restored.finished().chunkRange());
        assertEquals(high, restored.finished().highWatermark());
        assertNull(restored.output());
    }

    @Test
    void oldCheckpointVersionsAndTruncatedBuffersFailClosed() throws Exception {
        var raw = MySqlSnapshotSplit.typed("chunk", "definition",
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 1L, 5L));
        byte[] encoded = SERIALIZER.serialize(raw);
        for (int v = 1; v < SERIALIZER.getVersion(); v++) {
            int oldVersion = v;
            assertThrows(IOException.class, () -> SERIALIZER.deserialize(oldVersion, encoded));
        }
        assertThrows(IOException.class, () -> SERIALIZER.deserialize(SERIALIZER.getVersion(),
                Arrays.copyOf(encoded, encoded.length - 1)));
    }

    private static MySqlSnapshotSplit roundTrip(MySqlSnapshotSplit split) throws Exception {
        return (MySqlSnapshotSplit) SERIALIZER.deserialize(SERIALIZER.getVersion(), SERIALIZER.serialize(split));
    }

    private static DebeziumOffset offset(long pos) {
        return new DebeziumOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", pos));
    }
}
