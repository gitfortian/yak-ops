package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.pipeline.SyncRuntimeOptions;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import java.util.Objects;

/**
 * Execution-scoped Source factory context.
 *
 * <p>The connection binding may contain credentials. This context is never persisted or logged.
 */
public final class SyncSourceContext {

    private final SyncRuntimeConnection connection;
    private final SyncRuntimeTable table;
    private final Configuration configuration;
    private final TaskExecutionIdentity identity;
    private final SyncRuntimeOptions runtimeOptions;

    public SyncSourceContext(
            SyncRuntimeConnection connection,
            SyncRuntimeTable table,
            Configuration configuration,
            TaskExecutionIdentity identity,
            SyncRuntimeOptions runtimeOptions) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.table = Objects.requireNonNull(table, "table");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.runtimeOptions = Objects.requireNonNull(runtimeOptions, "runtimeOptions");
    }

    public SyncRuntimeConnection connection() {
        return connection;
    }

    public SyncRuntimeTable table() {
        return table;
    }

    public Configuration configuration() {
        return configuration;
    }

    public TaskExecutionIdentity identity() {
        return identity;
    }

    public SyncRuntimeOptions runtimeOptions() {
        return runtimeOptions;
    }
}
