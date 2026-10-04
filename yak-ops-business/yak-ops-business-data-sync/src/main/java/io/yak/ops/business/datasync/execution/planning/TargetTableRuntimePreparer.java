package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.business.datasync.schema.LogicalTableNormalizer;
import io.yak.ops.business.datasync.schema.TargetSchemaCompatibility;
import io.yak.ops.business.datasync.schema.TargetSchemaCompatibilityResult;
import io.yak.ops.business.datasync.schema.TargetTablePlan;
import io.yak.ops.business.datasync.schema.TargetTablePlanner;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.flow.api.row.YakTableSchema;
import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Execution 启动前的目标表 Runtime Preflight。
 *
 * <p>每个 Attempt 都重新读取 Catalog：目标存在时做 Schema Compatibility；目标不存在时仅在 definitionSnapshot
 * 显式允许 autoCreateTable 且 TargetTablePlan supported 时执行受控 CREATE TABLE。建表后必须重新 introspect，
 * 禁止直接相信生成计划。</p>
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Component
public class TargetTableRuntimePreparer {

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private TargetTablePlanner targetTablePlanner;

    @Resource
    private TargetTableDdlExecutor ddlExecutor;

    public TargetTablePreparation prepare(DataSyncDefinitionSnapshotVO snapshot, int timeoutSeconds) {
        ObjectUtils.requireNonNull(snapshot, "definition snapshot must not be null");
        DataSyncEndpointSnapshotVO sourceEndpoint =
                ObjectUtils.requireNonNull(snapshot.getSource(), "source endpoint must not be null");
        DataSyncEndpointSnapshotVO targetEndpoint =
                ObjectUtils.requireNonNull(snapshot.getTarget(), "target endpoint must not be null");

        LogicalTable sourceLogicalTable = sourceLogicalTable(sourceEndpoint);
        DataSourceTablePathDTO targetPath = tablePath(targetEndpoint);

        Optional<DataSourceCatalogTableVO> targetTable =
                dataSourceService.findCatalogTable(targetEndpoint.getDataSourceId(), targetPath);
        boolean created = false;
        if (targetTable.isEmpty()) {
            if (!Boolean.TRUE.equals(snapshot.getAutoCreateTable())) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_TABLE_NOT_FOUND, targetEndpoint.getTable() + "；请先创建目标表或开启自动建表");
            }
            created = createTargetTable(sourceLogicalTable, targetEndpoint, targetPath, timeoutSeconds);
            targetTable = dataSourceService.findCatalogTable(targetEndpoint.getDataSourceId(), targetPath);
            if (targetTable.isEmpty()) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_TABLE_CREATE_FAILED,
                        "DDL 执行后 Catalog 仍未发现目标表：" + targetEndpoint.getTable());
            }
        }

        List<DataSourceCatalogColumnVO> targetColumns =
                dataSourceService.queryCatalogColumns(targetEndpoint.getDataSourceId(), targetPath);
        TargetSchemaCompatibilityResult compatibility =
                TargetSchemaCompatibility.check(sourceLogicalTable, targetColumns);
        if (!compatibility.compatible()) {
            throw new DataSyncException(
                    DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, String.join("；", compatibility.issues()));
        }

        validatePrimaryKeyContract(snapshot, sourceLogicalTable, compatibility.targetWriteSchema());
        return new TargetTablePreparation(
                sourceLogicalTable.toRuntimeSchema(), compatibility.targetWriteSchema(), created);
    }

    private boolean createTargetTable(
            LogicalTable sourceLogicalTable,
            DataSyncEndpointSnapshotVO targetEndpoint,
            DataSourceTablePathDTO targetPath,
            int timeoutSeconds) {
        String targetType = targetEndpoint.getDataSourceType();
        if (targetType == null || targetType.isBlank()) {
            DataSourceVO targetDataSource = dataSourceService.queryDataSource(targetEndpoint.getDataSourceId());
            targetType = targetDataSource.getDbType();
        }

        TargetTablePlan plan = targetTablePlanner.plan(
                sourceLogicalTable,
                targetType,
                targetPath.getDatabase(),
                targetPath.getSchema(),
                targetPath.getTable());
        if (!plan.supported()) {
            throw new DataSyncException(
                    DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, String.join("；", plan.unsupportedReasons()));
        }

        try {
            ddlExecutor.createTable(
                    dataSourceService.resolveRuntimeConnection(targetEndpoint.getDataSourceId()),
                    plan.targetPath(),
                    sourceLogicalTable.toRuntimeSchema(),
                    timeoutSeconds);
            return true;
        } catch (Exception exception) {
            Optional<DataSourceCatalogTableVO> concurrentTable =
                    dataSourceService.findCatalogTable(targetEndpoint.getDataSourceId(), targetPath);
            if (concurrentTable.isPresent()) {
                return false;
            }
            throw new DataSyncException(
                    DataSyncErrorCode.TARGET_TABLE_CREATE_FAILED, exception.getMessage(), exception);
        }
    }

    private LogicalTable sourceLogicalTable(DataSyncEndpointSnapshotVO endpoint) {
        DataSourceTablePathDTO path = tablePath(endpoint);
        DataSourceCatalogTableVO table = dataSourceService.queryCatalogTable(endpoint.getDataSourceId(), path);
        List<DataSourceCatalogColumnVO> columns =
                dataSourceService.queryCatalogColumns(endpoint.getDataSourceId(), path);
        return LogicalTableNormalizer.fromCatalog(table, columns);
    }

    private void validatePrimaryKeyContract(
            DataSyncDefinitionSnapshotVO snapshot, LogicalTable sourceLogicalTable, YakTableSchema targetWriteSchema) {
        Set<String> sourcePrimaryKeys = normalizedKeys(sourceLogicalTable.primaryKeys());
        Set<String> targetPrimaryKeys = normalizedKeys(targetWriteSchema.primaryKeys());

        if (DataSyncType.REALTIME.name().equals(snapshot.getSyncType())) {
            if (sourcePrimaryKeys.isEmpty()) {
                throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "实时同步来源表必须包含主键");
            }
            if (!sourcePrimaryKeys.equals(targetPrimaryKeys)) {
                throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "实时同步目标表主键必须与来源表主键一致");
            }
            return;
        }

        if (DataSyncWriteMode.UPSERT.name().equals(snapshot.getWriteMode())) {
            if (targetPrimaryKeys.isEmpty()) {
                throw new DataSyncException(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, "UPSERT 写入要求目标表存在主键");
            }
            if (!sourceColumnNames(sourceLogicalTable).containsAll(targetPrimaryKeys)) {
                throw new DataSyncException(
                        DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE,
                        "UPSERT 写入要求来源包含目标表全部主键字段");
            }
        }
    }

    private Set<String> sourceColumnNames(LogicalTable logicalTable) {
        Set<String> result = new HashSet<>();
        logicalTable.columns().forEach(column -> result.add(column.name().toLowerCase(Locale.ROOT)));
        return result;
    }

    private Set<String> normalizedKeys(List<String> keys) {
        Set<String> result = new HashSet<>();
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                result.add(key.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private DataSourceTablePathDTO tablePath(DataSyncEndpointSnapshotVO endpoint) {
        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(endpoint.getDatabase());
        path.setSchema(endpoint.getSchema());
        path.setTable(endpoint.getTable());
        return path;
    }
}
