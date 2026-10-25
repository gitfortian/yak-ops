package io.yak.ops.dao.repository.modeling.metric.impl;

import io.yak.ops.dao.entity.modeling.metric.MetricModifierEntity;
import io.yak.ops.dao.mapper.modeling.metric.MetricModifierMapper;
import io.yak.ops.dao.repository.modeling.metric.MetricModifierRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes bound modifier persistence in the calling business transaction. */
@Repository
public class MetricModifierRepositoryImpl implements MetricModifierRepository {
    @Resource
    private MetricModifierMapper mapper;

    @Override
    public List<MetricModifierEntity> page(String workspaceId, MetricQuery query) {
        return mapper.page(workspaceId, query);
    }

    @Override
    public long count(String workspaceId, MetricQuery query) {
        return mapper.count(workspaceId, query);
    }

    @Override
    public Optional<MetricModifierEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public void insert(MetricModifierEntity node) {
        mapper.insert(node);
    }

    @Override
    public boolean update(MetricModifierEntity node) {
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
