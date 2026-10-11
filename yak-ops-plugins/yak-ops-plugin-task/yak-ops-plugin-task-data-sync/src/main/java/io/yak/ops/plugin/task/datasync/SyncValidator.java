package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationContext;
import java.util.Set;

/** Validates physical DATA_SYNC parameters without secrets or Task lifecycle behavior. */
final class SyncValidator {
    private static final Set<String> REALTIME_TARGETS = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");

    private SyncValidator() {}

    static SyncParameters validate(SyncParameters p, TaskValidationContext context) {
        var source = context.dataSource(p.sourceDataSourceId());
        var target = context.dataSource(p.targetDataSourceId());
        if (p.syncType() == DataSyncType.REALTIME
                && (!"MYSQL".equals(source.type()) || !REALTIME_TARGETS.contains(target.type()))) {
            throw new TaskPluginException("Unsupported realtime datasource types");
        }
        String sourceDb = bound(source.database(), p.sourceDatabase());
        String sourceSchema = bound(source.schema(), p.sourceSchema());
        String targetDb = bound(target.database(), p.targetDatabase());
        String targetSchema = bound(target.schema(), p.targetSchema());
        TableSchema from = context.tableSchema(p.sourceDataSourceId(), sourceDb, sourceSchema, p.sourceTable());
        TableSchema to = context.tableSchema(p.targetDataSourceId(), targetDb, targetSchema, p.targetTable());
        requireCompatible(from, to, p.syncType() == DataSyncType.REALTIME || p.writeMode() == DataSyncWriteMode.UPSERT);
        return new SyncParameters(p.syncType(), p.writeMode(), p.sourceDataSourceId(), sourceDb, sourceSchema,
                p.sourceTable(), p.targetDataSourceId(), targetDb, targetSchema, p.targetTable(),
                p.runtimeConfig(), p.realtimeConfig(), p.retryPolicy());
    }

    private static String bound(String configured, String requested) {
        return configured != null && !configured.isBlank() ? configured : requested;
    }


    private static void requireCompatible(TableSchema source, TableSchema target, boolean requireKeys) {
        java.util.Map<String, io.yak.ops.core.types.Column> sourceColumns = columns(source);
        java.util.Map<String, io.yak.ops.core.types.Column> targetColumns = columns(target);
        for (var entry : sourceColumns.entrySet()) {
            var other = targetColumns.get(entry.getKey());
            if (other == null || !io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility
                    .isCompatible(entry.getValue(), other)) {
                throw new TaskPluginException("Source and target table schemas are incompatible");
            }
        }
        for (var entry : targetColumns.entrySet()) {
            if (!sourceColumns.containsKey(entry.getKey()) && !entry.getValue().nullable()) {
                throw new TaskPluginException("Target contains an unmatched required column");
            }
        }
        if (requireKeys) {
            Set<String> sourceKeys = keys(source.primaryKeys());
            if (sourceKeys.isEmpty() || !sourceKeys.equals(keys(target.primaryKeys()))) {
                throw new TaskPluginException("Realtime and upsert require matching primary keys");
            }
        }
    }

    private static java.util.Map<String, io.yak.ops.core.types.Column> columns(TableSchema schema) {
        java.util.Map<String, io.yak.ops.core.types.Column> result = new java.util.LinkedHashMap<>();
        for (var column : schema.columns()) {
            String key = column.name().toLowerCase(java.util.Locale.ROOT);
            if (result.putIfAbsent(key, column) != null) {
                throw new TaskPluginException("Ambiguous table column names");
            }
        }
        return result;
    }

    private static Set<String> keys(java.util.List<String> keys) {
        Set<String> normalized = new java.util.HashSet<>();
        for (String key : keys) normalized.add(key.toLowerCase(java.util.Locale.ROOT));
        return normalized;
    }

}
