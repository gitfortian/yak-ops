package io.yak.ops.connector.cdc.mysql.source.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ServerIdRangeTest {
    @Test
    void parsesInclusiveRangeAndReservesAllReaders() {
        ServerIdRange ids = ServerIdRange.from("5400-5416");
        assertEquals(17, ids.getNumberOfServerIds());
        assertEquals(5400, ids.getServerId(0));
        assertEquals(5416, ids.getServerId(16));
        assertThrows(IllegalArgumentException.class, () -> ids.getServerId(17));
        assertThrows(IllegalArgumentException.class, () -> ids.getServerId(-1));
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new ServerIdRange(10, 9));
        assertThrows(IllegalArgumentException.class, () -> ServerIdRange.from("foo"));
    }
}
