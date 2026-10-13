package io.yak.ops.plugin.task.datasync.pipeline;

import java.time.Duration;
import java.util.Objects;

/**
 * System-owned execution settings for a DATA_SYNC pipeline.
 *
 * <p>These values belong to application configuration, not the persisted Task Definition.
 * The snapshot chunk size and CDC queue capacity configure Connector creation, while parallelism
 * and recovery settings are applied through YakFlow's typed execution configuration.
 *
 * @param sourceParallelism number of Source subtasks
 * @param sinkParallelism number of Sink subtasks
 * @param snapshotChunkSize maximum approximate rows per MySQL CDC snapshot chunk
 * @param cdcQueueCapacity number of pending CDC records before reader backpressure
 * @param cdcCheckpointInterval interval between durable CDC checkpoints
 * @param cdcMaxRestarts maximum same-Job restarts from completed checkpoints
 */
public record SyncRuntimeOptions(
        int sourceParallelism,
        int sinkParallelism,
        int snapshotChunkSize,
        int cdcQueueCapacity,
        Duration cdcCheckpointInterval,
        int cdcMaxRestarts) {

    public SyncRuntimeOptions {
        Objects.requireNonNull(cdcCheckpointInterval, "cdcCheckpointInterval");
        if (sourceParallelism < 1 || sourceParallelism > 16 || sinkParallelism < 1 || sinkParallelism > 16) {
            throw new IllegalArgumentException("DATA_SYNC operator parallelism must be between 1 and 16");
        }
        if (snapshotChunkSize < 1 || snapshotChunkSize > 100_000) {
            throw new IllegalArgumentException("MySQL CDC snapshot chunk size must be between 1 and 100000");
        }
        if (cdcQueueCapacity < 1 || cdcQueueCapacity > 10_000) {
            throw new IllegalArgumentException("MySQL CDC queue capacity must be between 1 and 10000");
        }
        if (cdcCheckpointInterval.isZero() || cdcCheckpointInterval.isNegative()) {
            throw new IllegalArgumentException("DATA_SYNC CDC checkpoint interval must be positive");
        }
        if (cdcMaxRestarts < 0) {
            throw new IllegalArgumentException("DATA_SYNC CDC restart attempts must not be negative");
        }
    }

    /** Returns the system defaults for a single-table local execution. */
    public static SyncRuntimeOptions defaults() {
        return new SyncRuntimeOptions(1, 1, 1024, 256, Duration.ofSeconds(10), 1);
    }
}
