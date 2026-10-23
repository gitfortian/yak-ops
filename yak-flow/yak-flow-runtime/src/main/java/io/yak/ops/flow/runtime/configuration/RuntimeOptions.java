package io.yak.ops.flow.runtime.configuration;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

/** Configuration options for the single-JVM runtime implementation. */
public final class RuntimeOptions {

    /**
     * Bounded record-buffer capacity for each downstream subtask.
     *
     * <p>Retains its established configuration key so persisted configuration behaves consistently.
     */
    public static final Option<Integer> CHANNEL_CAPACITY =
            Options.key("execution.local-channel.capacity").intType().defaultValue(64);

    /** Enables the explicit runtime SchemaManager protocol; disabled for existing pipelines. */
    public static final Option<Boolean> SCHEMA_EVOLUTION_ENABLED =
            Options.key("execution.schema-evolution.enabled").booleanType().defaultValue(false);

    private RuntimeOptions() {}
}
