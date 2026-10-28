/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.connector.cdc.mysql.source.utils;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;

import io.yak.ops.core.data.TableId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tests for {@link SplitKeyUtils}. */
class SplitKeyUtilsTest {

    @Test
    void testSortFinishedSplitInfos() {
        TableId tableId = new TableId("test_db", null, "test_table");

        List<FinishedSnapshotSplitInfo> emptyList = new ArrayList<>();
        SplitKeyUtils.sortFinishedSplitInfos(emptyList);
        Assertions.assertThat(emptyList).isEmpty();

        List<FinishedSnapshotSplitInfo> singleList = new ArrayList<>();
        singleList.add(createSplit(tableId, "split-1", new Object[] {100L}, new Object[] {200L}));
        SplitKeyUtils.sortFinishedSplitInfos(singleList);
        Assertions.assertThat(singleList).hasSize(1);

        List<FinishedSnapshotSplitInfo> splits = new ArrayList<>();
        splits.add(createSplit(tableId, "split-3", new Object[] {200L}, new Object[] {300L}));
        splits.add(createSplit(tableId, "split-1", null, new Object[] {100L}));
        splits.add(createSplit(tableId, "split-4", new Object[] {300L}, null));
        splits.add(createSplit(tableId, "split-2", new Object[] {100L}, new Object[] {200L}));

        SplitKeyUtils.sortFinishedSplitInfos(splits);

        Assertions.assertThat(splits.get(0).getSplitStart()).isNull();
        Assertions.assertThat(splits.get(1).getSplitStart()).isEqualTo(new Object[] {100L});
        Assertions.assertThat(splits.get(2).getSplitStart()).isEqualTo(new Object[] {200L});
        Assertions.assertThat(splits.get(3).getSplitStart()).isEqualTo(new Object[] {300L});
    }

    @Test
    void testFindSplitByKeyBinary() {
        TableId tableId = new TableId("test_db", null, "test_table");

        List<FinishedSnapshotSplitInfo> sortedSplits = new ArrayList<>();
        sortedSplits.add(createSplit(tableId, "split-0", null, new Object[] {100L}));
        sortedSplits.add(createSplit(tableId, "split-1", new Object[] {100L}, new Object[] {200L}));
        sortedSplits.add(createSplit(tableId, "split-2", new Object[] {200L}, new Object[] {300L}));
        sortedSplits.add(createSplit(tableId, "split-3", new Object[] {300L}, null));

        FinishedSnapshotSplitInfo result =
                SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {-1L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-0");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {100L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-1");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {150L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-1");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {200L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-2");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {250L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-2");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {300L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-3");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {1000L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-3");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {99L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-0");
    }

    @Test
    void testFindSplitByKeyBinaryWithOnlyOneSplit() {
        TableId tableId = new TableId("test_db", null, "test_table");

        List<FinishedSnapshotSplitInfo> sortedSplits = new ArrayList<>();
        sortedSplits.add(createSplit(tableId, "split-0", null, null));

        FinishedSnapshotSplitInfo result =
                SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {100L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-0");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {Long.MAX_VALUE});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-0");
    }

    @Test
    void testFindSplitByKeyBinaryWithLargeNumberOfSplits() {
        TableId tableId = new TableId("test_db", null, "test_table");

        List<FinishedSnapshotSplitInfo> sortedSplits = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            Object[] start = i == 0 ? null : new Object[] {(long) i * 10};
            Object[] end = i == 999 ? null : new Object[] {(long) (i + 1) * 10};
            sortedSplits.add(createSplit(tableId, "split-" + i, start, end));
        }

        FinishedSnapshotSplitInfo result =
                SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {5L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-0");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {505L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-50");

        result = SplitKeyUtils.findSplitByKeyBinary(sortedSplits, new Object[] {9995L});
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getSplitId()).isEqualTo("split-999");
    }

    @Test
    void testFindSplitByKeyBinaryEdgeCases() {
        TableId tableId = new TableId("test_db", null, "test_table");

        List<FinishedSnapshotSplitInfo> emptyList = new ArrayList<>();
        FinishedSnapshotSplitInfo result =
                SplitKeyUtils.findSplitByKeyBinary(emptyList, new Object[] {100L});
        Assertions.assertThat(result).isNull();

        result = SplitKeyUtils.findSplitByKeyBinary(null, new Object[] {100L});
        Assertions.assertThat(result).isNull();
    }

    @Test
    void testBinarySearchConsistencyWithLinearSearch() {
        TableId tableId = new TableId("test_db", null, "test_table");

        List<FinishedSnapshotSplitInfo> sortedSplits = new ArrayList<>();
        sortedSplits.add(createSplit(tableId, "split-0", null, new Object[] {100L}));
        sortedSplits.add(createSplit(tableId, "split-1", new Object[] {100L}, new Object[] {200L}));
        sortedSplits.add(createSplit(tableId, "split-2", new Object[] {200L}, new Object[] {300L}));
        sortedSplits.add(createSplit(tableId, "split-3", new Object[] {300L}, new Object[] {400L}));
        sortedSplits.add(createSplit(tableId, "split-4", new Object[] {400L}, null));

        for (long key = 0; key < 500; key += 10) {
            Object[] keyArray = new Object[] {key};

            FinishedSnapshotSplitInfo binaryResult =
                    SplitKeyUtils.findSplitByKeyBinary(sortedSplits, keyArray);

            FinishedSnapshotSplitInfo linearResult = null;
            for (FinishedSnapshotSplitInfo split : sortedSplits) {
                if (SplitKeyUtils.splitKeyRangeContains(
                        keyArray, split.getSplitStart(), split.getSplitEnd())) {
                    linearResult = split;
                    break;
                }
            }

            if (binaryResult == null) {
                Assertions.assertThat(linearResult)
                        .as("Key %d should not be in any split", key)
                        .isNull();
            } else {
                Assertions.assertThat(linearResult)
                        .as("Key %d should be found by both methods", key)
                        .isNotNull();
                Assertions.assertThat(binaryResult.getSplitId())
                        .as("Both methods should find the same split for key %d", key)
                        .isEqualTo(linearResult.getSplitId());
            }
        }
    }

    private FinishedSnapshotSplitInfo createSplit(
            TableId tableId, String splitId, Object[] splitStart, Object[] splitEnd) {
        return new FinishedSnapshotSplitInfo(
                tableId, splitId,
                splitStart == null ? null : ((Number) splitStart[0]).longValue(),
                splitEnd == null ? null : ((Number) splitEnd[0]).longValue(),
                new DebeziumOffset(
                        Map.of("server", "test_db"),
                        Map.of("file", "mysql-bin.000001", "pos", 4L)));
    }

    @Test
    void sortsAndFindsHalfOpenFinishedChunkRanges() {
        var first = alignmentFinished("first", null, 10L, 100L);
        var middle = alignmentFinished("middle", 10L, 20L, 110L);
        var last = alignmentFinished("last", 20L, null, 120L);
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

    private static final TableId ALIGNMENT_TABLE = new TableId("test", null, "orders");

    private static FinishedSnapshotSplitInfo alignmentFinished(
            String id, Long start, Long end, long position) {
        return new FinishedSnapshotSplitInfo(
                ALIGNMENT_TABLE, id, start, end,
                new DebeziumOffset(
                        Map.of("server", "mysql"), Map.of("file", "mysql-bin.000001", "pos", position)));
    }

}
