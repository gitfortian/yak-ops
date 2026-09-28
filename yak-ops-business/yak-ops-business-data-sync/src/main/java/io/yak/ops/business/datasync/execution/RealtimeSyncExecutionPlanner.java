package io.yak.ops.business.datasync.execution;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRealtimeConfigVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.flow.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.CRC32;
import org.springframework.stereotype.Component;

/**
 * 将 REALTIME 实例定义快照解析为 MySQL CDC Source + JDBC Changelog Sink 执行计划。
 *
 * <p>当前阶段仅提供进程级执行引导：CDC state 目录位于系统临时目录，serverId 由 instanceId 临时派生。
 * 持久化 state ownership、稳定 serverId 分配与重启恢复由后续生命周期阶段收口。</p>
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class RealtimeSyncExecutionPlanner {

    @Resource
    private DataSourceService dataSourceService;

    RealtimeSyncExecutionPlan plan(String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        ObjectUtils.requireNonNull(instanceId, "instance id must not be null");
        ObjectUtils.requireNonNull(snapshot, "definition snapshot must not be null");
        if (!DataSyncType.REALTIME.name().equals(snapshot.getSyncType())) {
            throw new IllegalArgumentException("realtime planner requires REALTIME snapshot");
        }

        DataSyncEndpointSnapshotVO sourceEndpoint =
                ObjectUtils.requireNonNull(snapshot.getSource(), "source endpoint must not be null");
        DataSyncEndpointSnapshotVO targetEndpoint =
                ObjectUtils.requireNonNull(snapshot.getTarget(), "target endpoint must not be null");
        DataSyncRealtimeConfigVO realtimeConfig =
                ObjectUtils.requireNonNull(snapshot.getRealtimeConfig(), "realtime config must not be null");

        List<DataSourceCatalogColumnVO> sourceColumns =
                dataSourceService.queryCatalogColumns(sourceEndpoint.getDataSourceId(), tablePath(sourceEndpoint));
        List<DataSourceCatalogColumnVO> targetColumns =
                dataSourceService.queryCatalogColumns(targetEndpoint.getDataSourceId(), tablePath(targetEndpoint));
        YakTableSchema sourceSchema = OfflineSyncSchemaResolver.sourceSchema(sourceColumns);
        YakTableSchema targetWriteSchema = OfflineSyncSchemaResolver.targetWriteSchema(sourceColumns, targetColumns);

        JdbcConnectionProperties sourceConnection =
                requireMySqlConnection(dataSourceService.resolveRuntimeConnection(sourceEndpoint.getDataSourceId()));
        DataSourceConnection targetConnection =
                dataSourceService.resolveRuntimeConnection(targetEndpoint.getDataSourceId());

        MySqlCdcSource source = new MySqlCdcSource(new MySqlCdcSourceConfig(
                sourceConnection,
                tablePathValue(sourceEndpoint),
                sourceSchema,
                temporaryStateDirectory(instanceId),
                "yak-realtime-" + instanceId,
                temporaryServerId(instanceId),
                realtimeConfig.getQueueCapacity(),
                realtimeConfig.getPollBatchSize(),
                realtimeConfig.getTimeoutSeconds()));
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        tablePathValue(targetEndpoint),
                        realtimeConfig.getWriteBatchSize(),
                        realtimeConfig.getTimeoutSeconds(),
                        JdbcWriteMode.CHANGELOG),
                targetWriteSchema);
        return new RealtimeSyncExecutionPlan(
                source,
                sink,
                sourceSchema,
                Duration.ofSeconds(realtimeConfig.getCheckpointIntervalSeconds()));
    }

    private JdbcConnectionProperties requireMySqlConnection(DataSourceConnection connection) {
        if (!(connection instanceof JdbcConnectionProperties jdbcConnection) || !"MYSQL".equals(jdbcConnection.type())) {
            throw new IllegalArgumentException("realtime sync source runtime connection must be MYSQL JDBC");
        }
        return jdbcConnection;
    }

    private Path temporaryStateDirectory(String instanceId) {
        return Path.of(System.getProperty("java.io.tmpdir", "."), "yak-ops", "realtime-sync", instanceId);
    }

    private long temporaryServerId(String instanceId) {
        CRC32 checksum = new CRC32();
        checksum.update(instanceId.getBytes(StandardCharsets.UTF_8));
        long value = checksum.getValue();
        return value == 0 ? 1 : value;
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
