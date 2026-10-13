package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.plugin.task.datasync.resource.RuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.RuntimeTable;
import java.util.Objects;

/**
 * Execution-scoped Sink factory context.
 *
 * <p>The connection binding may contain credentials. This context is never persisted or logged.
 */
public final class SinkFactoryContext {

    private final RuntimeConnection connection;
    private final RuntimeTable source;
    private final RuntimeTable target;

    public SinkFactoryContext(RuntimeConnection connection, RuntimeTable source, RuntimeTable target) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
    }

    public RuntimeConnection connection() {
        return connection;
    }

    public RuntimeTable source() {
        return source;
    }

    public RuntimeTable target() {
        return target;
    }
}
