package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncTableExecutionMapper;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 实现 Workspace-scoped Table Execution 查询。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Repository
public class DataSyncTableExecutionRepositoryImpl
        extends BaseRepositoryImpl<DataSyncTableExecutionMapper, DataSyncTableExecutionEntity>
        implements DataSyncTableExecutionRepository {

    @Resource
    private DataSyncTableExecutionMapper tableExecutionMapper;

    @Override
    protected DataSyncTableExecutionMapper mapper() {
        return tableExecutionMapper;
    }

    @Override
    public Optional<DataSyncTableExecutionEntity> queryById(String workspaceId, String id) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(id)) return Optional.empty();
        return Optional.ofNullable(tableExecutionMapper.selectOne(Wrappers.<DataSyncTableExecutionEntity>lambdaQuery()
                .eq(DataSyncTableExecutionEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableExecutionEntity::getId, id)));
    }

    @Override
    public List<DataSyncTableExecutionEntity> queryByExecution(String workspaceId, String executionId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(executionId)) return List.of();
        return tableExecutionMapper.selectList(Wrappers.<DataSyncTableExecutionEntity>lambdaQuery()
                .eq(DataSyncTableExecutionEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncTableExecutionEntity::getExecutionId, executionId)
                .orderByAsc(DataSyncTableExecutionEntity::getRouteOrder)
                .orderByAsc(DataSyncTableExecutionEntity::getId));
    }
}
