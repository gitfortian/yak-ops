package io.yak.ops.connector.cdc.base.source.assigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.base.source.meta.split.SnapshotSplit;
import io.yak.ops.core.data.TableId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies reusable CDC scheduling without a database or MySQL-specific types. */
class SnapshotSplitAssignerTest {

    private static final TableId TABLE = new TableId("db", null, "orders");

    @Test
    void tracksProgressiveDiscoveryAssignmentAndCompletion() {
        var assigner = new SnapshotSplitAssigner<TestSplit>(3);
        assigner.append(List.of(new TestSplit("first")), false);
        assertFalse(assigner.allFinished());
        assertEquals(new TestSplit("first"), assigner.next());
        assertFalse(assigner.allFinished());
        assigner.complete("first");
        assigner.append(List.of(new TestSplit("second")), true);
        assertFalse(assigner.allFinished());
        assertEquals(new TestSplit("second"), assigner.next());
        assigner.complete("second");
        assigner.complete("second");
        assertTrue(assigner.allFinished());
        assertEquals(2, assigner.total());
        assertEquals(Set.of("first", "second"), assigner.finished());
    }

    @Test
    void preservesAssignedAndPendingSplitsAcrossRestoreAndReaderFailure() {
        var original = new SnapshotSplitAssigner<TestSplit>(3);
        original.append(List.of(new TestSplit("a"), new TestSplit("b")), false);
        TestSplit assigned = original.next();
        var restored = new SnapshotSplitAssigner<>(
                original.pending(), original.assigned(), original.finished(), original.total(), original.planned(), 3);
        assertEquals(Map.of("a", assigned), restored.assigned());
        restored.addBack(assigned);
        assertEquals(assigned, restored.next());
        assertEquals(new TestSplit("b"), restored.next());
        restored.append(List.of(), true);
        restored.complete("a");
        restored.complete("b");
        assertTrue(restored.allFinished());
    }

    @Test
    void rejectsDuplicatePlansInvalidRecoveryAndUnownedCompletion() {
        var assigner = new SnapshotSplitAssigner<TestSplit>(2);
        assigner.append(List.of(new TestSplit("a")), false);
        assertThrows(IllegalArgumentException.class, () -> assigner.append(List.of(new TestSplit("a")), true));
        assertThrows(IllegalStateException.class, () ->
                assigner.append(List.of(new TestSplit("b"), new TestSplit("c")), true));
        assertEquals(1, assigner.total());
        assertThrows(IllegalArgumentException.class, () -> assigner.complete("a"));
        assertThrows(IllegalArgumentException.class, () -> assigner.addBack(new TestSplit("a")));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotSplitAssigner<>(
                List.of(new TestSplit("a")), Map.of("a", new TestSplit("a")), Set.of(), 2, false, 2));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotSplitAssigner<>(
                List.of(), Map.of("wrong", new TestSplit("a")), Set.of(), 1, false, 2));
    }

    private record TestSplit(String splitId) implements SnapshotSplit {
        @Override
        public TableId tableId() {
            return TABLE;
        }
    }
}
