package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.Objects;

/** Sink construction inputs with sensitive resource bindings excluded from diagnostics. */
public final class TableSinkFactoryContext {

    private final ReadonlyConfig options;
    private final ConnectorResource resource;
    private final TableId sourceTable;
    private final TableSchema sourceSchema;
    private final TableId targetTable;
    private final TableSchema targetSchema;

    public TableSinkFactoryContext(
            ReadonlyConfig options,
            ConnectorResource resource,
            TableId sourceTable,
            TableSchema sourceSchema,
            TableId targetTable,
            TableSchema targetSchema) {
        this.options = Objects.requireNonNull(options, "options");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.sourceTable = Objects.requireNonNull(sourceTable, "sourceTable");
        this.sourceSchema = Objects.requireNonNull(sourceSchema, "sourceSchema");
        this.targetTable = Objects.requireNonNull(targetTable, "targetTable");
        this.targetSchema = Objects.requireNonNull(targetSchema, "targetSchema");
    }

    public ReadonlyConfig options() {
        return options;
    }

    public ConnectorResource resource() {
        return resource;
    }

    public TableId sourceTable() {
        return sourceTable;
    }

    public TableSchema sourceSchema() {
        return sourceSchema;
    }

    public TableId targetTable() {
        return targetTable;
    }

    public TableSchema targetSchema() {
        return targetSchema;
    }

    @Override
    public String toString() {
        return "TableSinkFactoryContext{resources=redacted}";
    }
}
