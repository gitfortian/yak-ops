package io.yak.ops.plugin.task.api;

import io.yak.ops.core.types.TableSchema;

/**
 * Trusted, Workspace-scoped read-only metadata supplied by the host application.
 * Plugins must never receive database passwords or mutable DAO objects.
 */
public interface TaskValidationContext {
    DataSourceInfo dataSource(String id);

    TableSchema tableSchema(String id, String database, String schema, String table);

    record DataSourceInfo(String type, String database, String schema) {}
}
