package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.util.StringUtils;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.DataSourceInfo;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.connector.ConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;

/**
 * Validates DATA_SYNC parameters and authorized Datasource metadata.
 *
 * <p>Source and Sink factories own their own execution capabilities. The current saved table path
 * remains explicit, and planning never silently changes a selected namespace.
 */
public final class DefinitionValidator {

    private DefinitionValidator() {}

    /**
     * Checks the saved configuration without reading Datasource metadata.
     *
     * @param parameters decoded DATA_SYNC parameters
     * @param factories discovered Connector capabilities
     */
    public static void validateParameters(DataSyncParameters parameters, ConnectorFactoryRegistry factories) {
        if (parameters == null || parameters.source() == null || parameters.sink() == null) {
            throw new TaskPluginException("DATA_SYNC requires one source and one sink");
        }
        if (!parameters.transform().isEmpty()) {
            throw new TaskPluginException("DATA_SYNC transforms are not supported yet");
        }
        SourceParameters source = parameters.source();
        var sourceFactory = factories.source(source);
        var sinkFactory = factories.sink(parameters.sink());
        checkTableFields(source.datasourceId(), source.database(), source.schema(), source.table());
        checkTableFields(
                parameters.sink().datasourceId(),
                parameters.sink().database(),
                parameters.sink().schema(),
                parameters.sink().table());
        sinkFactory.validate(parameters.sink(), sourceFactory.producesChangelog());
    }

    /**
     * Checks the authorized table and field mapping for the resolved Connectors.
     *
     * @param parameters decoded DATA_SYNC parameters
     * @param context Workspace-bound datasource access
     * @param factories discovered Connector capabilities
     */
    public static void validateResources(
            DataSyncParameters parameters, TaskValidationContext context, ConnectorFactoryRegistry factories) {
        SourceParameters source = parameters.source();
        var sourceFactory = factories.source(source);
        var sinkFactory = factories.sink(parameters.sink());
        DataSourceInfo sourceInfo = context.dataSource(source.datasourceId());
        DataSourceInfo sinkInfo = context.dataSource(parameters.sink().datasourceId());
        sourceFactory.validateDatasource(sourceInfo.type());

        TableId sourceTable = tablePath(sourceInfo, source.database(), source.schema(), source.table());
        TableId sinkTable = tablePath(
                sinkInfo,
                parameters.sink().database(),
                parameters.sink().schema(),
                parameters.sink().table());
        TableSchema from = context.tableSchema(source.datasourceId(), sourceTable);
        TableSchema to = context.tableSchema(parameters.sink().datasourceId(), sinkTable);
        sinkFactory.validateSchema(parameters.sink(), from, to, sourceFactory.producesChangelog());
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
