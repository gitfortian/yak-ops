package io.yak.ops.connector.cdc.mysql.source.offset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises finer-grained MySQL offsets before using them for snapshot fences. */
class BinlogOffsetOrderTest {

    @Test
    void comparesFilePositionsAndEventRows() {
        BinlogOffset low = offset("mysql-bin.000001", 100L, 0, 0);
        BinlogOffset sameEvent = offset("mysql-bin.000001", 100L, 0, 1);
        BinlogOffset high = offset("mysql-bin.000001", 200L, 0, 0);
        BinlogOffset rotated = offset("mysql-bin.000002", 4L, 0, 0);
        assertTrue(BinlogOffsetOrder.isAfter(sameEvent, low));
        assertTrue(BinlogOffsetOrder.isAfter(high, sameEvent));
        assertTrue(BinlogOffsetOrder.isAfter(rotated, high));
        assertFalse(BinlogOffsetOrder.isAfter(low, high));
        assertEquals(0, BinlogOffsetOrder.compare(high, high));
    }

    @Test
    void comparesContainedGtidHistoriesWithoutLosingWithinTransactionProgress() {
        String source = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        BinlogOffset first = new BinlogOffset(
                Map.of("server", "shop"),
                Map.of("gtids", source + ":1-5", "file", "mysql-bin.000001", "pos", 100L, "row", 0));
        BinlogOffset later = new BinlogOffset(
                Map.of("server", "shop"),
                Map.of("gtids", source + ":1-7", "file", "mysql-bin.000001", "pos", 120L, "row", 0));
        assertTrue(BinlogOffsetOrder.isAfter(later, first));
    }

    @Test
    void rejectsCrossSourceOffsetsAndUnknownPositions() {
        BinlogOffset regular = offset("mysql-bin.000001", 100L, 0, 0);
        BinlogOffset otherServer = new BinlogOffset(
                Map.of("server", "another"), Map.of("file", "mysql-bin.000001", "pos", 100L));
        assertThrows(IllegalArgumentException.class, () -> BinlogOffsetOrder.compare(regular, otherServer));
        BinlogOffset missingFile = new BinlogOffset(
                Map.of("server", "shop"), Map.of("pos", 100L));
        assertThrows(IllegalArgumentException.class, () -> BinlogOffsetOrder.compare(regular, missingFile));
    }


    @Test
    void comparesUnpaddedFileRotationAndKeepsEventRowOrder() {
        BinlogOffset previous = offset("mysql-bin.9", 98765L, 0, 0);
        BinlogOffset rotated = offset("mysql-bin.10", 4L, 0, 0);
        assertTrue(BinlogOffsetOrder.compare(previous, rotated) < 0);
        assertTrue(BinlogOffsetOrder.compare(rotated, previous) > 0);
        assertTrue(BinlogOffsetOrder.isAfter(rotated, previous));

        BinlogOffset nextEvent = offset("mysql-bin.10", 4L, 1, 0);
        BinlogOffset nextRow = offset("mysql-bin.10", 4L, 1, 1);
        assertTrue(BinlogOffsetOrder.compare(nextEvent, rotated) > 0);
        assertTrue(BinlogOffsetOrder.compare(nextRow, nextEvent) > 0);
        assertEquals(0, nextRow.compareTo(nextRow));
    }

    @Test
    void usesDebeziumGtidSetsForEquivalentAndContainedIntervals() {
        String server = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String other = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        BinlogOffset fragmented = gtid(server + ":1-3:4-5," + other + ":1-2", "mysql-bin.9", 90L);
        BinlogOffset combined = gtid(other + ":1-2," + server + ":1-5", "mysql-bin.10", 4L);
        assertTrue(fragmented.compareTo(combined) < 0);
        assertTrue(combined.compareTo(fragmented) > 0);

        BinlogOffset extended = gtid(server + ":1-8," + other + ":1-2", "mysql-bin.10", 10L);
        assertTrue(fragmented.compareTo(extended) < 0);
        assertTrue(extended.compareTo(fragmented) > 0);
    }

    @Test
    void rejectsIncomparableGtidHistoriesAndInvalidPositionMetadata() {
        String server = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String other = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        BinlogOffset first = gtid(server + ":1-5", "mysql-bin.1", 100L);
        BinlogOffset second = gtid(other + ":1-5", "mysql-bin.1", 100L);
        assertThrows(IllegalArgumentException.class, () -> first.compareTo(second));
        assertThrows(IllegalArgumentException.class, () -> second.compareTo(first));

        BinlogOffset missingPos = new BinlogOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.1"));
        assertThrows(IllegalArgumentException.class, () -> first.compareTo(missingPos));
        BinlogOffset missingFile = new BinlogOffset(
                Map.of("server", "shop"), Map.of("gtids", server + ":1-5"));
        assertThrows(IllegalArgumentException.class, () -> first.compareTo(missingFile));
    }

    @Test
    void comparesGtidOnlyOffsetsAndMixedGtidMetadataWithFilePositions() {
        String server = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        BinlogOffset before = new BinlogOffset(
                Map.of("server", "shop"), Map.of("gtids", server + ":1-5", "event", 1L, "row", 0L));
        BinlogOffset after = new BinlogOffset(
                Map.of("server", "shop"), Map.of("gtids", server + ":1-5", "event", 1L, "row", 1L));
        assertTrue(before.compareTo(after) < 0);
        assertTrue(after.compareTo(before) > 0);

        BinlogOffset withGtids = gtid(server + ":1-6", "mysql-bin.11", 4L);
        BinlogOffset withoutGtids = offset("mysql-bin.10", 900L, 0, 0);
        assertTrue(withGtids.compareTo(withoutGtids) > 0);
    }

    private static BinlogOffset gtid(String gtids, String file, long position) {
        return new BinlogOffset(
                Map.of("server", "shop"), Map.of("gtids", gtids, "file", file, "pos", position));
    }

    private static BinlogOffset offset(String file, long position, int event, int row) {
        return new BinlogOffset(
                Map.of("server", "shop"),
                Map.of("file", file, "pos", position, "event", event, "row", row));
    }

    @org.junit.jupiter.api.Test
    void equalGtidSetsStillCompareBinlogFileAndPosition() {
        var partition = java.util.Map.<String,Object>of("server", "same");
        var left = new BinlogOffset(partition, java.util.Map.of("gtids", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-5", "file", "mysql-bin.000001", "pos", 100L));
        var right = new BinlogOffset(partition, java.util.Map.of("gtids", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-5", "file", "mysql-bin.000001", "pos", 200L));
        org.junit.jupiter.api.Assertions.assertTrue(BinlogOffsetOrder.compare(left, right) < 0);
        org.junit.jupiter.api.Assertions.assertTrue(BinlogOffsetOrder.compare(right, left) > 0);
    }
}
