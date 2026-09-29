package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.business.datasync.execution.planning.OfflineSyncExecutionPlan;
import io.yak.ops.business.datasync.execution.planning.OfflineSyncExecutionPlanner;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.flow.runtime.ExecutionMetrics;
import io.yak.ops.flow.runtime.ExecutionStatus;
import io.yak.ops.flow.runtime.LocalExecution;
import io.yak.ops.flow.runtime.LocalExecutionEngine;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将 Offline Execution 按 Retry Policy 拆成连续 Attempt，并复用同一个 Execution Root 收口最终状态。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Component
public class OfflineSyncExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(OfflineSyncExecutor.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final long METRICS_FLUSH_INTERVAL_MILLIS = 500L;

    @Resource
    private OfflineSyncExecutionPlanner executionPlanner;

    @Resource
    private DataSyncExecutionRegistry executionRegistry;

    @Resource
    private DataSyncAttemptLifecycle attemptLifecycle;

    public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        Thread.ofVirtual()
                .name("yak-offline-sync-" + instanceId)
                .start(() -> execute(workspaceId, instanceId, snapshot));
    }

    private void execute(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        WorkspaceContext.bind(workspaceId);
        try {
            int maxAttempts = maxAttempts(snapshot);
            int backoffSeconds = backoffSeconds(snapshot);
            DataSyncInstanceStatus expectedExecutionStatus = DataSyncInstanceStatus.PENDING;

            for (int attemptNo = 1; attemptNo <= maxAttempts; attemptNo++) {
                DataSyncRetryDecision decision = executeAttempt(
                        workspaceId,
                        instanceId,
                        snapshot,
                        attemptNo,
                        maxAttempts,
                        backoffSeconds,
                        expectedExecutionStatus);
                if (!decision.retry()) return;
                if (!waitForRetry(workspaceId, instanceId, decision.nextRetryTime())) return;
                expectedExecutionStatus = DataSyncInstanceStatus.RETRY_WAITING;
            }
        } finally {
            WorkspaceContext.clear();
        }
    }

    private DataSyncRetryDecision executeAttempt(
            String workspaceId,
            String instanceId,
            DataSyncDefinitionSnapshotVO snapshot,
            int attemptNo,
            int maxAttempts,
            int backoffSeconds,
            DataSyncInstanceStatus expectedExecutionStatus) {
        DataSyncAttemptEntity attempt = attemptLifecycle.createAttempt(workspaceId, instanceId, attemptNo);
        LocalExecution<?> execution = null;
        boolean started = false;
        try {
            OfflineSyncExecutionPlan plan = executionPlanner.plan(snapshot);
            execution = new LocalExecutionEngine()
                    .start(plan.source(), plan.sink(), plan.sourceSchema(), plan.sourceParallelism());
            executionRegistry.register(instanceId, execution);

            if (!attemptLifecycle.startAttempt(
                    workspaceId, instanceId, attempt.getId(), attemptNo, expectedExecutionStatus)) {
                execution.cancel();
                execution.await();
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                return DataSyncRetryDecision.stop();
            }
            started = true;

            LOG.info(
                    "离线同步Attempt开始执行，workspaceId={}, taskId={}, instanceId={}, attempt={}/{}",
                    workspaceId,
                    snapshot.getTaskId(),
                    instanceId,
                    attemptNo,
                    maxAttempts);

            while (execution.status() == ExecutionStatus.RUNNING) {
                persistMetrics(workspaceId, instanceId, attempt.getId(), execution.metrics());
                Thread.sleep(METRICS_FLUSH_INTERVAL_MILLIS);
            }

            ExecutionStatus status = execution.await();
            ExecutionMetrics metrics = execution.metrics();
            persistMetrics(workspaceId, instanceId, attempt.getId(), metrics);

            if (status == ExecutionStatus.SUCCEEDED) {
                attemptLifecycle.succeedAttempt(
                        workspaceId, instanceId, attempt.getId(), attemptNo, metrics.readRows(), metrics.writeRows());
                LOG.info(
                        "离线同步Execution执行成功，workspaceId={}, taskId={}, instanceId={}, attempt={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        attemptNo);
                return DataSyncRetryDecision.stop();
            }

            if (status == ExecutionStatus.CANCELED) {
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                LOG.info(
                        "离线同步Execution已取消，workspaceId={}, taskId={}, instanceId={}, attempt={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        attemptNo);
                return DataSyncRetryDecision.stop();
            }

            String message = safeMessage(execution.failure().orElse(null));
            return failAttempt(
                    workspaceId,
                    instanceId,
                    attempt,
                    attemptNo,
                    maxAttempts,
                    backoffSeconds,
                    DataSyncAttemptStatus.RUNNING,
                    DataSyncInstanceStatus.RUNNING,
                    metrics,
                    message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (execution != null) execution.cancel();
            return failAttempt(
                    workspaceId,
                    instanceId,
                    attempt,
                    attemptNo,
                    maxAttempts,
                    backoffSeconds,
                    started ? DataSyncAttemptStatus.RUNNING : DataSyncAttemptStatus.PENDING,
                    started ? DataSyncInstanceStatus.RUNNING : expectedExecutionStatus,
                    execution == null ? null : execution.metrics(),
                    safeMessage(exception));
        } catch (Exception exception) {
            if (execution != null) execution.cancel();
            return failAttempt(
                    workspaceId,
                    instanceId,
                    attempt,
                    attemptNo,
                    maxAttempts,
                    backoffSeconds,
                    started ? DataSyncAttemptStatus.RUNNING : DataSyncAttemptStatus.PENDING,
                    started ? DataSyncInstanceStatus.RUNNING : expectedExecutionStatus,
                    execution == null ? null : execution.metrics(),
                    safeMessage(exception));
        } finally {
            if (execution != null) executionRegistry.remove(instanceId, execution);
        }
    }

    private DataSyncRetryDecision failAttempt(
            String workspaceId,
            String instanceId,
            DataSyncAttemptEntity attempt,
            int attemptNo,
            int maxAttempts,
            int backoffSeconds,
            DataSyncAttemptStatus expectedAttemptStatus,
            DataSyncInstanceStatus expectedExecutionStatus,
            ExecutionMetrics metrics,
            String message) {
        long readRows = metrics == null ? 0L : metrics.readRows();
        long writeRows = metrics == null ? 0L : metrics.writeRows();
        DataSyncRetryDecision decision = attemptLifecycle.failAttempt(
                workspaceId,
                instanceId,
                attempt.getId(),
                attemptNo,
                expectedAttemptStatus,
                expectedExecutionStatus,
                maxAttempts,
                backoffSeconds,
                readRows,
                writeRows,
                DataSyncErrorCode.EXECUTION_FAILED.getCode(),
                message);
        if (decision.retry()) {
            LOG.warn(
                    "离线同步Attempt失败等待重试，workspaceId={}, instanceId={}, attempt={}/{}, nextRetryTime={}, error={}",
                    workspaceId,
                    instanceId,
                    attemptNo,
                    maxAttempts,
                    decision.nextRetryTime(),
                    message);
        } else {
            LOG.error(
                    "离线同步Execution执行失败，workspaceId={}, instanceId={}, attempt={}/{}, error={}",
                    workspaceId,
                    instanceId,
                    attemptNo,
                    maxAttempts,
                    message);
        }
        return decision;
    }

    private void persistMetrics(String workspaceId, String instanceId, String attemptId, ExecutionMetrics metrics) {
        attemptLifecycle.updateMetrics(workspaceId, instanceId, attemptId, metrics.readRows(), metrics.writeRows());
    }

    private boolean waitForRetry(String workspaceId, String instanceId, LocalDateTime nextRetryTime) {
        if (nextRetryTime == null) return false;
        long delayMillis = Math.max(
                0L, Duration.between(LocalDateTime.now(), nextRetryTime).toMillis());
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
        return attemptLifecycle.isRetryWaiting(workspaceId, instanceId);
    }

    private int maxAttempts(DataSyncDefinitionSnapshotVO snapshot) {
        DataSyncRetryPolicyVO policy = snapshot.getRetryPolicy();
        return policy == null || policy.getMaxAttempts() == null ? 1 : Math.max(1, policy.getMaxAttempts());
    }

    private int backoffSeconds(DataSyncDefinitionSnapshotVO snapshot) {
        DataSyncRetryPolicyVO policy = snapshot.getRetryPolicy();
        return policy == null || policy.getBackoffSeconds() == null ? 60 : Math.max(0, policy.getBackoffSeconds());
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
