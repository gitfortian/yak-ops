package io.yak.ops.plugin.task.datasync.validation;

import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.sink.JdbcMetadataApplier;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;

/** Read-only constraints shared by schema preview, publication and execution planning. */
public final class SchemaValidator {
    private SchemaValidator() {}

    /**
     * Builds a complete desired target event from an authorized source table.
     *
     * @param source physical source metadata
     * @param target authorized target datasource and revision
     * @param table resolved target identity
     * @return validated target creation event
     */
    public static CreateTableEvent createEvent(DataSourceTable source, DataSourceInfo target, TableId table) {
        validateNamespace(target, table);
        if (!source.columnFeatures().isEmpty()) {
            throw new IllegalArgumentException(
                    "Schema preparation requires plain source columns without defaults, identities or generated values");
        }
        CreateTableEvent event =
                new CreateTableEvent(table, source.schema(), source.tableComment(), source.columnComments());
        ddl(target.type(), event);
        return event;
    }

    /**
     * Requires explicit target namespaces without parsing or quoting user-supplied SQL paths.
     *
     * @param target authorized target datasource
     * @param table resolved target table
     */
    public static void validateNamespace(DataSourceInfo target, TableId table) {
        if (table.catalog() == null
                || ("POSTGRE_SQL".equals(target.type()) && table.schema() == null)
                || ("MYSQL".equals(target.type()) && table.schema() != null)) {
            throw new IllegalArgumentException("Schema preparation requires an explicit target namespace");
        }
    }

    /**
     * Renders the execution DDL using only the resolved vendor type and expected event.
     *
     * @param targetType resolved datasource type
     * @param event complete expected target structure
     * @return validated ordered statements; no driver or database is accessed
     */
    public static JdbcDdlPlan ddl(String targetType, CreateTableEvent event) {
        String prefix =
                switch (targetType) {
                    case "MYSQL" -> "jdbc:mysql:";
                    case "POSTGRE_SQL" -> "jdbc:postgresql:";
                    default -> throw new IllegalArgumentException("Schema preparation target is not supported");
                };
        return JdbcMetadataApplier.plan(event, JdbcFactoryLoader.loadDialect(prefix));
    }

    /**
     * Checks an existing table without modifying it or accepting an incomplete write projection.
     *
     * @param event complete expected structure
     * @param actual current physical target metadata
     */
    public static void validateTarget(CreateTableEvent event, DataSourceTable actual) {
        if (!event.tableId().equals(actual.table()) || !actual.columnFeatures().isEmpty()) {
            throw new IllegalArgumentException("Target physical identity or column features are incompatible");
        }
        JdbcMetadataApplier.validateSchema(event, actual.schema());
        JdbcMetadataApplier.validateComments(event, actual.tableComment(), actual.columnComments());
    }
}
