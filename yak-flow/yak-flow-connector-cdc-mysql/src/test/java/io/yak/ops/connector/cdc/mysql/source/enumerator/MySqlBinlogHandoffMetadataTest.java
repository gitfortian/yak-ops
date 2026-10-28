package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.SnapshotPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaAssembledEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.ReaderInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.connector.source.SplitsAssignment;
import io.yak.ops.core.api.metrics.Gauge;
import io.yak.ops.core.api.metrics.groups.SplitEnumeratorMetricGroup;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises metadata groups, stale ACK rejection and restored HANDOFF to STREAMING. */
class MySqlBinlogHandoffMetadataTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void restoredHandoffAssemblesBoundedGroupsBeforeResume() {
        var context = new Context();
        var source = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9320)
                .topicPrefix("handoff-groups")
                .splitMetaGroupSize(2)
                .table(
                        TABLE,
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")))
                .buildHybrid(2);
        var enumerator = new MySqlSourceEnumerator(
                StartupOptions.initial(), context, source.config(), 2, handoffState(), new TableMetrics());
        enumerator.start();

        assertThat(context.toReader0).hasSize(1);
        var first = (BinlogSplitMetaEvent) context.toReader0.getFirst();
        assertThat(first.getMetaGroupId()).isZero();
        assertThat(first.getTotalGroups()).isEqualTo(2);
        assertThat(first.getTotalFinishedSplitSize()).isEqualTo(3);
        assertThat(first.getMetaGroup()).hasSize(2);
        assertThat(first.getBinlogAssignmentGeneration()).isZero();

        assertThatThrownBy(() -> enumerator.handleSourceEvent(
                        1, new BinlogSplitMetaAssembledEvent(0)))
                .isInstanceOf(IllegalArgumentException.class);
        enumerator.handleSourceEvent(0, new BinlogSplitMetaAssembledEvent(1));
        assertThat(((HybridPendingSplitsState) enumerator.snapshotState(12)).phase())
                .isEqualTo(HybridPendingSplitsState.Phase.HANDOFF);
        assertThat(context.toReader0).noneMatch(MySqlResumeBinlogEvent.class::isInstance);

        enumerator.handleSourceEvent(0, new BinlogSplitMetaRequestEvent(1, 0));
        var last = (BinlogSplitMetaEvent) context.toReader0.getLast();
        assertThat(last.getMetaGroupId()).isEqualTo(1);
        assertThat(last.getMetaGroup()).hasSize(1);

        enumerator.handleSourceEvent(0, new BinlogSplitMetaAssembledEvent(0));
        assertThat(((HybridPendingSplitsState) enumerator.snapshotState(13)).phase())
                .isEqualTo(HybridPendingSplitsState.Phase.STREAMING);
        assertThat(context.toReader0).anyMatch(MySqlResumeBinlogEvent.class::isInstance);
        assertThat(context.toReader1).anyMatch(MySqlStopSnapshotEvent.class::isInstance);
        enumerator.close();
    }

    @Test
    void rejectsSnapshotMetadataReleaseWithoutDurableReleasedStateProtocol() {
        var source = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9321)
                .topicPrefix("handoff-release")
                .releaseSnapshotMetadataEnabled(true)
                .table(
                        TABLE,
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")))
                .buildHybrid(2);
        assertThatThrownBy(() -> new MySqlSourceEnumerator(
                        StartupOptions.initial(), new Context(), source.config(), 2, handoffState(), new TableMetrics()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("durable released-state checkpoint");
    }

    private static HybridPendingSplitsState handoffState() {
        List<FinishedSnapshotSplitInfo> infos = List.of(
                info("chunk-0", 100), info("chunk-1", 200), info("chunk-2", 300));
        var snapshots = new SnapshotPendingSplitsState(
                true, 3, List.of(), Map.of(), Set.of("chunk-0", "chunk-1", "chunk-2"), infos, 1, 0, null);
        return new HybridPendingSplitsState(
                snapshots, true, null, HybridPendingSplitsState.Phase.HANDOFF, offset(50), offset(300));
    }

    private static FinishedSnapshotSplitInfo info(String id, long position) {
        return new FinishedSnapshotSplitInfo(TABLE, id, null, null, offset(position));
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }

    private static final class Context implements SplitEnumeratorContext<MySqlSplit> {
        private final List<SourceEvent> toReader0 = new ArrayList<>();
        private final List<SourceEvent> toReader1 = new ArrayList<>();

        @Override
        public int currentParallelism() {
            return 2;
        }

        @Override
        public Map<Integer, ReaderInfo> registeredReaders() {
            return Map.of(0, new ReaderInfo(0, ""), 1, new ReaderInfo(1, ""));
        }

        @Override
        public SplitEnumeratorMetricGroup metricGroup() {
            return new SplitEnumeratorMetricGroup() {
                @Override
                public <G extends Gauge<Long>> G setUnassignedSplitsGauge(G gauge) {
                    return gauge;
                }
            };
        }

        @Override
        public void assignSplits(SplitsAssignment<MySqlSplit> assignment) {
            assignment.assignment().forEach((reader, splits) ->
                    splits.forEach(split -> assignSplit(split, reader)));
        }

        @Override
        public void assignSplit(MySqlSplit split, int reader) {}

        @Override
        public void signalNoMoreSplits(int reader) {}

        @Override
        public void sendEventToSourceReader(int reader, SourceEvent event) {
            if (reader == 0) toReader0.add(event);
            else if (reader == 1) toReader1.add(event);
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            throw new AssertionError("Completed handoff must not rediscover Snapshot chunks");
        }

        @Override
        public <T> void callAsync(
                Callable<T> action,
                BiConsumer<T, Throwable> handler,
                long initialDelayMillis,
                long periodMillis) {
            throw new AssertionError("Completed handoff must not schedule discovery");
        }
    }
}
