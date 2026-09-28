package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.business.datasync.execution.planning.RealtimeSyncExecutionPlan;
import io.yak.ops.business.datasync.execution.planning.RealtimeSyncExecutionPlanner;
import io.yak.ops.business.datasync.execution.realtime.MySqlCdcServerIdAllocator;
import io.yak.ops.business.datasync.execution.realtime.RealtimeSyncStateManager;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalExecution;
import io.yak.ops.flow.runtime.LocalExecutionEngine;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将持久化 REALTIME 同步实例交给 YakFlow 连续执行，并收口运行状态、指标和取消语义。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class RealtimeSyncExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RealtimeSyncExecutor.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final long METRICS_FLUSH_INTERVAL_MILLIS = 500L;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private RealtimeSyncExecutionPlanner executionPlanner;

    @Resource
    private RealtimeSyncStateManager stateManager;

    @Resource
    private MySqlCdcServerIdAllocator serverIdAllocator;

    @Resource
    private DataSyncExecutionRegistry executionRegistry;

    public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        Thread.ofVirtual()
                .name("yak-realtime-sync-" + instanceId)
                .start(() -> execute(workspaceId, instanceId, snapshot));
    }

    private void execute(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        LocalExecution<?> execution = null;
        String stateKey = null;
        Long serverId = null;
        WorkspaceContext.bind(workspaceId);
        try {
            stateKey = stateManager.stateKey(workspaceId, snapshot.getTaskId(), snapshot.getTaskVersion());
            serverId = serverIdAllocator.allocate(stateKey);
            RealtimeSyncExecutionPlan plan = executionPlanner.plan(workspaceId, snapshot, serverId);
            execution = new LocalExecutionEngine(plan.checkpointInterval())
                    .start(plan.source(), plan.sink(), plan.sourceSchema());
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
                    "实时同步实例开始执行，workspaceId={}, taskId={}, instanceId={}",
                    workspaceId,
                    snapshot.getTaskId(),
                    instanceId);

            while (execution.status() == ExecutionStatus.RUNNING) {
                persistMetrics(workspaceId, instanceId, execution.metrics());
                Thread.sleep(METRICS_FLUSH_INTERVAL_MILLIS);
            }
            ExecutionStatus status = execution.await();
            persistMetrics(workspaceId, instanceId, execution.metrics());
            if (status == ExecutionStatus.CANCELED) {
                transitionTerminal(workspaceId, instanceId, DataSyncInstanceStatus.CANCELED, null, null);
                LOG.info(
                        "实时同步实例已停止，workspaceId={}, taskId={}, instanceId={}",
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
                        "实时同步实例执行失败，workspaceId={}, taskId={}, instanceId={}, error={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        message);
            } else if (status == ExecutionStatus.SUCCEEDED) {
                String message = "实时同步连续 Source 意外结束";
                transitionTerminal(
                        workspaceId,
                        instanceId,
                        DataSyncInstanceStatus.FAILED,
                        DataSyncErrorCode.EXECUTION_FAILED.getCode(),
                        message);
                LOG.error(
                        "实时同步实例意外结束，workspaceId={}, taskId={}, instanceId={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (execution != null) execution.cancel();
            failPendingOrRunning(workspaceId, instanceId, exception);
        } catch (Exception exception) {
            if (execution != null) execution.cancel();
            failPendingOrRunning(workspaceId, instanceId, exception);
        } finally {
            if (execution != null) executionRegistry.remove(instanceId, execution);
            if (stateKey != null && serverId != null) serverIdAllocator.release(stateKey, serverId);
            WorkspaceContext.clear();
        }
    }

    private void persistMetrics(String workspaceId, String instanceId, ExecutionMetrics metrics) {
        instanceRepository.updateMetrics(workspaceId, instanceId, metrics.readRows(), metrics.writeRows());
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
        LOG.error("实时同步实例执行异常，workspaceId={}, instanceId={}, error={}", workspaceId, instanceId, message);
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
