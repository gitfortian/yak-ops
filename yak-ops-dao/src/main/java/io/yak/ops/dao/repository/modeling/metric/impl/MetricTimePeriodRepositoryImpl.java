package io.yak.ops.dao.repository.modeling.metric.impl;

import io.yak.ops.dao.entity.modeling.metric.MetricTimePeriodEntity;
import io.yak.ops.dao.mapper.modeling.metric.MetricTimePeriodMapper;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import io.yak.ops.dao.repository.modeling.metric.MetricTimePeriodRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes bound time-period persistence in the calling business transaction. */
@Repository
public class MetricTimePeriodRepositoryImpl implements MetricTimePeriodRepository {
    @Resource
    private MetricTimePeriodMapper mapper;

    @Override
    public List<MetricTimePeriodEntity> page(String workspaceId, MetricQuery query) {
        return mapper.page(workspaceId, query);
    }

    @Override
    public long count(String workspaceId, MetricQuery query) {
        return mapper.count(workspaceId, query);
    }

    @Override
    public Optional<MetricTimePeriodEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public void insert(MetricTimePeriodEntity node) {
        mapper.insert(node);
    }

    @Override
    public boolean update(MetricTimePeriodEntity node) {
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
