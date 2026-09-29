package io.yak.ops.business.datasync.execution.lifecycle;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一维护 Execution Root 与 child Attempt 的一致状态迁移、指标镜像和 Retry Waiting 语义。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Component
public class DataSyncAttemptLifecycle {

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Transactional(rollbackFor = Exception.class)
    public DataSyncAttemptEntity createAttempt(String workspaceId, String executionId, int attemptNo) {
        DataSyncAttemptEntity attempt = new DataSyncAttemptEntity();
        attempt.setWorkspaceId(workspaceId);
        attempt.setExecutionId(executionId);
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus(DataSyncAttemptStatus.PENDING);
        attempt.setReadRows(0L);
        attempt.setWriteRows(0L);
        attempt.initCreate();
        if (attemptRepository.add(attempt) == null) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "创建 Attempt 失败");
        }
        return attempt;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean startAttempt(
            String workspaceId,
            String executionId,
            String attemptId,
            int attemptNo,
            DataSyncInstanceStatus expectedExecutionStatus) {
        LocalDateTime startTime = DateUtils.now();
        if (!instanceRepository.startAttempt(workspaceId, executionId, expectedExecutionStatus, attemptNo, startTime)) {
            attemptRepository.cancelActiveByExecution(workspaceId, executionId, DateUtils.now());
            return false;
        }
        if (!attemptRepository.transitionStatus(
                workspaceId,
                attemptId,
                DataSyncAttemptStatus.PENDING,
                DataSyncAttemptStatus.RUNNING,
                startTime,
                null,
                null,
                null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "启动 Attempt 失败");
        }
        return true;
    }

    public void updateMetrics(String workspaceId, String executionId, String attemptId, long readRows, long writeRows) {
        attemptRepository.updateMetrics(workspaceId, attemptId, readRows, writeRows);
        instanceRepository.updateMetrics(workspaceId, executionId, readRows, writeRows);
    }

    @Transactional(rollbackFor = Exception.class)
    public void succeedAttempt(
            String workspaceId, String executionId, String attemptId, int attemptNo, long readRows, long writeRows) {
        LocalDateTime finishTime = DateUtils.now();
        if (!attemptRepository.transitionStatus(
                workspaceId,
                attemptId,
                DataSyncAttemptStatus.RUNNING,
                DataSyncAttemptStatus.SUCCEEDED,
                null,
                finishTime,
                null,
                null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "完成 Attempt 成功状态失败");
        }
        if (!instanceRepository.completeExecution(
                workspaceId,
                executionId,
                DataSyncInstanceStatus.RUNNING,
                DataSyncInstanceStatus.SUCCEEDED,
                attemptNo,
                finishTime,
                readRows,
                writeRows,
                null,
                null)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "完成 Execution 成功状态失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSyncRetryDecision failAttempt(
            String workspaceId,
            String executionId,
            String attemptId,
            int attemptNo,
            DataSyncAttemptStatus expectedAttemptStatus,
            DataSyncInstanceStatus expectedExecutionStatus,
            int maxAttempts,
            int backoffSeconds,
            long readRows,
            long writeRows,
            Integer errorCode,
            String errorMessage) {
        DataSyncInstanceEntity execution =
                instanceRepository.queryById(workspaceId, executionId).orElse(null);
        if (execution == null) return DataSyncRetryDecision.stop();
        if (execution.getStatus() == DataSyncInstanceStatus.CANCELED) {
            attemptRepository.cancelActiveByExecution(workspaceId, executionId, DateUtils.now());
            return DataSyncRetryDecision.stop();
        }

        LocalDateTime finishTime = DateUtils.now();
        if (!attemptRepository.transitionStatus(
                workspaceId,
                attemptId,
                expectedAttemptStatus,
                DataSyncAttemptStatus.FAILED,
                null,
                finishTime,
                errorCode,
                errorMessage)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "记录 Attempt 失败状态失败");
        }

        if (attemptNo < Math.max(1, maxAttempts)) {
            LocalDateTime nextRetryTime = finishTime.plusSeconds(Math.max(0, backoffSeconds));
            if (!instanceRepository.waitForRetry(
                    workspaceId,
                    executionId,
                    expectedExecutionStatus,
                    attemptNo,
                    nextRetryTime,
                    readRows,
                    writeRows,
                    errorCode,
                    errorMessage)) {
                return DataSyncRetryDecision.stop();
            }
            return DataSyncRetryDecision.retryAt(nextRetryTime);
        }

        if (!instanceRepository.completeExecution(
                workspaceId,
                executionId,
                expectedExecutionStatus,
                DataSyncInstanceStatus.FAILED,
                attemptNo,
                finishTime,
                readRows,
                writeRows,
                errorCode,
                errorMessage)) {
            throw new DataSyncException(DataSyncErrorCode.ATTEMPT_PERSIST_FAILED, "记录 Execution 最终失败状态失败");
        }
        return DataSyncRetryDecision.stop();
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelActiveAttempt(String workspaceId, String executionId) {
        LocalDateTime finishTime = DateUtils.now();
        attemptRepository.cancelActiveByExecution(workspaceId, executionId, finishTime);
        DataSyncInstanceEntity execution = instanceRepository.queryById(workspaceId, executionId).orElse(null);
        if (execution == null
                || execution.getStatus() == null
                || execution.getStatus().isTerminal()) return;
        instanceRepository.cancelExecution(workspaceId, executionId, execution.getStatus(), finishTime);
    }

    public boolean isRetryWaiting(String workspaceId, String executionId) {
        return instanceRepository
                .queryById(workspaceId, executionId)
                .map(value -> value.getStatus() == DataSyncInstanceStatus.RETRY_WAITING)
                .orElse(false);
    }
}
