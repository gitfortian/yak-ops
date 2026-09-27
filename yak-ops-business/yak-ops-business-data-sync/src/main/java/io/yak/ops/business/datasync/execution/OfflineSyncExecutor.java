package io.yak.ops.business.datasync.execution;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRuntimeConfigVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcSinkConfig;
import io.yak.ops.flow.connector.jdbc.JdbcSourceConfig;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.connector.jdbc.source.JdbcSource;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalExecution;
import io.yak.ops.flow.runtime.LocalRuntime;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将持久化离线同步实例交给 YakFlow Local Runtime，并收口 RUNNING / SUCCESS / FAILURE / CANCEL 状态。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Component
public class OfflineSyncExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(OfflineSyncExecutor.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private OfflineSyncExecutionRegistry executionRegistry;

    /**
     * 单机 Local Runtime 无法跨进程恢复；应用启动时把上一进程遗留的活动实例统一标记为 LOST。
     */
    @PostConstruct
    public void recoverLostExecutions() {
        int affected = instanceRepository.markActiveAsLost(
                DateUtils.now(),
                DataSyncErrorCode.EXECUTION_LOST.getCode(),
                DataSyncErrorCode.EXECUTION_LOST.getMessage());
        if (affected > 0) {
            LOG.warn("应用启动发现遗留离线同步实例，已标记为 LOST，count={}", affected);
        }
    }

    public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        Thread.ofVirtual()
                .name("yak-offline-sync-" + instanceId)
                .start(() -> execute(workspaceId, instanceId, snapshot));
    }

    private void execute(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        LocalExecution<?> execution = null;
        WorkspaceContext.bind(workspaceId);
        try {
            DataSyncEndpointSnapshotVO sourceEndpoint = snapshot.getSource();
            DataSyncEndpointSnapshotVO targetEndpoint = snapshot.getTarget();
            DataSyncRuntimeConfigVO runtimeConfig = snapshot.getRuntimeConfig();

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
                    runtimeConfig.getTimeoutSeconds()));
            JdbcSink sink = new JdbcSink(
                    new JdbcSinkConfig(
                            targetConnection,
                            tablePathValue(targetEndpoint),
                            runtimeConfig.getWriteBatchSize(),
                            runtimeConfig.getTimeoutSeconds(),
                            JdbcWriteMode.INSERT),
                    targetWriteSchema);

            execution = new LocalRuntime().start(source, sink, sourceSchema);
            executionRegistry.register(instanceId, execution);

            LocalDateTime startTime = DateUtils.now();
            if (!instanceRepository.transitionStatus(
                    workspaceId,
                    instanceId,
                    DataSyncInstanceStatus.PENDING,
                    DataSyncInstanceStatus.RUNNING,
                    startTime,
                    null,
                    null,
                    null)) {
                execution.cancel();
                execution.await();
                return;
            }

            LOG.info(
                    "离线同步实例开始执行，workspaceId={}, taskId={}, instanceId={}",
                    workspaceId,
                    snapshot.getTaskId(),
                    instanceId);

            ExecutionStatus status = execution.await();
            if (status == ExecutionStatus.SUCCEEDED) {
                transitionTerminal(workspaceId, instanceId, DataSyncInstanceStatus.SUCCEEDED, null, null);
                LOG.info(
                        "离线同步实例执行成功，workspaceId={}, taskId={}, instanceId={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId);
            } else if (status == ExecutionStatus.CANCELED) {
                transitionTerminal(workspaceId, instanceId, DataSyncInstanceStatus.CANCELED, null, null);
                LOG.info(
                        "离线同步实例已取消，workspaceId={}, taskId={}, instanceId={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId);
            } else if (status == ExecutionStatus.FAILED) {
                String message = safeMessage(execution.failure().orElse(null));
                transitionTerminal(
                        workspaceId,
                        instanceId,
                        DataSyncInstanceStatus.FAILED,
                        DataSyncErrorCode.EXECUTION_FAILED.getCode(),
                        message);
                LOG.error(
                        "离线同步实例执行失败，workspaceId={}, taskId={}, instanceId={}, error={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (execution != null) execution.cancel();
            failPendingOrRunning(workspaceId, instanceId, exception);
        } catch (Exception exception) {
            if (execution != null) execution.cancel();
            failPendingOrRunning(workspaceId, instanceId, exception);
        } finally {
            if (execution != null) {
                executionRegistry.remove(instanceId, execution);
            }
            WorkspaceContext.clear();
        }
    }

    private void failPendingOrRunning(String workspaceId, String instanceId, Throwable throwable) {
        String message = safeMessage(throwable);
        Integer errorCode = DataSyncErrorCode.EXECUTION_FAILED.getCode();
        LocalDateTime finishTime = DateUtils.now();
        boolean updated = instanceRepository.transitionStatus(
                workspaceId,
                instanceId,
                DataSyncInstanceStatus.RUNNING,
                DataSyncInstanceStatus.FAILED,
                null,
                finishTime,
                errorCode,
                message);
        if (!updated) {
            instanceRepository.transitionStatus(
                    workspaceId,
                    instanceId,
                    DataSyncInstanceStatus.PENDING,
                    DataSyncInstanceStatus.FAILED,
                    null,
                    finishTime,
                    errorCode,
                    message);
        }
        LOG.error("离线同步实例执行异常，workspaceId={}, instanceId={}, error={}", workspaceId, instanceId, message);
    }

    private void transitionTerminal(
            String workspaceId,
            String instanceId,
            DataSyncInstanceStatus status,
            Integer errorCode,
            String errorMessage) {
        instanceRepository.transitionStatus(
                workspaceId,
                instanceId,
                DataSyncInstanceStatus.RUNNING,
                status,
                null,
                DateUtils.now(),
                errorCode,
                errorMessage);
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

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? DataSyncErrorCode.EXECUTION_FAILED.getMessage() : throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable == null
                    ? DataSyncErrorCode.EXECUTION_FAILED.getMessage()
                    : throwable.getClass().getSimpleName();
        }
        String masked = SensitiveUtils.mask(message);
        return masked.length() > MAX_ERROR_MESSAGE_LENGTH ? masked.substring(0, MAX_ERROR_MESSAGE_LENGTH) : masked;
    }
}
