package io.yak.ops.flow.runtime.checkpoint;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A checkpoint attempt that is not yet fully acknowledged by its physical tasks.
 *
 * <p>Like Flink's PendingCheckpoint, the coordinator owns acknowledgements until
 * all participating tasks confirm an aligned state. Persisting the completed
 * checkpoint is a separate transition from notifying external committers.
 * Once persisted, a failed completion callback cannot roll the checkpoint back.
 */
public final class PendingCheckpoint {

    public enum Status {
        PENDING,
        PERSISTED,
        COMPLETED,
        ABORTED,
        COMPLETION_FAILED
    }

    private final long checkpointId;
    private final Set<CheckpointSnapshot.OperatorSubtask> notYetAcknowledgedTasks;
    private Status status = Status.PENDING;

    public PendingCheckpoint(
            long checkpointId, Set<CheckpointSnapshot.OperatorSubtask> tasksToAcknowledge) {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("Checkpoint ID must be positive");
        }
        checkpointIdNotNull(tasksToAcknowledge);
        this.checkpointId = checkpointId;
        this.notYetAcknowledgedTasks = new HashSet<>(tasksToAcknowledge);
    }

    private static void checkpointIdNotNull(Set<CheckpointSnapshot.OperatorSubtask> tasks) {
        Objects.requireNonNull(tasks, "Checkpoint acknowledgement plan");
        if (tasks.contains(null)) {
            throw new IllegalArgumentException("Checkpoint plan contains a null task");
        }
    }

    public long checkpointId() {
        return checkpointId;
    }

    public Status status() {
        return status;
    }

    public int numberOfNonAcknowledgedTasks() {
        return notYetAcknowledgedTasks.size();
    }

    public boolean isFullyAcknowledged() {
        return notYetAcknowledgedTasks.isEmpty();
    }

    /** Each physical task can acknowledge exactly once, and only during the pending phase. */
    public void acknowledgeTask(CheckpointSnapshot.OperatorSubtask task) {
        require(Status.PENDING);
        if (!notYetAcknowledgedTasks.remove(Objects.requireNonNull(task, "task"))) {
            throw new IllegalArgumentException("Unexpected or duplicate checkpoint task acknowledgement: " + task);
        }
    }

    /**
     * Records the durable publication point. Only the storage layer, after successful
     * sync and atomic publication, may cause this transition.
     */
    public void markPersisted(CheckpointSnapshot snapshot) {
        require(Status.PENDING);
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.checkpointId() != checkpointId || !isFullyAcknowledged()) {
            throw new IllegalStateException("Cannot persist an incompletely acknowledged checkpoint");
        }
        status = Status.PERSISTED;
    }

    /** All external committers and coordinator callbacks have finished successfully. */
    public void complete() {
        require(Status.PERSISTED);
        status = Status.COMPLETED;
    }

    /** A failed attempt before durable publication may be discarded. */
    public void abort() {
        require(Status.PENDING);
        status = Status.ABORTED;
    }

    /**
     * A persisted checkpoint cannot be aborted. Recovery must replay it and
     * resolve prepared external transactions after a completion callback failure.
     */
    public void failCompletion() {
        require(Status.PERSISTED);
        status = Status.COMPLETION_FAILED;
    }

    private void require(Status expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Checkpoint " + checkpointId + " cannot transition from " + status + " (expected " + expected + ")");
        }
    }
}
