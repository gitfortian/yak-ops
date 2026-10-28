package io.yak.ops.flow.runtime.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checkpoint acknowledgement and durable publication transitions follow Flink's lifecycle. */
class PendingCheckpointTest {

    private static final CheckpointSnapshot.OperatorSubtask SOURCE =
            new CheckpointSnapshot.OperatorSubtask("source", 0);
    private static final CheckpointSnapshot.OperatorSubtask SINK =
            new CheckpointSnapshot.OperatorSubtask("sink", 0);

    @Test
    void waitsForEveryParticipantBeforeDurablePublication() throws Exception {
        PendingCheckpoint pending = new PendingCheckpoint(17, Set.of(SOURCE, SINK));
        assertFalse(pending.isFullyAcknowledged());
        assertEquals(PendingCheckpoint.TaskAcknowledgeResult.SUCCESS, pending.acknowledgeTask(SOURCE));
        assertEquals(1, pending.getNumberOfNonAcknowledgedTasks());
        assertThrows(IllegalStateException.class, () -> pending.finalizeCheckpoint(snapshot(17), store()));
        assertEquals(PendingCheckpoint.TaskAcknowledgeResult.DUPLICATE, pending.acknowledgeTask(SOURCE));
        pending.acknowledgeTask(SINK);
        assertTrue(pending.isFullyAcknowledged());
        pending.finalizeCheckpoint(snapshot(17), store());
        assertEquals(PendingCheckpoint.Status.PERSISTED, pending.status());
        assertThrows(IllegalStateException.class, pending::abort);
        pending.complete();
        assertEquals(PendingCheckpoint.Status.COMPLETED, pending.status());
        assertThrows(IllegalStateException.class, pending::complete);
    }

    @Test
    void rejectsUnknownTasksAndCheckpointIdentityMismatch() throws Exception {
        var pending = new PendingCheckpoint(18, Set.of(SOURCE));
        assertEquals(PendingCheckpoint.TaskAcknowledgeResult.UNKNOWN, pending.acknowledgeTask(SINK));
        pending.acknowledgeTask(SOURCE);
        assertThrows(IllegalStateException.class, () -> pending.finalizeCheckpoint(snapshot(19), store()));
        pending.abort();
        assertEquals(PendingCheckpoint.Status.ABORTED, pending.status());
        assertEquals(PendingCheckpoint.TaskAcknowledgeResult.DISCARDED, pending.acknowledgeTask(SOURCE));
    }

    @Test
    void persistedCheckpointCannotAbortAfterExternalCommitFailure() throws Exception {
        var pending = new PendingCheckpoint(20, Set.of(SINK));
        pending.acknowledgeTask(SINK);
        pending.finalizeCheckpoint(snapshot(20), store());
        pending.failCompletion();
        assertEquals(PendingCheckpoint.Status.COMPLETION_FAILED, pending.status());
        assertThrows(IllegalStateException.class, pending::abort);
        assertThrows(IllegalStateException.class, pending::complete);
    }

    private static CompletedCheckpointStore store() {
        return new CompletedCheckpointStore() {
            @Override
            public Optional<CheckpointSnapshot> loadLatest(String graphSignature) {
                return Optional.empty();
            }

            @Override
            public void save(CheckpointSnapshot snapshot) {}

            @Override
            public void close() {}
        };
    }

    private static CheckpointSnapshot snapshot(long id) {
        return new CheckpointSnapshot(
                id, "test-signature", new CheckpointSnapshot.SerializedState(1, new byte[] {1}),
                Map.of(), Map.of(), 100L);
    }
}
