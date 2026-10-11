package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.connector.jdbc.database.JdbcSchemaCompatibility;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.sink.jdbc.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import io.yak.ops.plugin.task.datasync.source.jdbc.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.mysqlcdc.MySqlCdcSourceParameters;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validates product-level pipeline semantics against authorized physical table metadata. */
final class SyncValidator {
    private static final Set<String> JDBC_TYPES = Set.of("MYSQL", "POSTGRESQL", "POSTGRE_SQL", "PGSQL", "ORACLE");

    private SyncValidator() {}

    static SyncParameters validate(SyncParameters parameters, TaskValidationContext context) {
        parameters.validate();
        SourceParameters source = parameters.source();
        JdbcSinkParameters sink = (JdbcSinkParameters) parameters.sink();
        var fromDatasource = context.dataSource(source.datasourceId());
        var toDatasource = context.dataSource(sink.datasourceId());
        String fromType = fromDatasource.type().toUpperCase(Locale.ROOT);
        String toType = toDatasource.type().toUpperCase(Locale.ROOT);
        if (!JDBC_TYPES.contains(fromType) || !JDBC_TYPES.contains(toType)) {
            throw new TaskPluginException("Unsupported DATA_SYNC datasource type");
        }
        if (parameters.syncType() == DataSyncType.REALTIME && !"MYSQL".equals(fromType)) {
            throw new TaskPluginException("MySQL CDC requires a MySQL datasource");
        }
        String sourceDatabase = bound(fromDatasource.database(), source.database());
        String sourceSchema = bound(fromDatasource.schema(), source.schema());
        String sinkDatabase = bound(toDatasource.database(), sink.database());
        String sinkSchema = bound(toDatasource.schema(), sink.schema());
        TableSchema from = context.tableSchema(
                source.datasourceId(), sourceDatabase, sourceSchema, source.table());
        TableSchema to = context.tableSchema(
                sink.datasourceId(), sinkDatabase, sinkSchema, sink.table());
        requireCompatible(from, to,
                parameters.syncType() == DataSyncType.REALTIME || sink.writeMode() == DataSyncWriteMode.UPSERT);
        SourceParameters normalizedSource = source instanceof JdbcSourceParameters
                ? new JdbcSourceParameters(source.datasourceId(), sourceDatabase, sourceSchema, source.table())
                : new MySqlCdcSourceParameters(source.datasourceId(), sourceDatabase, sourceSchema, source.table());
        JdbcSinkParameters normalizedSink = new JdbcSinkParameters(
                sink.datasourceId(), sinkDatabase, sinkSchema, sink.table(), sink.writeMode());
        return new SyncParameters(normalizedSource, parameters.transform(), normalizedSink);
    }

    private static String bound(String configured, String requested) {
        return configured != null && !configured.isBlank() ? configured : requested;
    }

    private static void requireCompatible(TableSchema source, TableSchema target, boolean requireKeys) {
        Map<String, Column> sourceColumns = columns(source);
        Map<String, Column> targetColumns = columns(target);
        for (var entry : sourceColumns.entrySet()) {
            Column other = targetColumns.get(entry.getKey());
            if (other == null || !JdbcSchemaCompatibility.isCompatible(entry.getValue(), other)) {
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
                throw new TaskPluginException("UPSERT requires matching source and target primary keys");
            }
        }
    }

    private static Map<String, Column> columns(TableSchema schema) {
        Map<String, Column> result = new LinkedHashMap<>();
        for (Column column : schema.columns()) {
            if (result.putIfAbsent(column.name().toLowerCase(Locale.ROOT), column) != null) {
                throw new TaskPluginException("Ambiguous table column names");
            }
        }
        return result;
    }

    private static Set<String> keys(java.util.List<String> values) {
        Set<String> result = new HashSet<>();
        for (String key : values) result.add(key.toLowerCase(Locale.ROOT));
        return result;
    }
}
