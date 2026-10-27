package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Incremental Snapshot only persists completed range metadata, never row images. */
class MySqlSnapshotOutputTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void completedSplitPersistsOnlyWatermarkAndRange() throws Exception {
        var raw = new MySqlSnapshotSplit("chunk", TABLE, 1L, 10L, null);
        var prepared = raw.withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(row(3L, "one"), row(3L, "one"), row(4L, "other"))));
        var serializer = new MySqlSplitSerializer();
        assertThrows(IOException.class, () -> serializer.serialize(prepared));
        var state = MySqlSplitState.fromSplit(raw);
        state.prepareSnapshot(prepared);
        var finished = (MySqlSnapshotSplit) state.checkpoint(null);
        assertNull(finished.output());
        assertEquals(offset(200), finished.finished().highWatermark());
        var restored = (MySqlSnapshotSplit)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(finished));
        assertEquals(finished, restored);
        assertEquals(raw.getSplitStart()[0], restored.getSplitStart()[0]);
        assertEquals(raw.getSplitEnd()[0], restored.getSplitEnd()[0]);
    }

    @Test
    void failureBeforeCompletedChunkRestoresUnpreparedRange() throws Exception {
        var raw = new MySqlSnapshotSplit("chunk", TABLE, null, null, null);
        var serializer = new MySqlSplitSerializer();
        var restored = (MySqlSnapshotSplit)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(raw));
        assertEquals(raw, restored);
        assertFalse(restored.prepared());
        assertNull(restored.finished());
    }

    @Test
    void inMemoryNormalizedRowsRetainKeylessMultiplicityAndDetachedValues() {
        byte[] payload = {1, 2, 3};
        var mutable = GenericRowData.of(1L, "same", payload);
        var buffer = new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, mutable),
                        new TableRecord(TABLE, RowKind.INSERT, mutable)));
        mutable.setField(1, "changed");
        payload[0] = 8;
        assertEquals(2, buffer.rows().size());
        assertEquals("same", buffer.rows().getFirst().row().getString(1));
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) buffer.rows().getLast().row().getField(2));
    }

    private static TableRecord row(long key, String value) {
        return new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(key, value));
    }

    private static DebeziumOffset offset(long pos) {
        return new DebeziumOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", pos));
    }
}
