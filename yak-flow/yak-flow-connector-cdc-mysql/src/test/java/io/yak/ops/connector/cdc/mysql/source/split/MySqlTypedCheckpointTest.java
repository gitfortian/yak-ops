package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Typed range persistence and immutable prepared-output recovery, without enabling typed readers. */
class MySqlTypedCheckpointTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final MySqlSplitSerializer SERIALIZER = new MySqlSplitSerializer();

    @Test
    void persistsEachSupportedBoundaryFamilyWithoutCoercion() throws Exception {
        List<ChunkRange> ranges = List.of(
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 12L, 40L),
                new ChunkRange(TABLE, "amount", LogicalTypeRoot.DECIMAL,
                        new BigDecimal("9223372036854775808.00000000"), new BigDecimal("9223372036854775809.00000000")),
                new ChunkRange(TABLE, "code", LogicalTypeRoot.VARCHAR, "a", "ø"),
                new ChunkRange(TABLE, "code", LogicalTypeRoot.CHAR, null, null));
        for (ChunkRange range : ranges) {
            MySqlSnapshotSplit split = MySqlSnapshotSplit.typed("typed", "fingerprint", range);
            assertEquals(split, roundTrip(split));
            assertEquals(range, roundTrip(split).chunkRange());
            assertThrows(IllegalStateException.class, () -> split.withLastEmittedKey(15L));
        }
    }

    @Test
    void frozenTypedSnapshotResumesFromDurableOutputPositionInsteadOfRescanning() throws Exception {
        ChunkRange range = new ChunkRange(
                TABLE, "amount", LogicalTypeRoot.DECIMAL, new BigDecimal("1.00"), new BigDecimal("9.00"));
        MySqlSnapshotSplit split = MySqlSnapshotSplit.typed("chunk", "fp", range);
        MySqlSnapshotOutput output = new MySqlSnapshotOutput(
                offset(100), offset(200),
                List.of(
                        new TableRecord(TABLE, RowKind.INSERT,
                                GenericRowData.of(new BigDecimal("2.00"), "same")),
                        new TableRecord(TABLE, RowKind.INSERT,
                                GenericRowData.of(new BigDecimal("2.00"), "same"))));
        MySqlSplitState state = MySqlSplitState.fromSplit(split);
        state.prepareSnapshot(split.withOutput(output));
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(1));
        state.snapshotCheckpoint(5);
        assertNull(state.completeCheckpoint(4));
        assertEquals("chunk", state.completeCheckpoint(5));
        state.emitSnapshotRows(1);
        MySqlSnapshotSplit frozen = roundTrip((MySqlSnapshotSplit) state.checkpoint(null));
        assertEquals(range, frozen.chunkRange());
        assertEquals(1, frozen.outputPosition());
        assertEquals(2, frozen.output().rows().size());
        MySqlSplitState recovered = MySqlSplitState.fromSplit(frozen);
        recovered.emitSnapshotRows(2);
        MySqlSnapshotSplit completed = roundTrip((MySqlSnapshotSplit) recovered.checkpoint(null));
        assertEquals(range, completed.finished().chunkRange());
        assertEquals(offset(200), completed.finished().highWatermark());
        assertNull(completed.output());
    }

    @Test
    void restoresTypedCompletedWatermarksInsideStreamingBinlogSplit() throws Exception {
        var offset = offset(200);
        var typed = MySqlSnapshotSplit.typed(
                        "one", "fp", new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, null, null))
                .withOutput(new MySqlSnapshotOutput(offset(100), offset, List.of()))
                .withOutputPosition(0);
        var anchor = new MySqlHybridBinlogSplit(
                new MySqlBinlogSplit("fp", null, new byte[0]),
                MySqlHybridBinlogSplit.Phase.BOOTSTRAP, null)
                .withAnchor(offset(100), new byte[] {1, 2});
        var streaming = anchor.startStreaming(offset, List.of(typed.finished()));
        var restored = (MySqlHybridBinlogSplit) SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(streaming));
        assertEquals(typed.finished(), restored.finishedSplits().getFirst());
    }

    @Test
    void rejectsOldSplitFormatsAndTruncatedFrames() throws Exception {
        var split = MySqlSnapshotSplit.typed("old", "fp",
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, 2L, 10L));
        byte[] encoded = SERIALIZER.serialize(split);
        for (int oldVersion = 1; oldVersion < SERIALIZER.getVersion(); oldVersion++) {
            final int version = oldVersion;
            assertThrows(IOException.class, () -> SERIALIZER.deserialize(version, encoded));
        }
        assertThrows(IOException.class, () -> SERIALIZER.deserialize(
                SERIALIZER.getVersion(), Arrays.copyOf(encoded, encoded.length - 1)));
    }

    private static MySqlSnapshotSplit roundTrip(MySqlSnapshotSplit split) throws Exception {
        return (MySqlSnapshotSplit) SERIALIZER.deserialize(SERIALIZER.getVersion(), SERIALIZER.serialize(split));
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
