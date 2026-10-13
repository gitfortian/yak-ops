package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import java.util.Objects;

/**
 * Execution-scoped Sink factory context.
 *
 * <p>The connection binding may contain credentials. This context is never persisted or logged.
 */
public final class SyncSinkContext {

    private final SyncRuntimeConnection connection;
    private final SyncRuntimeTable source;
    private final SyncRuntimeTable target;

    public SyncSinkContext(SyncRuntimeConnection connection, SyncRuntimeTable source, SyncRuntimeTable target) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
    }

    public SyncRuntimeConnection connection() {
        return connection;
    }

    public SyncRuntimeTable source() {
        return source;
    }

    public SyncRuntimeTable target() {
        return target;
    }
}
