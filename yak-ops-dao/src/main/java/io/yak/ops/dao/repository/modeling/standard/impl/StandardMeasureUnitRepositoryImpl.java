package io.yak.ops.dao.repository.modeling.standard.impl;

import io.yak.ops.dao.entity.modeling.standard.StandardMeasureUnitEntity;
import io.yak.ops.dao.mapper.modeling.standard.StandardMeasureUnitMapper;
import io.yak.ops.dao.repository.modeling.standard.StandardMeasureUnitRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes bound unit persistence in the calling business transaction. */
@Repository
public class StandardMeasureUnitRepositoryImpl implements StandardMeasureUnitRepository {
    @Resource
    private StandardMeasureUnitMapper mapper;

    @Override
    public List<StandardMeasureUnitEntity> page(String workspaceId, StandardQuery query) {
        return mapper.page(workspaceId, query);
    }

    @Override
    public long count(String workspaceId, StandardQuery query) {
        return mapper.count(workspaceId, query);
    }

    @Override
    public Optional<StandardMeasureUnitEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public void insert(StandardMeasureUnitEntity node) {
        mapper.insert(node);
    }

    @Override
    public boolean update(StandardMeasureUnitEntity node) {
        return mapper.update(node) == 1;
    }

    @Override
    public boolean delete(String workspaceId, String id, long revision) {
        return mapper.delete(workspaceId, id, revision) == 1;
    }

    @Override
    public boolean occupied(String workspaceId, String enCode, String excludeId) {
        return mapper.occupied(workspaceId, enCode, excludeId) > 0;
    }
}
