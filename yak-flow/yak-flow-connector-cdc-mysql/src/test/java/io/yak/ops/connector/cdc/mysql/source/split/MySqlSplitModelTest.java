package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Flink CDC split accessors must preserve YakFlow's typed boundary and checkpoint contract. */
class MySqlSplitModelTest {

    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void exposesTypedSnapshotBoundaries() {
        ChunkRange range = new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 100L, 200L);
        MySqlSnapshotSplit split = MySqlSnapshotSplit.typed("orders:0", "source", range);
        assertEquals(TABLE, split.getTableId());
        assertArrayEquals(new Object[] {100L}, split.getSplitStart());
        assertArrayEquals(new Object[] {200L}, split.getSplitEnd());
        assertFalse(split.isSnapshotReadFinished());
        assertNull(split.getHighWatermark());
        assertEquals(split, MySqlSnapshotSplit.typed("orders:0", "source", range));
        assertEquals(split.hashCode(), MySqlSnapshotSplit.typed("orders:0", "source", range).hashCode());
    }

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
    void classConversionPreservesTheCurrentTypedSplitCheckpointContract() throws Exception {
        ChunkRange range = new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 100L, 200L);
        MySqlSnapshotSplit split = MySqlSnapshotSplit.typed("orders:0", "source", range);
        MySqlHybridSplitSerializer serializer = new MySqlHybridSplitSerializer();
        byte[] serialized = serializer.serialize(split);
        MySqlSnapshotSplit restored =
                (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), serialized);
        assertEquals(split, restored);
        assertArrayEquals(split.getSplitStart(), restored.getSplitStart());
        assertArrayEquals(split.getSplitEnd(), restored.getSplitEnd());
    }
}
