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

    // SCHEMA_CHECK
}
