package io.yak.ops.plugin.task.datasync.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies bounded and continuous execution settings and their checkpoint ownership. */
class SyncRuntimePolicyTest {

    @TempDir
    Path stateRoot;

    private static final TaskExecutionIdentity FIRST =
            new TaskExecutionIdentity("ws", "definition", 1, "instance-100", "attempt-1");

    @Test
    void boundedSourceRunsWithoutPeriodicCheckpointsOrAutomaticRestarts() {
        SyncRuntimePolicy policy = new SyncRuntimePolicy(SyncRuntimeOptions.defaults(), stateRoot);
        var configuration = policy.configure(FIRST, Boundedness.BOUNDED);

        assertEquals(RuntimeExecutionMode.BATCH, configuration.get(ExecutionOptions.RUNTIME_MODE));
        assertEquals(Duration.ZERO, configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(0, configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS));
        assertEquals(false, configuration.get(CheckpointingOptions.RESTORE_LATEST));
        assertEquals(
                stateRoot.resolve(FIRST.instanceId()).toAbsolutePath().toString(),
                configuration.get(CheckpointingOptions.STATE_DIRECTORY));
    }

    @Test
    void continuousSourceUsesCheckpointGatedSameJobRestarts() {
        SyncRuntimeOptions options = new SyncRuntimeOptions(2, 1, 512, 64, Duration.ofMillis(500), 3);
        SyncRuntimePolicy policy = new SyncRuntimePolicy(options, stateRoot);
        var configuration = policy.configure(FIRST, Boundedness.CONTINUOUS_UNBOUNDED);

        assertEquals(RuntimeExecutionMode.STREAMING, configuration.get(ExecutionOptions.RUNTIME_MODE));
        assertEquals(Duration.ofMillis(500), configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(1, configuration.get(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS));
        assertEquals(3, configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS));
        assertEquals(false, configuration.get(CheckpointingOptions.RESTORE_LATEST));
        assertEquals(2, policy.sourceParallelism());
        assertEquals(1, policy.sinkParallelism(true));
        assertEquals(512, policy.options().snapshotChunkSize());
        assertEquals(64, policy.options().cdcQueueCapacity());
    }

    @Test
    void isolatesDifferentInstancesAndRetainsStateDirectoryAcrossInternalAttempts() {
        SyncRuntimePolicy policy = new SyncRuntimePolicy(SyncRuntimeOptions.defaults(), stateRoot);
        TaskExecutionIdentity sameInstance =
                new TaskExecutionIdentity("ws", "definition", 1, "instance-100", "attempt-2");
        TaskExecutionIdentity nextInstance =
                new TaskExecutionIdentity("ws", "definition", 1, "instance-101", "attempt-3");

        String first = policy.configure(FIRST, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.STATE_DIRECTORY);
        String resumed = policy.configure(sameInstance, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.STATE_DIRECTORY);
        String next = policy.configure(nextInstance, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.STATE_DIRECTORY);

        assertEquals(first, resumed);
        assertNotEquals(first, next);
        assertEquals(false, policy.configure(nextInstance, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.RESTORE_LATEST));
    }

    @Test
    void rejectsUnsafeInstanceIdentifiersAndChangelogWriterScaling() {
        SyncRuntimeOptions options = new SyncRuntimeOptions(2, 2, 1024, 256, Duration.ofSeconds(10), 1);
        SyncRuntimePolicy policy = new SyncRuntimePolicy(options, stateRoot);
        TaskExecutionIdentity unsafe =
                new TaskExecutionIdentity("ws", "definition", 1, "../outside", "attempt");

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.configure(unsafe, Boundedness.CONTINUOUS_UNBOUNDED));
        assertThrows(UnsupportedOperationException.class, () -> policy.sinkParallelism(true));
        assertEquals(2, policy.sinkParallelism(false));
    }

    @Test
    void rejectsInvalidSystemOwnedRuntimeSettings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncRuntimeOptions(0, 1, 1024, 256, Duration.ofSeconds(10), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncRuntimeOptions(1, 17, 1024, 256, Duration.ofSeconds(10), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncRuntimeOptions(1, 1, 0, 256, Duration.ofSeconds(10), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncRuntimeOptions(1, 1, 1024, 10001, Duration.ofSeconds(10), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncRuntimeOptions(1, 1, 1024, 256, Duration.ZERO, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncRuntimeOptions(1, 1, 1024, 256, Duration.ofSeconds(10), -1));
        assertTrue(SyncRuntimeOptions.defaults().cdcMaxRestarts() > 0);
    }
}
