package io.yak.ops.connector.cdc.mysql.source.utils;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.data.TableId;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression tests for the Flink CDC split-key and numeric utility contracts. */
class SourceUtilitiesAlignmentTest {

    private static final TableId TABLE = new TableId("test", null, "orders");

    @Test
    void sortsAndFindsHalfOpenFinishedChunkRanges() {
        var first = finished("first", null, 10L, 100L);
        var middle = finished("middle", 10L, 20L, 110L);
        var last = finished("last", 20L, null, 120L);
        List<FinishedSnapshotSplitInfo> chunks = new ArrayList<>(List.of(last, first, middle));

        SplitKeyUtils.sortFinishedSplitInfos(chunks);
        assertEquals(List.of(first, middle, last), chunks);
        assertSame(first, SplitKeyUtils.findSplitByKeyBinary(chunks, new Object[] {9L}));
        assertSame(middle, SplitKeyUtils.findSplitByKeyBinary(chunks, new Object[] {10L}));
        assertSame(last, SplitKeyUtils.findSplitByKeyBinary(chunks, new Object[] {20L}));
        // JDBC and Debezium can materialize the same numeric key as different Java classes.
        assertSame(middle, SplitKeyUtils.findSplitByKeyBinary(chunks, new Object[] {new BigDecimal("10.00")}));
    }

    @Test
    void splitRangeContainsFollowsHalfOpenBoundaries() {
        assertTrue(SplitKeyUtils.splitKeyRangeContains(new Object[] {1}, null, new Object[] {10}));
        assertFalse(SplitKeyUtils.splitKeyRangeContains(new Object[] {10}, null, new Object[] {10}));
        assertTrue(SplitKeyUtils.splitKeyRangeContains(new Object[] {10}, new Object[] {10}, null));
    }

    @Test
    void numericObjectUtilitiesMatchFlinkSemantics() {
        assertEquals(new BigInteger("9223372036854775808"),
                ObjectUtils.plus(new BigInteger("9223372036854775807"), 1));
        assertEquals(new BigDecimal("1.25"), ObjectUtils.minus(new BigDecimal("2.50"), new BigDecimal("1.25")));
        assertTrue(ObjectUtils.compare(new byte[] {(byte) 255}, new byte[] {1}) > 0);
    }

    @Test
    void earliestFinishedWatermarkUsesFlinkStyleRecordUtility() {
        var first = finished("first", null, 10L, 150L);
        var second = finished("second", 10L, null, 100L);
        assertEquals(second.highWatermark(),
                RecordUtils.getStartingOffsetOfBinlogSplit(List.of(first, second)));
    }

    private static FinishedSnapshotSplitInfo finished(
            String id, Long start, Long end, long position) {
        return new FinishedSnapshotSplitInfo(
                TABLE, id, start, end,
                new DebeziumOffset(
                        Map.of("server", "mysql"), Map.of("file", "mysql-bin.000001", "pos", position)));
    }
}
