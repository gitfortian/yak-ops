package io.yak.ops.core.configuration;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;

/** Shared execution defaults that do not contain state of a particular job attempt. */
public final class CoreOptions {

    /** Default parallelism for operators without an explicit value; resolved during graph planning. */
    public static final Option<Integer> DEFAULT_PARALLELISM =
            Options.key("parallelism.default").intType().defaultValue(1);

    private CoreOptions() {}
}
