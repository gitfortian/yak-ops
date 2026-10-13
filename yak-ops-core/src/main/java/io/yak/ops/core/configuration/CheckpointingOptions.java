package io.yak.ops.core.configuration;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

import java.time.Duration;

/**
 * Typed configuration options for aligned checkpoint execution.
 *
 * <p>The runtime validates effective values and applies them to the checkpoint
 * coordinator. This class only defines the option contracts.
 *
 * @author weifuwan
 */
public final class CheckpointingOptions {

    /**
     * Base interval between periodic checkpoint triggers.
     *
     * <p>An absent or zero interval disables periodic triggering; negative intervals are invalid.
     */
    public static final Option<Duration> CHECKPOINTING_INTERVAL =
            Options.key("execution.checkpointing.interval").durationType().defaultValue(Duration.ZERO);

    /** Maximum duration of one checkpoint attempt before it must be declined. */
    public static final Option<Duration> CHECKPOINTING_TIMEOUT =
            Options.key("execution.checkpointing.timeout").durationType().defaultValue(Duration.ofMinutes(10));

    /**
     * Minimum idle time between successive checkpoint attempts.
     *
     * <p>Only one checkpoint may be in flight in the current local runtime.
     */
    public static final Option<Duration> MIN_PAUSE_BETWEEN_CHECKPOINTS = Options.key(
                    "execution.checkpointing.min-pause")
            .durationType()
            .defaultValue(Duration.ZERO);

    /** Maximum concurrent checkpoints; the current runtime requires exactly one. */
    public static final Option<Integer> MAX_CONCURRENT_CHECKPOINTS = Options.key(
                    "execution.checkpointing.max-concurrent-checkpoints")
            .intType()
            .defaultValue(1);

    /**
     * Local directory for durably completed checkpoints.
     *
     * <p>Periodic checkpointing requires an explicit directory. Separate jobs must not write
     * concurrently to the same directory; the runtime uses an exclusive lock and atomic replacement.
     */
    public static final Option<String> STATE_DIRECTORY = Options.key(
                    "execution.checkpointing.state-directory")
            .stringType()
            .noDefaultValue();

    /**
     * Whether to resume from the most recently completed checkpoint in the state directory.
     *
     * <p>Restoration requires stable operator UIDs and a compatible topology and parallelism.
     */
    public static final Option<Boolean> RESTORE_LATEST = Options.key(
                    "execution.checkpointing.restore-latest")
            .booleanType()
            .defaultValue(false);

    private CheckpointingOptions() {}
}
