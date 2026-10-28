package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Flink CDC's finished snapshot metadata identity and boundary contracts, adapted to YakFlow. */
class FinishedSnapshotSplitInfoTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void finishedChunkAccessorsAndEqualityRemainStable() {
        DebeziumOffset offset = new DebeziumOffset(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", 200L));
        ChunkRange range = new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 100L, 200L);
        FinishedSnapshotSplitInfo info =
                new FinishedSnapshotSplitInfo(TABLE, "orders:0", null, null, offset, range);
        FinishedSnapshotSplitInfo again =
                new FinishedSnapshotSplitInfo(TABLE, "orders:0", null, null, offset, range);
        assertEquals(TABLE, info.getTableId());
        assertEquals("orders:0", info.getSplitId());
        assertArrayEquals(new Object[] {100L}, info.getSplitStart());
        assertArrayEquals(new Object[] {200L}, info.getSplitEnd());
        assertEquals(offset, info.getHighWatermark());
        assertEquals(info, again);
        assertEquals(info.hashCode(), again.hashCode());
        assertTrue(info.chunkRange() != null);
    }

    @Test
    void rejectsInvalidCompletedChunkBoundaries() {
        DebeziumOffset high = new DebeziumOffset(
                Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000001", "pos", 200L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FinishedSnapshotSplitInfo(TABLE, "chunk", 10L, 10L, high));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FinishedSnapshotSplitInfo(TABLE, "chunk", 20L, 10L, high));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FinishedSnapshotSplitInfo(
                        TABLE, "chunk", 1L, 2L, high,
                        new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 1L, 2L)));
    }
}
