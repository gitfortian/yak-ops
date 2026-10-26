package io.yak.ops.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** CDC transport metadata is not a JDBC field, business key or RowData property. */
class ChangeEventIdTest {
    @Test
    void retainsRowOwnershipAndSeparatesImagesFromTheirCommonMutation() {
        var table = new TableId("shop", null, "events");
        var payload = GenericRowData.of(5L, "same");
        var record = new TableRecord(table, RowKind.UPDATE_BEFORE, payload);
        assertNull(record.changeEventId());
        var before = new ChangeEventId("job", "binlog:abc", 0, 2);
        var after = new ChangeEventId("job", "binlog:abc", 1, 2);
        var transported = record.withChangeEventId(before);
        assertSame(payload, transported.row());
        assertEquals(before, transported.changeEventId());
        assertTrue(before.sameEvent(after));
        assertFalse(before.equals(after));
        assertEquals(2, transported.row().getArity());
        assertSame(table, transported.tableId());
        assertEquals(RowKind.UPDATE_BEFORE, transported.rowKind());
        assertEquals(before, transported.withChangeEventId(before).changeEventId());
        assertThrows(IllegalStateException.class, () -> transported.withChangeEventId(after));
        assertFalse(transported.toString().contains("binlog:abc"));
        assertFalse(before.toString().contains("binlog:abc"));
    }

    @Test
    void frozenSnapshotOrdinalsDistinguishPhysicallyIdenticalRows() {
        var first = ChangeEventId.snapshot("job", "chunk-0", 0);
        var second = ChangeEventId.snapshot("job", "chunk-0", 1);
        assertFalse(first.sameEvent(second));
        assertEquals(first, ChangeEventId.snapshot("job", "chunk-0", 0));
        assertFalse(first.sameEvent(ChangeEventId.snapshot("different-job", "chunk-0", 0)));
        assertThrows(IllegalArgumentException.class, () -> ChangeEventId.snapshot("job", "chunk-0", -1));
        assertThrows(IllegalArgumentException.class, () -> new ChangeEventId("job", "e", 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new ChangeEventId("job", "e", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChangeEventId("", "e", 0, 1));
    }
}
