package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.BaseRepository;
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
}
