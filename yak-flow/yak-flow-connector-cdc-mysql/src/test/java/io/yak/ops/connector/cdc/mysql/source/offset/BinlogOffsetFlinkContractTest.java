package io.yak.ops.connector.cdc.mysql.source.offset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Verifies Flink CDC's symbolic and specific offset contracts independently of Debezium checkpoints. */
class BinlogOffsetFlinkContractTest {

    @Test
    void representsAllOffsetKindsAndTheirConstruction() {
        assertEquals(BinlogOffsetKind.EARLIEST, BinlogOffset.ofEarliest().getOffsetKind());
        assertEquals(BinlogOffsetKind.LATEST, BinlogOffset.ofLatest().getOffsetKind());
        assertEquals(BinlogOffsetKind.TIMESTAMP, BinlogOffset.ofTimestampSec(1700000000L).getOffsetKind());
        assertEquals(BinlogOffsetKind.NON_STOPPING, BinlogOffset.ofNonStopping().getOffsetKind());
        assertEquals("mysql-bin.000007", BinlogOffset.ofBinlogFilePosition("mysql-bin.000007", 4).getFilename());
        assertEquals(4L, BinlogOffset.ofBinlogFilePosition("mysql-bin.000007", 4).getPosition());
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-5",
                BinlogOffset.ofGtidSet("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-5").getGtidSet());
        assertThrows(IllegalArgumentException.class, () -> BinlogOffset.builder().build());
    }

    @Test
    void ordersEventsAndRowsAndBoundsWithNonStopping() {
        BinlogOffset file = BinlogOffset.ofBinlogFilePosition("mysql-bin.000007", 4);
        BinlogOffset laterEvent = BinlogOffset.builder().setBinlogFilePosition("mysql-bin.000007", 4)
                .setSkipEvents(1).build();
        BinlogOffset laterRow = BinlogOffset.builder().setBinlogFilePosition("mysql-bin.000007", 4)
                .setSkipEvents(1).setSkipRows(1).build();
        assertTrue(file.isBefore(laterEvent));
        assertTrue(laterEvent.isBefore(laterRow));
        assertFalse(laterRow.isBefore(file));
        assertTrue(BinlogOffset.ofNonStopping().isAfter(laterRow));
    }

    @Test
    void jsonRoundTripsFlinkOffsetsWithoutDebeziumPartition() throws IOException {
        BinlogOffset offset = BinlogOffset.builder().setBinlogFilePosition("mysql-bin.000007", 1024)
                .setSkipEvents(1).setSkipRows(2).setServerId(99).build();
        byte[] bytes = BinlogOffsetSerializer.INSTANCE.serialize(offset);
        BinlogOffset restored = BinlogOffsetSerializer.INSTANCE.deserialize(bytes);
        assertEquals(offset, restored);
        assertArrayEquals(bytes, BinlogOffsetSerializer.INSTANCE.serialize(restored));
    }

    @Test
    void identifiesNonStoppingEndOffset() {
        assertTrue(BinlogOffsetUtils.isNonStoppingOffset(BinlogOffset.ofNonStopping()));
        assertFalse(BinlogOffsetUtils.isNonStoppingOffset(BinlogOffset.ofLatest()));
    }
}
