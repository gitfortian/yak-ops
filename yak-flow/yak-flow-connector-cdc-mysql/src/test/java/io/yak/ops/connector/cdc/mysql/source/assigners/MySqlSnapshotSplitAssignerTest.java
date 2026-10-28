package io.yak.ops.connector.cdc.mysql.source.assigners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Snapshot split assignment contracts adopted from Flink CDC's
 * MySqlSnapshotSplitAssignerTest. YakFlow shares the common SnapshotSplitAssigner
 * instead of copying Flink's database discovery code into the coordinator.
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
        assertThat(assigner.allFinished()).isFalse();
        assertThat(assigner.assigned().keySet()).containsExactlyInAnyOrder("orders-0", "orders-1");

        assigner.complete("orders-0");
        assertThat(assigner.allFinished()).isFalse();
        assigner.complete("orders-1");
        assertThat(assigner.allFinished()).isTrue();
        // Flink CDC's snapshot completion reporting is idempotent.
        assigner.complete("orders-1");
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
        assigner.complete(item.splitId());
        assertThat(assigner.allFinished()).isFalse();
        assigner.complete(order.splitId());
        assertThat(assigner.allFinished()).isTrue();
    }

    @Test
    void testReturnedReaderSplitIsAssignableAfterRecovery() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, 10L);
        MySqlSnapshotSplit second = split(ORDERS, "orders-1", 10L, null);
        MySqlSnapshotSplitAssigner initial = new MySqlSnapshotSplitAssigner();
        initial.plan(List.of(first, second));
        initial.next();
        assertThat(initial.assigned()).containsEntry(first.splitId(), first);

        MySqlSnapshotSplitAssigner recovered = new MySqlSnapshotSplitAssigner(
                initial.pending(), initial.assigned(), initial.finished(), initial.total(), initial.planned());
        assertThat(recovered.next()).isEqualTo(second);
        assertThat(recovered.allFinished()).isFalse();
        recovered.addBack(first);
        assertThat(recovered.next()).isEqualTo(first);
        recovered.complete(first.splitId());
        recovered.complete(second.splitId());
        assertThat(recovered.allFinished()).isTrue();
    }

    @Test
    void testIncrementalChunkDiscoveryDoesNotFinishPrematurely() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, 100L);
        MySqlSnapshotSplit last = split(ORDERS, "orders-1", 100L, null);
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        assigner.append(List.of(first), false);
        assertThat(assigner.planned()).isFalse();
        assertThat(assigner.next()).isEqualTo(first);
        assigner.complete(first.splitId());
        assertThat(assigner.allFinished()).isFalse();

        assigner.append(List.of(last), true);
        assertThat(assigner.planned()).isTrue();
        assertThat(assigner.next()).isEqualTo(last);
        assigner.complete(last.splitId());
        assertThat(assigner.allFinished()).isTrue();
        assertThat(assigner.total()).isEqualTo(2);
    }

    @Test
    void testDuplicateOrUnassignedCompletionIsRejected() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, null);
        MySqlSnapshotSplitAssigner assigner = new MySqlSnapshotSplitAssigner();
        assertThatThrownBy(() -> assigner.plan(List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class);
        assigner.plan(List.of(first));
        assertThatThrownBy(() -> assigner.complete(first.splitId()))
                .isInstanceOf(IllegalArgumentException.class);
        assigner.next();
        assigner.complete(first.splitId());
        assertThatThrownBy(() -> assigner.addBack(first))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidRestoredAssignmentStateFailsClosed() {
        MySqlSnapshotSplit first = split(ORDERS, "orders-0", null, null);
        assertThatThrownBy(() -> new MySqlSnapshotSplitAssigner(
                        List.of(first), Map.of(first.splitId(), first), Set.of(), 2, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MySqlSnapshotSplitAssigner(
                        List.of(), Map.of("other-id", first), Set.of(), 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MySqlSnapshotSplit split(TableId table, String id, Long low, Long high) {
        return new MySqlSnapshotSplit(id, table, low, high, null);
    }
}
