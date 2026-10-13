package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.DataSourceInfo;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.plan.SyncSchemaValidator;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;

/**
 * DATA_SYNC definition checks, independent of execution and checkpoint state.
 *
 * <p>Structure is checked once during parsing. Physical checks use Workspace-authorized
 * Datasource metadata and do not silently replace an explicit database or schema.
 */
public final class SyncValidator {

    private SyncValidator() {}

    public static void checkParameters(SyncParameters parameters) {
        if (parameters == null || parameters.source() == null || parameters.sink() == null) {
            throw new TaskPluginException("DATA_SYNC requires one source and one sink");
        }
        if (!(parameters.source() instanceof JdbcSourceParameters)
                && !(parameters.source() instanceof MySqlCdcSourceParameters)) {
            throw new TaskPluginException("Unsupported DATA_SYNC source");
        }
        if (!(parameters.sink() instanceof JdbcSinkParameters sink)) {
            throw new TaskPluginException("Unsupported DATA_SYNC sink");
        }
        if (!parameters.transform().isEmpty()) {
            throw new TaskPluginException("DATA_SYNC transforms are not supported yet");
        }
        checkTableFields(
                parameters.source().datasourceId(),
                parameters.source().database(),
                parameters.source().schema(),
                parameters.source().table());
        checkTableFields(sink.datasourceId(), sink.database(), sink.schema(), sink.table());
        if (sink.writeMode() == null || sink.writeMode() == DataSyncWriteMode.OVERWRITE) {
            throw new TaskPluginException("DATA_SYNC supports APPEND or UPSERT only");
        }
        if (parameters.source() instanceof MySqlCdcSourceParameters && sink.writeMode() != DataSyncWriteMode.UPSERT) {
            throw new TaskPluginException("MySQL CDC requires UPSERT to preserve change events");
        }
    }

    static void checkResources(SyncParameters parameters, TaskValidationContext context) {
        var source = parameters.source();
        var sink = (JdbcSinkParameters) parameters.sink();
        DataSourceInfo sourceInfo = context.dataSource(source.datasourceId());
        DataSourceInfo sinkInfo = context.dataSource(sink.datasourceId());

        if (source instanceof MySqlCdcSourceParameters && !"MYSQL".equals(sourceInfo.type())) {
            throw new TaskPluginException("MySQL CDC requires a MySQL datasource");
        }

        TableId sourceTable = tablePath(sourceInfo, source.database(), source.schema(), source.table());
        TableId sinkTable = tablePath(sinkInfo, sink.database(), sink.schema(), sink.table());
        TableSchema from = context.tableSchema(source.datasourceId(), sourceTable);
        TableSchema to = context.tableSchema(sink.datasourceId(), sinkTable);
        boolean requireKeys =
                source instanceof MySqlCdcSourceParameters || sink.writeMode() == DataSyncWriteMode.UPSERT;
        SyncSchemaValidator.validate(from, to, requireKeys);
    }

    private static void checkTableFields(String datasourceId, String database, String schema, String table) {
        requireText(datasourceId, 64, "datasourceId");
        requireText(table, 128, "table");
        optionalText(database, 128, "database");
        optionalText(schema, 128, "schema");
    }

    private static void requireText(String value, int maxLength, String field) {
        if (StringUtils.isBlank(value)) {
            throw new TaskPluginException("DATA_SYNC requires " + field);
        }
        optionalText(value, maxLength, field);
    }

    private static void optionalText(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new TaskPluginException("DATA_SYNC " + field + " exceeds " + maxLength + " characters");
        }
    }

    private static TableId tablePath(DataSourceInfo info, String database, String schema, String table) {
        String selectedDatabase = chooseNamespace(info.database(), database, "database");
        String selectedSchema = chooseNamespace(info.schema(), schema, "schema");
        return new TableId(selectedDatabase, selectedSchema, table);
    }

    private static String chooseNamespace(String bound, String selected, String field) {
        if (StringUtils.isNotBlank(bound) && StringUtils.isNotBlank(selected) && !bound.equals(selected)) {
            throw new TaskPluginException("DATA_SYNC " + field + " differs from the bound Datasource");
        }
        if (StringUtils.isNotBlank(selected)) {
            return selected;
        }
        return bound;
    }
}
