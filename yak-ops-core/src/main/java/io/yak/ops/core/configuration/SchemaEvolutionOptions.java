package io.yak.ops.core.configuration;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

/** Shared opt-in capability used by the Runtime and MySQL CDC Source. */
public final class SchemaEvolutionOptions {

    public static final Option<Boolean> ENABLED =
            Options.key("execution.schema-evolution.enabled").booleanType().defaultValue(false);

    private SchemaEvolutionOptions() {}
}
