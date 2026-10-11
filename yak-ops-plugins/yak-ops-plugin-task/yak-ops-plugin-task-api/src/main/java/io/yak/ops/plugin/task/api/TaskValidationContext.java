package io.yak.ops.plugin.task.api;

import io.yak.ops.core.types.TableSchema;

/**
 * Trusted, Workspace-scoped read-only metadata supplied by the host application.
 * Plugins must never receive database passwords or mutable DAO objects.
 */
public interface TaskValidationContext {
    /** Resolves an authorized datasource within the caller's Workspace, without exposing credentials. */
    DataSourceInfo dataSource(String id);

    /** Returns physical table metadata for a datasource authorized in the caller's Workspace. */
    TableSchema tableSchema(String id, String database, String schema, String table);

    /** Non-secret provider type and optional database/schema context for validation. */
    record DataSourceInfo(String type, String database, String schema) {}
}
