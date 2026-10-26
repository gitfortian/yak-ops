package io.yak.ops.connector.cdc.mysql.source.enumerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Enumerator resumes typed chunk discovery without guessing from old BIGINT cursor slots. */
class MySqlTypedEnumeratorCheckpointTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final MySqlHybridEnumeratorStateSerializer SERIALIZER =
            new MySqlHybridEnumeratorStateSerializer();

    @Test
    void keepsTypedDiscoveryBoundaryAndCompletedChunkWatermarks() throws Exception {
        var assigner = new MySqlHybridSplitAssigner("fp", null);
        assigner.nextBinlog(0);
        assigner.captureLow(offset(100));
        BigDecimal boundary = new BigDecimal("9223372036854775808.00000000");
        var first = MySqlSnapshotSplit.typed(
                "chunk-0", "fp", new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL, null, boundary));
        assigner.appendTypedPlan(first, 0, 1,
                new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL, boundary, null), false);
        assertEquals(first, assigner.nextSnapshot());
        assigner.finishSnapshot(finished(first, 150));
        MySqlHybridEnumeratorState persisted = SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(assigner.snapshot(5)));
        assertEquals(boundary, persisted.nextChunkRange().start());
        assertNull(persisted.nextChunkLower());
        assertEquals(1, persisted.finishedSplitInfos().size());
        var recovered = new MySqlHybridSplitAssigner("fp", persisted);
        assertEquals(persisted.nextChunkRange(), recovered.nextChunkRange());
        assertEquals(finished(first, 150), recovered.finishedSplits().getFirst());

        var last = MySqlSnapshotSplit.typed(
                "chunk-1", "fp", new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL, boundary, null));
        recovered.appendTypedPlan(last, 1, 0, null, true);
        assertNull(recovered.nextChunkRange());
        assertEquals(last, recovered.nextSnapshot());
        recovered.finishSnapshot(finished(last, 200));
        assertTrue(recovered.snapshotComplete());
        recovered.captureHigh(offset(200));
        var handoff = SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(recovered.snapshot(7)));
        var resumed = new MySqlHybridSplitAssigner("fp", handoff);
        resumed.resumeRestoredHandoff();
        assertEquals(MySqlHybridEnumeratorState.Phase.STREAMING, resumed.phase());
    }

    @Test
    void decodesGenuineVersionTwoEnumeratorWithNoNestedSplits() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF("fp");
            out.writeByte(MySqlHybridEnumeratorState.Phase.SNAPSHOT.ordinal());
            out.writeBoolean(true); // assigned Binlog, no nested split
            out.writeBoolean(true);
            offset(100).writeTo(out);
            out.writeBoolean(false); // no high
            out.writeBoolean(false); // discovery incomplete
            out.writeInt(0); // planned count
            out.writeInt(0); // pending
            out.writeInt(0); // assigned
            out.writeInt(0); // finished IDs
            out.writeInt(0); // finished metadata
            out.writeInt(0); // table index
            out.writeInt(0); // chunk index
            out.writeBoolean(false);
        }
        MySqlHybridEnumeratorState restored = SERIALIZER.deserialize(2, bytes.toByteArray());
        assertEquals(MySqlHybridEnumeratorState.Phase.SNAPSHOT, restored.phase());
        assertNull(restored.nextChunkRange());
        assertEquals(0, restored.nextChunkIndex());
        assertThrows(IOException.class, () -> SERIALIZER.deserialize(3, bytes.toByteArray()));
        assertThrows(IOException.class, () -> SERIALIZER.deserialize(1, bytes.toByteArray()));
    }

    @Test
    void readsLegacyEnumeratorWithNestedVersionThreeSnapshotSplit() throws Exception {
        var embedded = new ByteArrayOutputStream();
        try (var split = new DataOutputStream(embedded)) {
            split.writeByte(1);
            split.writeUTF("legacy-chunk");
            split.writeUTF("fp");
            split.writeUTF("shop");
            split.writeUTF("orders");
            split.writeBoolean(false);
            split.writeBoolean(false);
            split.writeBoolean(false);
            split.writeByte(0);
        }
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF("fp");
            out.writeByte(MySqlHybridEnumeratorState.Phase.SNAPSHOT.ordinal());
            out.writeBoolean(true);
            out.writeBoolean(true);
            offset(100).writeTo(out);
            out.writeBoolean(false);
            out.writeBoolean(false);
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(embedded.size());
            out.write(embedded.toByteArray());
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            out.writeBoolean(false);
        }
        MySqlHybridEnumeratorState old = SERIALIZER.deserialize(2, bytes.toByteArray());
        assertEquals(1, old.pendingSnapshots().size());
        assertEquals(
                new MySqlSnapshotSplit("legacy-chunk", "fp", TABLE, null, null, null),
                old.pendingSnapshots().getFirst());
        assertEquals(old.pendingSnapshots(), SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(old)).pendingSnapshots());
    }

    private static FinishedSnapshotSplitInfo finished(MySqlSnapshotSplit split, long position) {
        return new FinishedSnapshotSplitInfo(
                TABLE, split.splitId(), null, null, offset(position), split.chunkRange());
    }

    private static BinlogOffset offset(long position) {
        return new BinlogOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
