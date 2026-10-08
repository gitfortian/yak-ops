package io.yak.ops.dao.repository.datasync;

import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/**
 * 定义 Workspace-scoped Data Sync Table Execution 持久化能力。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
public interface DataSyncTableExecutionRepository extends BaseRepository<DataSyncTableExecutionEntity> {

    Optional<DataSyncTableExecutionEntity> queryById(String workspaceId, String id);

    List<DataSyncTableExecutionEntity> queryByExecution(String workspaceId, String executionId);
}
