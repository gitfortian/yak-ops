package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests Flink-style grouped Binlog metadata assembly, replay and corruption rejection. */
class BinlogSplitMetaGroupAssemblerTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void assemblesGroupsAndValidatesHandoffWatermark() {
        var assembler = new BinlogSplitMetaGroupAssembler();
        var first = info("chunk-0", 100);
        var second = info("chunk-1", 200);
        var third = info("chunk-2", 300);
        assertThat(assembler.addGroup(group(0, 2, 3, 0, first, second))).isFalse();
        assertThat(assembler.nextGroupId()).isEqualTo(1);
        assertThatThrownBy(() -> assembler.finishedSplits(offset(300))).isInstanceOf(IllegalStateException.class);
        assertThat(assembler.addGroup(group(1, 2, 3, 0, third))).isTrue();
        assertThat(assembler.finishedSplits(offset(300))).containsExactly(first, second, third);
        assertThatThrownBy(() -> assembler.finishedSplits(offset(200))).isInstanceOf(IllegalStateException.class);
        assertThat(assembler.addGroup(group(0, 2, 3, 0, first, second))).isTrue();
    }

    @Test
    void rejectsDuplicateOutOfOrderAndMissingSnapshotChunks() {
        var assembler = new BinlogSplitMetaGroupAssembler();
        var first = info("chunk-0", 100);
        var second = info("chunk-1", 200);
        assertThat(assembler.addGroup(group(0, 2, 2, 4, first))).isFalse();
        assertThatThrownBy(() -> assembler.addGroup(group(0, 2, 2, 4, first)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> assembler.addGroup(group(1, 2, 2, 4, first)))
                .isInstanceOf(IllegalStateException.class);

        var fresh = new BinlogSplitMetaGroupAssembler();
        assertThat(fresh.addGroup(group(0, 2, 3, 8, first))).isFalse();
        assertThatThrownBy(() -> fresh.addGroup(group(1, 2, 3, 8, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void discardsStaleGenerationAfterReaderAssignmentChanges() {
        var assembler = new BinlogSplitMetaGroupAssembler();
        var first = info("chunk-0", 100);
        assertThat(assembler.addGroup(group(0, 2, 2, 1, first))).isFalse();
        assertThat(assembler.addGroup(group(0, 1, 1, 2, first))).isTrue();
        assertThat(assembler.addGroup(group(1, 2, 2, 1, info("chunk-1", 200)))).isFalse();
        assertThat(assembler.finishedSplits(offset(100))).containsExactly(first);
        assertThat(assembler.generation()).isEqualTo(2);
    }

    private static BinlogSplitMetaEvent group(
            int index, int groups, int total, long generation, FinishedSnapshotSplitInfo... infos) {
        return new BinlogSplitMetaEvent(index, groups, total, generation, List.of(infos));
    }

    private static FinishedSnapshotSplitInfo info(String split, long position) {
        return new FinishedSnapshotSplitInfo(TABLE, split, null, null, offset(position));
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
