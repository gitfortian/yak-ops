package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.dao.entity.modeling.ModelingSynonymEntity;
import io.yak.ops.dao.mapper.modeling.ModelingSynonymMapper;
import io.yak.ops.dao.repository.modeling.ModelingSynonymRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Executes semantic synonym statements inside the owning business transaction. */
@Repository
public class ModelingSynonymRepositoryImpl implements ModelingSynonymRepository {
    @Resource
    private ModelingSynonymMapper mapper;

    @Override
    public List<ModelingSynonymEntity> listByTarget(String workspaceId, String targetType, String targetId) {
        return mapper.listByTarget(workspaceId, targetType, targetId);
    }

    @Override
    public void deleteByTarget(String workspaceId, String targetType, String targetId) {
        mapper.deleteByTarget(workspaceId, targetType, targetId);
    }

    @Override
    public void add(ModelingSynonymEntity row) {
        mapper.insert(row);
    }

    @Override
    public List<ModelingSynonymEntity> listByTargets(String workspaceId, String targetType, List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) return List.of();
        return mapper.listByTargets(workspaceId, targetType, targetIds);
    }
}
