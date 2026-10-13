package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.core.configuration.PipelineOptions;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Selects YakFlow execution mode, checkpoint and restart policy from Source boundedness.
 *
 * <p>Every new Task Instance starts without restoring another execution's checkpoint.
 * YakFlow may restart subtasks within the same Job only after a complete, durable checkpoint.
 * Task platform recovery across process ownership changes remains a separate lifecycle decision.
 */
public final class SyncRuntimePolicy {

    private final SyncRuntimeOptions options;
    private final Path stateRoot;

    public SyncRuntimePolicy(SyncRuntimeOptions options, Path stateRoot) {
        this.options = Objects.requireNonNull(options, "options");
        this.stateRoot =
                Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
    }

    /**
     * Prepares the physical execution settings for one authorized Instance.
     *
     * @param identity trusted Task execution identity
     * @param boundedness boundedness declared by the selected Source factory
     * @return independent YakFlow execution configuration
     */
    public Configuration configure(TaskExecutionIdentity identity, Boundedness boundedness) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(boundedness, "boundedness");
        Path stateDirectory = stateDirectory(identity.instanceId());
        Configuration configuration = new Configuration()
                .set(PipelineOptions.AUTO_GENERATE_UIDS, false)
                .set(CheckpointingOptions.STATE_DIRECTORY, stateDirectory.toString())
                .set(CheckpointingOptions.RESTORE_LATEST, false);

        if (boundedness == Boundedness.BOUNDED) {
            return configuration
                    .set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.BATCH)
                    .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ZERO)
                    .set(ExecutionOptions.MAX_RESTART_ATTEMPTS, 0);
        }

        return configuration
                .set(ExecutionOptions.RUNTIME_MODE, RuntimeExecutionMode.STREAMING)
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, options.cdcCheckpointInterval())
                .set(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS, 1)
                .set(ExecutionOptions.MAX_RESTART_ATTEMPTS, options.cdcMaxRestarts());
    }

    /** Returns the configured number of physical Source subtasks. */
    public int sourceParallelism() {
        return options.sourceParallelism();
    }

    /**
     * Returns the physical Sink parallelism consistent with current changelog routing.
     *
     * @param producesChangelog whether the Source may emit key-aware change events
     * @return configured Sink parallelism
     */
    public int sinkParallelism(boolean producesChangelog) {
        if (producesChangelog && options.sinkParallelism() != 1) {
            throw new UnsupportedOperationException(
                    "DATA_SYNC changelog requires a single Sink writer until keyed routing is configured");
        }
        return options.sinkParallelism();
    }

    /** Returns the validated Connector-specific execution settings. */
    public SyncRuntimeOptions options() {
        return options;
    }

    private Path stateDirectory(String instanceId) {
        if (!instanceId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid DATA_SYNC Instance identifier for checkpoint storage");
        }
        return stateRoot.resolve(instanceId);
    }
}
