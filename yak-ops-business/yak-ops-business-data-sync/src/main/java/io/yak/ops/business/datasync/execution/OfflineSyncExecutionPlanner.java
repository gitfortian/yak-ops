package io.yak.ops.business.datasync.execution;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.connector.jdbc.source.JdbcSource;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 将实例定义快照解析为 YakFlow 可直接执行的 JDBC Source / Sink 计划。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class OfflineSyncExecutionPlanner {

    @Resource
    private DataSourceService dataSourceService;

    OfflineSyncExecutionPlan plan(DataSyncDefinitionSnapshotVO snapshot) {
        ObjectUtils.requireNonNull(snapshot, "definition snapshot must not be null");
        DataSyncEndpointSnapshotVO sourceEndpoint =
                ObjectUtils.requireNonNull(snapshot.getSource(), "source endpoint must not be null");
        DataSyncEndpointSnapshotVO targetEndpoint =
                ObjectUtils.requireNonNull(snapshot.getTarget(), "target endpoint must not be null");
        DataSyncRuntimeConfigVO runtimeConfig =
                ObjectUtils.requireNonNull(snapshot.getRuntimeConfig(), "runtime config must not be null");

        List<DataSourceCatalogColumnVO> sourceColumns =
                dataSourceService.queryCatalogColumns(sourceEndpoint.getDataSourceId(), tablePath(sourceEndpoint));
        List<DataSourceCatalogColumnVO> targetColumns =
                dataSourceService.queryCatalogColumns(targetEndpoint.getDataSourceId(), tablePath(targetEndpoint));
        YakTableSchema sourceSchema = OfflineSyncSchemaResolver.sourceSchema(sourceColumns);
        YakTableSchema targetWriteSchema =
                OfflineSyncSchemaResolver.targetWriteSchema(sourceColumns, targetColumns);

        DataSourceConnection sourceConnection =
                dataSourceService.resolveRuntimeConnection(sourceEndpoint.getDataSourceId());
        DataSourceConnection targetConnection =
                dataSourceService.resolveRuntimeConnection(targetEndpoint.getDataSourceId());

        JdbcSource source = new JdbcSource(new JdbcSourceConfig(
                sourceConnection,
                tablePathValue(sourceEndpoint),
                sourceSchema,
                runtimeConfig.getFetchSize(),
                runtimeConfig.getReadBatchSize(),
                runtimeConfig.getTimeoutSeconds(),
                runtimeConfig.getSplitSize()));
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        tablePathValue(targetEndpoint),
                        runtimeConfig.getWriteBatchSize(),
                        runtimeConfig.getTimeoutSeconds(),
                        JdbcWriteMode.INSERT),
                targetWriteSchema);
        return new OfflineSyncExecutionPlan(source, sink, sourceSchema, runtimeConfig.getSourceParallelism());
    }

    private DataSourceTablePathDTO tablePath(DataSyncEndpointSnapshotVO endpoint) {
        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(endpoint.getDatabase());
        path.setSchema(endpoint.getSchema());
        path.setTable(endpoint.getTable());
        return path;
    }

    private DataSourceTablePath tablePathValue(DataSyncEndpointSnapshotVO endpoint) {
        return new DataSourceTablePath(endpoint.getDatabase(), endpoint.getSchema(), endpoint.getTable());
    }
}
