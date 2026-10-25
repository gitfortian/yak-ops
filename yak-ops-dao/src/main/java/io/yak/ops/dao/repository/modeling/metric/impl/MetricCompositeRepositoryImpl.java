package io.yak.ops.dao.repository.modeling.metric.impl;

import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.metric.MetricCompositeEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricCompositeRefEntity;
import io.yak.ops.dao.mapper.modeling.metric.MetricCompositeMapper;
import io.yak.ops.dao.repository.modeling.metric.MetricCompositeRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Executes bound composite-metric persistence and reference replacement in the caller's transaction. */
@Repository
public class MetricCompositeRepositoryImpl implements MetricCompositeRepository {
    @Resource
    private MetricCompositeMapper mapper;

    @Override
    public List<MetricCompositeEntity> page(String workspaceId, MetricQuery query) {
        var rows = mapper.page(workspaceId, query);
        if (!rows.isEmpty()) {
            Map<String, List<MetricCompositeRefEntity>> grouped =
                    mapper.referencesByComposite(
                                    workspaceId, rows.stream().map(MetricCompositeEntity::getId).toList())
                            .stream()
                            .collect(Collectors.groupingBy(MetricCompositeRefEntity::getCompositeId));
            for (var row : rows) {
                row.setReferences(grouped.getOrDefault(row.getId(), List.of()));
            }
        }
        return rows;
    }

    @Override
    public long count(String workspaceId, MetricQuery query) {
        return mapper.count(workspaceId, query);
    }

    @Override
    public Optional<MetricCompositeEntity> find(String workspaceId, String id) {
        var row = mapper.find(workspaceId, id);
        if (row == null) return Optional.empty();
        row.setReferences(mapper.references(workspaceId, id));
        return Optional.of(row);
    }

    @Override
    public void insert(MetricCompositeEntity node) {
        mapper.insert(node);
        for (var reference : node.getReferences()) {
            mapper.insertReference(reference(node, reference.getDerivedMetricId()));
        }
    }

    @Override
    public boolean update(MetricCompositeEntity node) {
        var updated = mapper.update(node) == 1;
        if (updated) {
            mapper.deleteReferences(node.getWorkspaceId(), node.getId());
            for (var reference : node.getReferences()) {
                mapper.insertReference(reference(node, reference.getDerivedMetricId()));
            }
        }
        return updated;
    }

    @Override
    public boolean delete(String workspaceId, String id, long revision) {
        return mapper.delete(workspaceId, id, revision) == 1;
    }

    @Override
    public boolean occupied(String workspaceId, String enCode, String excludeId) {
        return mapper.occupied(workspaceId, enCode, excludeId) > 0;
    }

    private static MetricCompositeRefEntity reference(MetricCompositeEntity row, String derivedMetricId) {
        var reference = new MetricCompositeRefEntity();
        reference.setId(IdUtils.nextId());
        reference.setWorkspaceId(row.getWorkspaceId());
        reference.setCompositeId(row.getId());
        reference.setDerivedMetricId(derivedMetricId);
        return reference;
    }
}
