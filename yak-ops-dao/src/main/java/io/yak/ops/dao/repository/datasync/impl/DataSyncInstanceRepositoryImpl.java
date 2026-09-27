package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncInstanceMapper;
import io.yak.ops.dao.repository.datasync.DataSyncInstancePageQuery;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped 数据同步实例分页与详情查询。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Repository
public class DataSyncInstanceRepositoryImpl extends BaseRepositoryImpl<DataSyncInstanceMapper, DataSyncInstanceEntity>
        implements DataSyncInstanceRepository {

    @Resource
    private DataSyncInstanceMapper instanceMapper;

    @Override
    protected DataSyncInstanceMapper mapper() {
        return instanceMapper;
    }

    @Override
    public PageData<DataSyncInstanceEntity> queryPage(String workspaceId, DataSyncInstancePageQuery query) {
        DataSyncInstancePageQuery condition =
                query == null ? new DataSyncInstancePageQuery(1, 10, null, null, null, null, null, null) : query;
        Page<DataSyncInstanceEntity> page = Page.of(Math.max(1, condition.pageNo()), Math.max(1, condition.pageSize()));
        IPage<DataSyncInstanceEntity> result = instanceMapper.selectPage(
                page,
                queryWrapper(workspaceId, condition)
                        .orderByDesc(DataSyncInstanceEntity::getCreateTime)
                        .orderByDesc(DataSyncInstanceEntity::getId));
        return new PageData<>(
                result.getRecords(), result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
    }

    @Override
    public Optional<DataSyncInstanceEntity> queryById(String workspaceId, String id) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(id)) return Optional.empty();
        return Optional.ofNullable(instanceMapper.selectOne(Wrappers.<DataSyncInstanceEntity>lambdaQuery()
                .eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncInstanceEntity::getId, id)));
    }

    private LambdaQueryWrapper<DataSyncInstanceEntity> queryWrapper(
            String workspaceId, DataSyncInstancePageQuery query) {
        LambdaQueryWrapper<DataSyncInstanceEntity> wrapper =
                Wrappers.<DataSyncInstanceEntity>lambdaQuery().eq(DataSyncInstanceEntity::getWorkspaceId, workspaceId);
        return wrapper.eq(StringUtils.hasText(query.taskId()), DataSyncInstanceEntity::getTaskId, query.taskId())
                .like(StringUtils.hasText(query.keyword()), DataSyncInstanceEntity::getTaskName, query.keyword())
                .eq(query.status() != null, DataSyncInstanceEntity::getStatus, query.status())
                .eq(query.triggerType() != null, DataSyncInstanceEntity::getTriggerType, query.triggerType())
                .ge(query.startTimeStart() != null, DataSyncInstanceEntity::getStartTime, query.startTimeStart())
                .le(query.startTimeEnd() != null, DataSyncInstanceEntity::getStartTime, query.startTimeEnd());
    }
}
