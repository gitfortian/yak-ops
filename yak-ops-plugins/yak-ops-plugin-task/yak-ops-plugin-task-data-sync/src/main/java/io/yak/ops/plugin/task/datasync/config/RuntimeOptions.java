package io.yak.ops.plugin.task.datasync.config;

import java.time.Duration;
import java.util.Objects;

/**
 * System-owned parallelism and execution settings for one DATA_SYNC Job.
 *
 * <p>Connector-specific settings are declared by their own Connector.
 *
 * @param sourceParallelism Source subtask parallelism
 * @param sinkParallelism Sink subtask parallelism
 * @param checkpointInterval interval between streaming checkpoints
 * @param maxRestarts maximum same-Job restart attempts after a completed checkpoint
 */
public record RuntimeOptions(int sourceParallelism, int sinkParallelism, Duration checkpointInterval, int maxRestarts) {

    public RuntimeOptions {
        Objects.requireNonNull(checkpointInterval, "checkpointInterval");
        if (sourceParallelism < 1 || sourceParallelism > 16 || sinkParallelism < 1 || sinkParallelism > 16) {
            throw new IllegalArgumentException("DATA_SYNC operator parallelism must be between 1 and 16");
        }
        if (checkpointInterval.isZero() || checkpointInterval.isNegative()) {
            throw new IllegalArgumentException("DATA_SYNC checkpoint interval must be positive");
        }
        if (maxRestarts < 0) {
            throw new IllegalArgumentException("DATA_SYNC restart attempts must not be negative");
        }
    }

    /** Returns the system defaults for single-table embedded execution. */
    public static RuntimeOptions defaults() {
        return new RuntimeOptions(1, 1, Duration.ofSeconds(10), 1);
    }
}
