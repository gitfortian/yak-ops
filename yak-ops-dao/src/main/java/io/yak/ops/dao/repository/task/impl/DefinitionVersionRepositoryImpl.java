package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.mapper.task.DefinitionVersionMapper;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Append-only Task Definition snapshots. */
@Repository
public class DefinitionVersionRepositoryImpl implements DefinitionVersionRepository {
    @Resource
    private DefinitionVersionMapper versionMapper;

    @Override
    public DefinitionVersionEntity append(DefinitionVersionEntity version) {
        versionMapper.insert(version);
        return version;
    }

    @Override
    public Optional<DefinitionVersionEntity> queryCurrent(String workspaceId, String definitionId, int version) {
        return Optional.ofNullable(versionMapper.selectOne(Wrappers.<DefinitionVersionEntity>lambdaQuery()
                .eq(DefinitionVersionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionVersionEntity::getDefinitionId, definitionId)
                .eq(DefinitionVersionEntity::getVersion, version)));
    }

    @Override
    public List<DefinitionVersionEntity> queryByDefinition(String workspaceId, String definitionId) {
        return versionMapper.selectList(Wrappers.<DefinitionVersionEntity>lambdaQuery()
                .eq(DefinitionVersionEntity::getWorkspaceId, workspaceId)
                .eq(DefinitionVersionEntity::getDefinitionId, definitionId)
                .orderByDesc(DefinitionVersionEntity::getVersion));
    }
}
