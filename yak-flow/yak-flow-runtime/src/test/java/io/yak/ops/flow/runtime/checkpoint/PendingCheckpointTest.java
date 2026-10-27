package io.yak.ops.flow.runtime.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checkpoint acknowledgement and durable publication transitions follow Flink's lifecycle. */
class PendingCheckpointTest {

    private static final CheckpointSnapshot.OperatorSubtask SOURCE =
            new CheckpointSnapshot.OperatorSubtask("source", 0);
    private static final CheckpointSnapshot.OperatorSubtask SINK =
            new CheckpointSnapshot.OperatorSubtask("sink", 0);

    @Test
    void waitsForEveryParticipantBeforeDurablePublication() {
        PendingCheckpoint pending = new PendingCheckpoint(17, Set.of(SOURCE, SINK));
        assertFalse(pending.isFullyAcknowledged());
        pending.acknowledgeTask(SOURCE);
        assertEquals(1, pending.numberOfNonAcknowledgedTasks());
        assertThrows(IllegalStateException.class, () -> pending.markPersisted(snapshot(17)));
        assertThrows(IllegalArgumentException.class, () -> pending.acknowledgeTask(SOURCE));
        pending.acknowledgeTask(SINK);
        assertTrue(pending.isFullyAcknowledged());
        pending.markPersisted(snapshot(17));
        assertEquals(PendingCheckpoint.Status.PERSISTED, pending.status());
        assertThrows(IllegalStateException.class, pending::abort);
        pending.complete();
        assertEquals(PendingCheckpoint.Status.COMPLETED, pending.status());
        assertThrows(IllegalStateException.class, pending::complete);
    }

    @Test
    void rejectsUnknownTasksAndCheckpointIdentityMismatch() {
        var pending = new PendingCheckpoint(18, Set.of(SOURCE));
        assertThrows(IllegalArgumentException.class, () -> pending.acknowledgeTask(SINK));
        pending.acknowledgeTask(SOURCE);
        assertThrows(IllegalStateException.class, () -> pending.markPersisted(snapshot(19)));
        pending.abort();
        assertEquals(PendingCheckpoint.Status.ABORTED, pending.status());
        assertThrows(IllegalStateException.class, () -> pending.acknowledgeTask(SOURCE));
    }

    @Test
    void persistedCheckpointCannotAbortAfterExternalCommitFailure() {
        var pending = new PendingCheckpoint(20, Set.of(SINK));
        pending.acknowledgeTask(SINK);
        pending.markPersisted(snapshot(20));
        pending.failCompletion();
        assertEquals(PendingCheckpoint.Status.COMPLETION_FAILED, pending.status());
        assertThrows(IllegalStateException.class, pending::abort);
        assertThrows(IllegalStateException.class, pending::complete);
    }

    private static CheckpointSnapshot snapshot(long id) {
        return new CheckpointSnapshot(
                id, "test-signature", new CheckpointSnapshot.SerializedState(1, new byte[] {1}),
                Map.of(), Map.of(), 100L);
    }
}
