package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlHybridSplitState;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises durable preparation, scalar state, and partial output without live database dependencies. */
class MySqlSnapshotOutputTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void preparationMustBeCoveredByCompletedCheckpointBeforeEmission() throws Exception {
        MySqlSnapshotSplit prepared = prepared();
        MySqlHybridSplitState state = new MySqlHybridSplitState(range());
        state.prepareSnapshot(prepared);
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(1));
        state.snapshotCheckpoint(10);
        state.snapshotCheckpoint(11);
        assertNull(state.completeCheckpoint(9));
        assertEquals("chunk", state.completeCheckpoint(10));
        state.emitSnapshotRows(1);
        MySqlSnapshotSplit partial = (MySqlSnapshotSplit) state.checkpoint(null);
        assertEquals(1, partial.outputPosition());
        assertEquals(2, partial.output().rows().size());
        assertEquals("original", partial.output().rows().getFirst().row().getString(1));
        MySqlHybridSplitState recovered = new MySqlHybridSplitState(roundTrip(partial));
        recovered.emitSnapshotRows(2);
        MySqlSnapshotSplit done = (MySqlSnapshotSplit) recovered.checkpoint(null);
        assertNull(done.output());
        assertEquals(offset(200), done.finished().highWatermark());
        assertEquals(done, roundTrip(done));
        assertNull(((MySqlSnapshotSplit) new MySqlHybridSplitState(roundTrip(done)).checkpoint(null)).output());
    }

    @Test
    void emptyChunkStillRequiresDurablePreparationAndRetainsItsHigh() {
        var prepared = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(100), List.of()));
        var state = new MySqlHybridSplitState(range());
        state.prepareSnapshot(prepared);
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(0));
        state.snapshotCheckpoint(3);
        state.completeCheckpoint(3);
        state.emitSnapshotRows(0);
        assertEquals(offset(100), ((MySqlSnapshotSplit) state.checkpoint(null)).finished().highWatermark());
    }

    @Test
    void normalizedRowsCannotBeMutatedThroughProducerOrReaderReferences() {
        byte[] payload = {1,2,3};
        GenericRowData mutable = GenericRowData.of(1L, "original", payload);
        var result = new MySqlSnapshotOutput(offset(100), offset(200), List.of(new TableRecord(TABLE, RowKind.INSERT, mutable)));
        mutable.setField(1, "changed");
        payload[0] = 9;
        var row = result.rows().getFirst().row();
        assertFalse(row instanceof GenericRowData);
        assertEquals("original", row.getString(1));
        byte[] exposed = (byte[]) row.getField(2);
        exposed[0] = 8;
        assertArrayEquals(new byte[]{1,2,3}, (byte[]) row.getField(2));
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertFalse(result.toString().contains("original"));
    }

    @Test
    void roundTripsAllCanonicalScalarFamiliesWithoutTypeCoercion() throws Exception {
        GenericRowData values = GenericRowData.of(1L, null, true, (byte) 2, (short) 3, 4, 5L, 1.25f, 2.5d,
                new BigDecimal("123456.78900"), "长字符串".repeat(24000), new byte[]{0,1,-1},
                LocalDate.of(2020,2,29), LocalTime.of(12,13,14,123456789),
                LocalDateTime.of(2020,2,29,12,13,14,123456789), OffsetDateTime.parse("2020-02-29T12:13:14.123456789+08:00"));
        var original = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, values))));
        MySqlSnapshotSplit restored = roundTrip(original);
        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
    }

    @Test
    void rejectsOldVersionTruncationUnsupportedScalarsAndTrailingBytes() throws Exception {
        var serializer = new MySqlHybridSplitSerializer();
        byte[] bytes = serializer.serialize(prepared());
        assertThrows(IOException.class, () -> serializer.deserialize(1, bytes));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), Arrays.copyOf(bytes, bytes.length-1)));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), Arrays.copyOf(bytes, bytes.length+1)));
        var unsupported = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, new Object())))));
        assertThrows(IOException.class, () -> serializer.serialize(unsupported));
    }

    @Test
    void compressesPreparedRowsWithoutChangingRecoveryData() throws Exception {
        java.util.ArrayList<TableRecord> rows = new java.util.ArrayList<>();
        for (long index = 0; index < 1000; index++) {
            rows.add(new TableRecord(TABLE, RowKind.INSERT,
                    GenericRowData.of(index, "a repetitive CDC payload ".repeat(20))));
        }
        MySqlSnapshotSplit prepared = range().withOutput(
                new MySqlSnapshotOutput(offset(100), offset(200), rows));
        var serializer = new MySqlHybridSplitSerializer();
        byte[] encoded = serializer.serialize(prepared);
        byte[] legacy = legacySnapshot(prepared);
        assertTrue(encoded.length < legacy.length / 2, "Prepared row checkpoint should be compact");
        assertEquals(prepared, serializer.deserialize(serializer.getVersion(), encoded));
        assertEquals(prepared, serializer.deserialize(2, legacy));
    }

    @Test
    void detectsCorruptedOrTruncatedCompressedSnapshotOutput() throws Exception {
        var serializer = new MySqlHybridSplitSerializer();
        byte[] encoded = serializer.serialize(prepared());
        byte[] corrupted = encoded.clone();
        // The last four bytes are the output cursor, not the checksummed row payload.
        corrupted[corrupted.length - 5] ^= 0x01;
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), corrupted));
        byte[] invalidCursor = encoded.clone();
        invalidCursor[invalidCursor.length - 1] = 3;
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), invalidCursor));
        assertThrows(IOException.class, () -> serializer.deserialize(
                serializer.getVersion(), Arrays.copyOf(encoded, encoded.length - 1)));
    }

    /** The old checkpoint body remains readable after upgrading the split version. */
    private static byte[] legacySnapshot(MySqlSnapshotSplit snapshot) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(bytes)) {
            out.writeByte(1);
            out.writeUTF(snapshot.splitId());
            out.writeUTF(snapshot.fingerprint());
            out.writeUTF(snapshot.tableId().catalog());
            out.writeUTF(snapshot.tableId().table());
            out.writeBoolean(snapshot.lowerInclusive() != null);
            if (snapshot.lowerInclusive() != null) out.writeLong(snapshot.lowerInclusive());
            out.writeBoolean(snapshot.upperExclusive() != null);
            if (snapshot.upperExclusive() != null) out.writeLong(snapshot.upperExclusive());
            out.writeBoolean(snapshot.lastEmittedKey() != null);
            if (snapshot.lastEmittedKey() != null) out.writeLong(snapshot.lastEmittedKey());
            out.writeByte(1);
            MySqlSnapshotOutputSerializer.write(out, snapshot.output());
            out.writeInt(snapshot.outputPosition());
        }
        return bytes.toByteArray();
    }

    @Test
    void invalidOutputPositionsAndConflictingRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> prepared().withOutputPosition(3));
        var state = new MySqlHybridSplitState(range());
        var other = new MySqlSnapshotSplit("other", "fp", TABLE, null, null, null).withOutput(prepared().output());
        assertThrows(IllegalStateException.class, () -> state.prepareSnapshot(other));
        assertThrows(IllegalArgumentException.class, () -> new MySqlSnapshotOutput(offset(200), offset(100), List.of()));
    }

    private static MySqlSnapshotSplit prepared() {
        return range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200), List.of(
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, "original")),
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(2L, "second")))));
    }
    private static MySqlSnapshotSplit range() { return new MySqlSnapshotSplit("chunk", "fp", TABLE, null, null, null); }
    private static BinlogOffset offset(long pos) { return new BinlogOffset(Map.of("server","shop"), Map.of("file","mysql-bin.000001","pos",pos)); }
    private static MySqlSnapshotSplit roundTrip(MySqlSnapshotSplit split) throws Exception {
        var serializer = new MySqlHybridSplitSerializer();
        return (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(split));
    }
}
