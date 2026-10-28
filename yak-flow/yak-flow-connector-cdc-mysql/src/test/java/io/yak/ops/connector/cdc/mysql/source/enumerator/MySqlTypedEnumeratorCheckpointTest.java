package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Enumerator resumes the single typed chunk discovery cursor and rejects older formats. */
class MySqlTypedEnumeratorCheckpointTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final PendingSplitsStateSerializer SERIALIZER =
            new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);

    @Test
    void keepsTypedDiscoveryBoundaryAndCompletedChunkWatermarks() throws Exception {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assigner.nextBinlog();
        assigner.captureLow(offset(100));
        BigDecimal boundary = new BigDecimal("9223372036854775808.00000000");
        var first = MySqlSnapshotSplit.typed("chunk-0", new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL, null, boundary));
        assigner.appendPlan(first, 1);
        assertEquals(first, assigner.nextSnapshot());
        assigner.finishSnapshot(finished(first, 150));
        HybridPendingSplitsState persisted = (HybridPendingSplitsState) SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(assigner.snapshotState(5)));
        assertEquals(boundary, persisted.getSnapshotPendingSplits().nextChunkRange().start());
        assertEquals(1, persisted.getSnapshotPendingSplits().getFinishedSplitInfos().size());
        var recovered = new MySqlHybridSplitAssigner(StartupOptions.initial(), persisted);
        assertEquals(persisted.getSnapshotPendingSplits().nextChunkRange(), recovered.nextChunkRange());
        assertEquals(finished(first, 150), recovered.finishedSplits().getFirst());

        var last = MySqlSnapshotSplit.typed("chunk-1", new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL, boundary, null));
        recovered.appendPlan(last, 1);
        assertNull(recovered.nextChunkRange());
        assertEquals(last, recovered.nextSnapshot());
        recovered.finishSnapshot(finished(last, 200));
        assertTrue(recovered.snapshotComplete());
        recovered.captureHigh(offset(200));
        var handoff = (HybridPendingSplitsState) SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(recovered.snapshotState(7)));
        var resumed = new MySqlHybridSplitAssigner(StartupOptions.initial(), handoff);
        resumed.resumeRestoredHandoff();
        assertEquals(HybridPendingSplitsState.Phase.STREAMING, resumed.phase());
    }

    @Test
    void rejectsOldDiscoveryCheckpointVersionsWithoutGuessingTheNextRange() throws Exception {
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        assigner.nextBinlog();
        assigner.captureLow(offset(100));
        byte[] persisted = SERIALIZER.serialize(assigner.snapshotState(1));
        assertThrows(IOException.class, () -> SERIALIZER.deserialize(2, persisted));
        assertThrows(IOException.class, () -> SERIALIZER.deserialize(3, persisted));
        assertThrows(IOException.class, () -> SERIALIZER.deserialize(1, persisted));
    }

    private static FinishedSnapshotSplitInfo finished(MySqlSnapshotSplit split, long position) {
        return new FinishedSnapshotSplitInfo(
                TABLE, split.splitId(), null, null, offset(position), split.chunkRange());
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
