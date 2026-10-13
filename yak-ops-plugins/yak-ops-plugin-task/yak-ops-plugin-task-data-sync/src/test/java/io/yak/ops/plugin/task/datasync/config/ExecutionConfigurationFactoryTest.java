package io.yak.ops.plugin.task.datasync.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.ExecutionOptions;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.connector.cdc.MySqlCdcOptions;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the execution configuration, Connector options and checkpoint ownership. */
class ExecutionConfigurationFactoryTest {

    @TempDir
    Path stateRoot;

    private static final TaskExecutionIdentity FIRST =
            new TaskExecutionIdentity("ws", "definition", 1, "instance-100", "attempt-1");

    @Test
    void boundedSourceRunsWithoutPeriodicCheckpointsOrAutomaticRestarts() {
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(RuntimeOptions.defaults(), stateRoot);
        var configuration = factory.create(FIRST, Boundedness.BOUNDED);

        assertEquals(RuntimeExecutionMode.BATCH, configuration.get(ExecutionOptions.RUNTIME_MODE));
        assertEquals(Duration.ZERO, configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(0, configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS));
        assertEquals(false, configuration.get(CheckpointingOptions.RESTORE_LATEST));
        assertEquals(
                stateRoot.resolve(FIRST.instanceId()).toAbsolutePath().toString(),
                configuration.get(CheckpointingOptions.STATE_DIRECTORY));
    }

    @Test
    void continuousSourceUsesCheckpointGatedSameJobRestartsAndTypedCdcOptions() {
        RuntimeOptions options = new RuntimeOptions(2, 1, Duration.ofMillis(500), 3);
        var cdc = new MySqlCdcOptions(512, 64).configuration();
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(options, stateRoot, cdc);
        var configuration = factory.create(FIRST, Boundedness.CONTINUOUS_UNBOUNDED);

        assertEquals(RuntimeExecutionMode.STREAMING, configuration.get(ExecutionOptions.RUNTIME_MODE));
        assertEquals(Duration.ofMillis(500), configuration.get(CheckpointingOptions.CHECKPOINTING_INTERVAL));
        assertEquals(1, configuration.get(CheckpointingOptions.MAX_CONCURRENT_CHECKPOINTS));
        assertEquals(3, configuration.get(ExecutionOptions.MAX_RESTART_ATTEMPTS));
        assertEquals(false, configuration.get(CheckpointingOptions.RESTORE_LATEST));
        assertEquals(512, configuration.get(MySqlCdcOptions.SNAPSHOT_CHUNK_SIZE));
        assertEquals(64, configuration.get(MySqlCdcOptions.QUEUE_CAPACITY));
        assertEquals(2, options.sourceParallelism());
        assertEquals(1, options.sinkParallelism());
    }

    @Test
    void isolatesDifferentInstancesAndRetainsStateDirectoryAcrossInternalAttempts() {
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(RuntimeOptions.defaults(), stateRoot);
        TaskExecutionIdentity sameInstance =
                new TaskExecutionIdentity("ws", "definition", 1, "instance-100", "attempt-2");
        TaskExecutionIdentity nextInstance =
                new TaskExecutionIdentity("ws", "definition", 1, "instance-101", "attempt-3");

        String first = factory.create(FIRST, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.STATE_DIRECTORY);
        String resumed = factory.create(sameInstance, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.STATE_DIRECTORY);
        String next = factory.create(nextInstance, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.STATE_DIRECTORY);

        assertEquals(first, resumed);
        assertNotEquals(first, next);
        assertEquals(false, factory.create(nextInstance, Boundedness.CONTINUOUS_UNBOUNDED)
                .get(CheckpointingOptions.RESTORE_LATEST));
    }

    @Test
    void rejectsUnsafeInstanceIdentifiers() {
        ExecutionConfigurationFactory factory = new ExecutionConfigurationFactory(RuntimeOptions.defaults(), stateRoot);
        TaskExecutionIdentity unsafe =
                new TaskExecutionIdentity("ws", "definition", 1, "../outside", "attempt");

        assertThrows(IllegalArgumentException.class, () -> factory.create(unsafe, Boundedness.CONTINUOUS_UNBOUNDED));
    }

    @Test
    void rejectsInvalidSystemOwnedAndConnectorSettings() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeOptions(0, 1, Duration.ofSeconds(10), 1));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeOptions(1, 17, Duration.ofSeconds(10), 1));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeOptions(1, 1, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeOptions(1, 1, Duration.ofSeconds(10), -1));
        assertThrows(IllegalArgumentException.class, () -> new MySqlCdcOptions(0, 256));
        assertThrows(IllegalArgumentException.class, () -> new MySqlCdcOptions(1024, 10001));
        assertTrue(RuntimeOptions.defaults().maxRestarts() > 0);
    }
}
