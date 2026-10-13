package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/**
 * Source construction inputs provided by the execution boundary.
 *
 * <p>Resources and runtime options are not persisted or included in diagnostics.
 */
public final class TableSourceFactoryContext {

    private final ReadonlyConfig options;
    private final ConnectorResource resource;
    private final TableId table;
    private final TableSchema schema;
    private final Configuration configuration;
    private final ConnectorExecutionIdentity identity;

    public TableSourceFactoryContext(
            ReadonlyConfig options,
            ConnectorResource resource,
            TableId table,
            TableSchema schema,
            Configuration configuration,
            ConnectorExecutionIdentity identity) {
        this.options = Objects.requireNonNull(options, "options");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.table = Objects.requireNonNull(table, "table");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration"));
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    public ReadonlyConfig options() {
        return options;
    }

    public ConnectorResource resource() {
        return resource;
    }

    public TableId table() {
        return table;
    }

    public TableSchema schema() {
        return schema;
    }

    public Configuration configuration() {
        return new Configuration(configuration);
    }

    public ConnectorExecutionIdentity identity() {
        return identity;
    }

    @Override
    public String toString() {
        return "TableSourceFactoryContext{resources=redacted}";
    }
}
