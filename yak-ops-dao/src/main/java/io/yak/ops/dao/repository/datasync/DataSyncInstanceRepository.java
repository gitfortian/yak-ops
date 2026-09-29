package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 定义 Workspace-scoped 数据同步任务实例持久化能力。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface DataSyncInstanceRepository extends BaseRepository<DataSyncInstanceEntity> {

    PageData<DataSyncInstanceEntity> queryPage(String workspaceId, DataSyncInstancePageQuery query);

    Optional<DataSyncInstanceEntity> queryById(String workspaceId, String id);

    boolean existsActiveByTask(String workspaceId, String taskId);

    boolean updateMetrics(String workspaceId, String id, long readRows, long writeRows);

    boolean startAttempt(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            int attemptNo,
            LocalDateTime startTime);

    boolean waitForRetry(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            int attemptNo,
            LocalDateTime nextRetryTime,
            long readRows,
            long writeRows,
            Integer errorCode,
            String errorMessage);

    boolean completeExecution(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            DataSyncInstanceStatus targetStatus,
            int attemptNo,
            LocalDateTime finishTime,
            long readRows,
            long writeRows,
            Integer errorCode,
            String errorMessage);

    boolean cancelExecution(
            String workspaceId, String id, DataSyncInstanceStatus expectedStatus, LocalDateTime finishTime);

    boolean transitionStatus(
            String workspaceId,
            String id,
            DataSyncInstanceStatus expectedStatus,
            DataSyncInstanceStatus targetStatus,
            LocalDateTime startTime,
            LocalDateTime finishTime,
            Integer errorCode,
            String errorMessage);

    /**
     * 应用启动恢复专用：把所有 Workspace 中遗留的 PENDING / RUNNING 实例统一标记为 LOST。
     */
    int markActiveAsLost(LocalDateTime finishTime, Integer errorCode, String errorMessage);
}
