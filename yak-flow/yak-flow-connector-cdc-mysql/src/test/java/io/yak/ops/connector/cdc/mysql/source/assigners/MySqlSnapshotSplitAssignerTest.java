package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.ChunkSplitterState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.SnapshotPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.LogicalTypeRoot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Snapshot split assignment, completion, return and recovery contracts adapted from Flink CDC's
 * MySqlSnapshotSplitAssignerTest. State is now owned by the MySQL assigner itself.
 */
class MySqlSnapshotSplitAssignerTest {

    private static final TableId ORDERS = new TableId("shop", null, "orders");
    private static final TableId ITEMS = new TableId("shop", null, "items");

    @Test
    void testAssignSingleTableSplits() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, 10L);
        MySqlSnapshotSplit last = split(ORDERS, "orders-1", 10L, null);
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        assigner.plan(List.of(first, last));

        assertThat(assigner.next()).isEqualTo(first);
        assertThat(assigner.next()).isEqualTo(last);
        assertThat(assigner.next()).isNull();
        assertThat(assigner.noMoreSplits()).isTrue();
        assertThat(assigner.waitingForFinishedSplits()).isTrue();
        assertThat(assigner.assigned().keySet()).containsExactlyInAnyOrder("orders-0", "orders-1");

        assertThatThrownBy(assigner::getFinishedSplitInfos).isInstanceOf(IllegalStateException.class);
        assigner.onFinishedSplits(Map.of("orders-0", offset(100)));
        assertThat(assigner.waitingForFinishedSplits()).isTrue();
        assigner.onFinishedSplits(Map.of("orders-1", offset(200)));
        assertThat(assigner.waitingForFinishedSplits()).isFalse();
        assertThat(assigner.getFinishedSplitInfos()).hasSize(2);
        // Duplicate completion reports do not remove or requeue an already finished split.
        assigner.onFinishedSplits(Map.of("orders-1", offset(200)));
        assertThat(assigner.finished()).containsExactlyInAnyOrder("orders-0", "orders-1");
    }

    @Test
    void testAssignMultipleTableSplits() {
        MySqlSnapshotSplit order = split(ORDERS, "orders-0", null, null);
        MySqlSnapshotSplit item = split(ITEMS, "items-0", null, null);
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        assigner.plan(List.of(order, item));
        assertThat(assigner.next()).isEqualTo(order);
        assertThat(assigner.next()).isEqualTo(item);
        assigner.finishSnapshot(item.splitId(), offset(150));
        assertThat(assigner.allFinished()).isFalse();
        assigner.finishSnapshot(order.splitId(), offset(200));
        assertThat(assigner.allFinished()).isTrue();
        assertThat(assigner.getFinishedSplitInfos()).extracting("tableId").containsExactlyInAnyOrder(ORDERS, ITEMS);
    }

    @Test
    void testReturnedReaderSplitIsAssignableAfterRecovery() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, 10L);
        MySqlSnapshotSplit second = split(ORDERS, "orders-1", 10L, null);
        MySqlSnapshotSplitAssigner initial = new MySqlSnapshotSplitAssigner();
        initial.plan(List.of(first, second));
        initial.next();
        assertThat(initial.assigned()).containsEntry(first.splitId(), first);

        MySqlSnapshotSplitAssigner recovered = new MySqlSnapshotSplitAssigner(initial.snapshotState(5));
        assertThat(recovered.next()).isEqualTo(second);
        assertThat(recovered.allFinished()).isFalse();
        recovered.addBack(first);
        assertThat(recovered.next()).isEqualTo(first);
        recovered.finishSnapshot(first.splitId(), offset(100));
        recovered.finishSnapshot(second.splitId(), offset(200));
        assertThat(recovered.allFinished()).isTrue();
        assertThat(recovered.snapshotState(6).getAssignedSplits()).isEmpty();
    }

    @Test
    void testIncrementalChunkDiscoveryDoesNotFinishPrematurely() {
        MySqlSnapshotSplit first = MySqlSnapshotSplit.typed(
                "orders-0", new ChunkRange(ORDERS, "id", LogicalTypeRoot.BIGINT, null, 100L));
        MySqlSnapshotSplit last = MySqlSnapshotSplit.typed(
                "orders-1", new ChunkRange(ORDERS, "id", LogicalTypeRoot.BIGINT, 100L, null));
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        assigner.appendPlan(first, 1);
        assertThat(assigner.planned()).isFalse();
        assertThat(assigner.next()).isEqualTo(first);
        assigner.finishSnapshot(first.splitId(), offset(150));
        assertThat(assigner.allFinished()).isFalse();
        assertThat(assigner.getChunkSplitterState())
                .isEqualTo(new ChunkSplitterState(ORDERS, ChunkSplitterState.ChunkBound.middleOf(100L), 1));

        SnapshotPendingSplitsState checkpoint = assigner.snapshotState(8);
        MySqlSnapshotSplitAssigner restored = new MySqlSnapshotSplitAssigner(checkpoint);
        assertThat(restored.nextChunkIndex()).isEqualTo(1);
        restored.appendPlan(last, 1);
        assertThat(restored.getChunkSplitterState()).isEqualTo(ChunkSplitterState.NO_SPLITTING_TABLE_STATE);
        assertThat(restored.planned()).isTrue();
        assertThat(restored.next()).isEqualTo(last);
        restored.finishSnapshot(last.splitId(), offset(200));
        assertThat(restored.allFinished()).isTrue();
        assertThat(restored.total()).isEqualTo(2);
    }

    @Test
    void finishedOffsetsMustMatchAssignedBoundariesAndBatchReportsAreAtomic() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, 10L);
        MySqlSnapshotSplit second = split(ORDERS, "orders-1", 10L, null);
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        assigner.plan(List.of(first, second));

        assertThatThrownBy(() -> assigner.finishSnapshot(first.splitId(), offset(100)))
                .isInstanceOf(IllegalArgumentException.class);
        assigner.next();
        assertThatThrownBy(() -> assigner.onFinishedSplits(Map.of(
                        first.splitId(), offset(100), "missing", offset(200))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(assigner.finished()).isEmpty();
        assertThat(assigner.assigned()).containsKey(first.splitId());
        assertThatThrownBy(() -> assigner.finishSnapshot(
                        new io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo(
                                ORDERS, first.splitId(), 1L, 10L, offset(100))))
                .isInstanceOf(IllegalArgumentException.class);
        assigner.finishSnapshot(first.splitId(), offset(100));
        assertThatThrownBy(() -> assigner.finishSnapshot(first.splitId(), offset(101)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> assigner.addBack(first)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidRestoredAssignmentStateFailsClosed() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, null);
        assertThatThrownBy(() -> new SnapshotPendingSplitsState(
                        true, 2, List.of(first), Map.of(first.splitId(), first), Set.of(), List.of(), 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnapshotPendingSplitsState(
                        true, 1, List.of(), Map.of("other-id", first), Set.of(), List.of(), 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hybridWireFormatRetainsOwnedSnapshotStateAndFinishedMetadata() throws Exception {
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        var first = MySqlSnapshotSplit.typed(
                "orders-0", new ChunkRange(ORDERS, "id", LogicalTypeRoot.BIGINT, null, 100L));
        var last = MySqlSnapshotSplit.typed(
                "orders-1", new ChunkRange(ORDERS, "id", LogicalTypeRoot.BIGINT, 100L, null));
        assigner.appendPlan(first, 1);
        assigner.next();
        assigner.finishSnapshot(first.splitId(), offset(100));
        assigner.appendPlan(last, 1);
        var state = assigner.snapshotState(10);
        // Checkpoint wire format remains a Hybrid envelope; restore into the same
        // snapshot owner and ensure both finished watermark and pending work survive.
        var wrapped = new io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState(
                state,
                false,
                io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit.forInitialSnapshot(),
                io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState.Phase.SNAPSHOT,
                offset(1),
                null);
        var serializer = new PendingSplitsStateSerializer();
        var recovered = (io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(wrapped));
        MySqlSnapshotSplitAssigner restored = new MySqlSnapshotSplitAssigner(recovered.getSnapshotPendingSplits());
        assertThat(restored.finishedSplits()).isEqualTo(assigner.finishedSplits());
        assertThat(restored.next()).isEqualTo(last);
        assertThat(restored.nextChunkIndex()).isZero();
        assertThat(restored.nextTableIndex()).isEqualTo(1);
    }

    @Test
    void newlyAddedTablesRestoreThroughSnapshotFinishedAndBinlogUpdatedStates() {
        MySqlSnapshotSplitAssigner initial = new MySqlSnapshotSplitAssigner();
        initial.initializeTables(List.of(ORDERS), true, false);
        MySqlSnapshotSplit original = split(ORDERS, "orders-0", null, null);
        initial.plan(List.of(original));
        initial.next();
        initial.finishSnapshot(original.splitId(), offset(100));
        initial.snapshotState(1);
        initial.notifyCheckpointComplete(1);
        assertThat(initial.getAssignerStatus()).isEqualTo(AssignerStatus.INITIAL_ASSIGNING_FINISHED);

        MySqlSnapshotSplitAssigner restored = new MySqlSnapshotSplitAssigner(initial.snapshotState(2));
        assertThat(restored.initializeTables(List.of(ORDERS, ITEMS), true, false)).isTrue();
        assertThat(restored.getAssignerStatus()).isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING);
        assertThat(restored.remainingTables()).containsExactly(ITEMS);

        MySqlSnapshotSplit added = split(ITEMS, "items-0", null, null);
        restored.plan(List.of(added));
        assertThat(restored.next()).isEqualTo(added);
        restored.finishSnapshot(added.splitId(), offset(200));
        restored.snapshotState(3);
        restored.notifyCheckpointComplete(3);
        assertThat(restored.getAssignerStatus())
                .isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED);

        restored.onBinlogSplitUpdated();
        assertThat(restored.getAssignerStatus()).isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED);
        assertThat(restored.finishedSplits()).hasSize(2);
    }

    @Test
    void releasedSnapshotMetadataIsPersistedAndRestoredWithoutReconstruction() throws Exception {
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        assigner.initializeTables(List.of(ORDERS), false, true);
        MySqlSnapshotSplit only = split(ORDERS, "orders-0", null, null);
        assigner.plan(List.of(only));
        assigner.next();
        assigner.finishSnapshot(only.splitId(), offset(200));
        assigner.snapshotState(1);
        assigner.notifyCheckpointComplete(1);
        assigner.releaseSnapshotMetadata();

        SnapshotPendingSplitsState released = assigner.snapshotState(2);
        assertThat(released.isSnapshotMetaReleased()).isTrue();
        assertThat(released.getFinishedSplitInfos()).isEmpty();
        assertThat(released.getAlreadyProcessedTables()).containsExactly(ORDERS);

        var hybrid = new io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState(
                released,
                true,
                null,
                io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState.Phase.STREAMING,
                offset(1),
                offset(200));
        PendingSplitsStateSerializer serializer = new PendingSplitsStateSerializer();
        var recovered = (io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState)
                serializer.deserialize(serializer.getVersion(), serializer.serialize(hybrid));
        SnapshotPendingSplitsState restored = recovered.getSnapshotPendingSplits();
        assertThat(restored.isSnapshotMetaReleased()).isTrue();
        assertThat(restored.getFinishedSplitInfos()).isEmpty();
        assertThat(restored.getAlreadyProcessedTables()).containsExactly(ORDERS);
        assertThat(new MySqlSnapshotSplitAssigner(restored).allFinished()).isTrue();
    }

    private static MySqlSnapshotSplit split(TableId table, String id, Long low, Long high) {
        return new MySqlSnapshotSplit(id, table, low, high, null);
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }
}
