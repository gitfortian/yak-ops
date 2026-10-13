package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.resource.RuntimeConnection;
import io.yak.ops.plugin.task.datasync.resource.RuntimeTable;
import java.util.Objects;

/**
 * Execution-scoped Source factory context.
 *
 * <p>The connection binding may contain credentials. This context is never persisted or logged.
 */
public final class SourceFactoryContext {

    private final RuntimeConnection connection;
    private final RuntimeTable table;
    private final Configuration configuration;
    private final TaskExecutionIdentity identity;

    public SourceFactoryContext(
            RuntimeConnection connection,
            RuntimeTable table,
            Configuration configuration,
            TaskExecutionIdentity identity) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.table = Objects.requireNonNull(table, "table");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    public RuntimeConnection connection() {
        return connection;
    }

    public RuntimeTable table() {
        return table;
    }

    public Configuration configuration() {
        return configuration;
    }

    public TaskExecutionIdentity identity() {
        return identity;
    }
}
