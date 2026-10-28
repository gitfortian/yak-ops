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

    /** The task acknowledgement result, following Flink's PendingCheckpoint. */
    public enum TaskAcknowledgeResult {
        SUCCESS, // successful acknowledge of the task
        DUPLICATE, // acknowledge message is a duplicate
        UNKNOWN, // unknown task acknowledged
        DISCARDED // pending checkpoint has been discarded
    }

    public enum Status {
        PENDING,
        PERSISTED,
        COMPLETED,
        ABORTED,
        COMPLETION_FAILED
    }

    private final long checkpointId;
    private final Set<CheckpointSnapshot.OperatorSubtask> notYetAcknowledgedTasks;
    private final Set<CheckpointSnapshot.OperatorSubtask> acknowledgedTasks = new HashSet<>();
    private Status status = Status.PENDING;

    public PendingCheckpoint(long checkpointId, Set<CheckpointSnapshot.OperatorSubtask> tasksToAcknowledge) {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("Checkpoint ID must be positive");
        }
        checkpointIdNotNull(tasksToAcknowledge);
        this.checkpointId = checkpointId;
        this.notYetAcknowledgedTasks = new HashSet<>(tasksToAcknowledge);
    }

    private static void checkpointIdNotNull(Set<CheckpointSnapshot.OperatorSubtask> tasks) {
        Objects.requireNonNull(tasks, "Checkpoint acknowledgement plan");
        for (CheckpointSnapshot.OperatorSubtask task : tasks) {
            Objects.requireNonNull(task, "Checkpoint plan task");
        }
    }

    public long getCheckpointID() {
        return checkpointId;
    }

    public Status status() {
        return status;
    }

    public int getNumberOfNonAcknowledgedTasks() {
        return notYetAcknowledgedTasks.size();
    }

    public boolean isFullyAcknowledged() {
        return notYetAcknowledgedTasks.isEmpty();
    }

    /** Processes the acknowledgement and distinguishes duplicate, unknown and discarded tasks. */
    public TaskAcknowledgeResult acknowledgeTask(CheckpointSnapshot.OperatorSubtask task) {
        Objects.requireNonNull(task, "task");
        if (status != Status.PENDING) {
            return TaskAcknowledgeResult.DISCARDED;
        }
        if (!notYetAcknowledgedTasks.remove(task)) {
            return acknowledgedTasks.contains(task)
                    ? TaskAcknowledgeResult.DUPLICATE
                    : TaskAcknowledgeResult.UNKNOWN;
        }
        acknowledgedTasks.add(task);
        return TaskAcknowledgeResult.SUCCESS;
    }

    /**
     * Finalizes the checkpoint after all acknowledgements. Only the completed store can
     * durably publish the state; a failed publication keeps this checkpoint pending.
     */
    public CheckpointSnapshot finalizeCheckpoint(CheckpointSnapshot snapshot, CompletedCheckpointStore store)
            throws java.io.IOException {
        require(Status.PENDING);
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(store, "store");
        if (snapshot.checkpointId() != checkpointId || !isFullyAcknowledged()) {
            throw new IllegalStateException("Cannot finalize an incompletely acknowledged checkpoint");
        }
        store.save(snapshot);
        status = Status.PERSISTED;
        return snapshot;
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
            throw new IllegalStateException("Checkpoint " + checkpointId + " cannot transition from " + status
                    + " (expected " + expected + ")");
        }
    }
}
