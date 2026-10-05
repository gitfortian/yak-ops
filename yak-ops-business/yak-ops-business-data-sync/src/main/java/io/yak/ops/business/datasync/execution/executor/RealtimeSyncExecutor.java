package io.yak.ops.business.datasync.execution.executor;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncExecutionRegistry;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.business.datasync.execution.planning.RealtimeSyncExecutionPlan;
import io.yak.ops.business.datasync.execution.planning.RealtimeSyncExecutionPlanner;
import io.yak.ops.business.datasync.execution.realtime.MySqlCdcServerIdAllocator;
import io.yak.ops.business.datasync.execution.realtime.RealtimeSyncStateNamespace;
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
 * 将 REALTIME Execution 按 Retry Policy 拆成连续 Attempt，并持续复用同一 Task/version 的 CDC state。
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
    private RealtimeSyncExecutionPlanner executionPlanner;

    @Resource
    private RealtimeSyncStateNamespace stateNamespace;

    @Resource
    private MySqlCdcServerIdAllocator serverIdAllocator;

    @Resource
    private DataSyncExecutionRegistry executionRegistry;

    @Resource
    private DataSyncAttemptLifecycle attemptLifecycle;

    public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
        start(
                workspaceId,
                instanceId,
                snapshot,
                1,
                maxAttempts(snapshot),
                backoffSeconds(snapshot),
                DataSyncInstanceStatus.PENDING,
                null);
    }

    public void resumeRetry(
            String workspaceId,
            String instanceId,
            DataSyncDefinitionSnapshotVO snapshot,
            int nextAttemptNo,
            int maxAttempts,
            int backoffSeconds,
            LocalDateTime nextRetryTime) {
        if (nextAttemptNo < 2 || maxAttempts < nextAttemptNo || nextRetryTime == null) {
            throw new IllegalArgumentException("invalid durable retry recovery state");
        }
        start(
                workspaceId,
                instanceId,
                snapshot,
                nextAttemptNo,
                maxAttempts,
                Math.max(0, backoffSeconds),
                DataSyncInstanceStatus.RETRY_WAITING,
                nextRetryTime);
    }

    private void start(
            String workspaceId,
            String instanceId,
            DataSyncDefinitionSnapshotVO snapshot,
            int firstAttemptNo,
            int maxAttempts,
            int backoffSeconds,
            DataSyncInstanceStatus expectedExecutionStatus,
            LocalDateTime initialRetryTime) {
        Thread.ofVirtual()
                .name("yak-realtime-sync-" + instanceId)
                .start(() -> execute(
                        workspaceId,
                        instanceId,
                        snapshot,
                        firstAttemptNo,
                        maxAttempts,
                        backoffSeconds,
                        expectedExecutionStatus,
                        initialRetryTime));
    }

    private void execute(
            String workspaceId,
            String instanceId,
            DataSyncDefinitionSnapshotVO snapshot,
            int firstAttemptNo,
            int maxAttempts,
            int backoffSeconds,
            DataSyncInstanceStatus expectedExecutionStatus,
            LocalDateTime initialRetryTime) {
        WorkspaceContext.bind(workspaceId);
        try {
            if (initialRetryTime != null && !waitForRetry(workspaceId, instanceId, initialRetryTime)) return;

            for (int attemptNo = firstAttemptNo; attemptNo <= maxAttempts; attemptNo++) {
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
        String stateKey = null;
        Long serverId = null;
        boolean started = false;

        try {
            stateKey = stateNamespace.stateKey(workspaceId, snapshot.getTaskId(), snapshot.getTaskVersion());
            serverId = serverIdAllocator.allocate(stateKey);
            RealtimeSyncExecutionPlan plan = executionPlanner.plan(workspaceId, snapshot, serverId);
            execution = new LocalExecutionEngine(plan.checkpointInterval())
                    .start(plan.source(), plan.sink(), plan.sourceSchema());
            executionRegistry.register(instanceId, execution);

            if (!attemptLifecycle.startAttempt(
                    workspaceId, instanceId, attempt.getId(), attemptNo, expectedExecutionStatus)) {
                execution.cancel();
                execution.await();
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                return DataSyncRetryDecision.stop();
            }
            started = true;
            attemptLifecycle.recordSourceReady(workspaceId, instanceId, attempt.getId());
            attemptLifecycle.recordTargetReady(workspaceId, instanceId, attempt.getId());

            LOG.info(
                    "实时同步Attempt开始执行，workspaceId={}, taskId={}, instanceId={}, attempt={}/{}",
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

            if (status == ExecutionStatus.CANCELED) {
                attemptLifecycle.cancelActiveAttempt(workspaceId, instanceId);
                LOG.info(
                        "实时同步Execution已停止，workspaceId={}, taskId={}, instanceId={}, attempt={}",
                        workspaceId,
                        snapshot.getTaskId(),
                        instanceId,
                        attemptNo);
                return DataSyncRetryDecision.stop();
            }

            if (status == ExecutionStatus.FAILED) {
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
            }

            String message = "实时同步连续 Source 意外结束";
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
            if (stateKey != null && serverId != null) serverIdAllocator.release(stateKey, serverId);
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
                    "实时同步Attempt失败等待重试，workspaceId={}, instanceId={}, attempt={}/{}, nextRetryTime={}, error={}",
                    workspaceId,
                    instanceId,
                    attemptNo,
                    maxAttempts,
                    decision.nextRetryTime(),
                    message);
        } else {
            LOG.error(
                    "实时同步Execution执行失败，workspaceId={}, instanceId={}, attempt={}/{}, error={}",
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
