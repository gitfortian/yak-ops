package io.yak.ops.dao.repository.modeling.metric.impl;

import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedModifierEntity;
import io.yak.ops.dao.mapper.modeling.metric.MetricDerivedMapper;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Executes bound derived-metric persistence and link replacement in the caller's transaction. */
@Repository
public class MetricDerivedRepositoryImpl implements MetricDerivedRepository {
    @Resource
    private MetricDerivedMapper mapper;

    @Override
    public List<MetricDerivedEntity> page(String workspaceId, MetricQuery query) {
        var rows = mapper.page(workspaceId, query);
        attach(
                rows,
                mapper.modifierLinksByDerived(
                        workspaceId,
                        rows.stream().map(MetricDerivedEntity::getId).toList()));
        return rows;
    }

    @Override
    public long count(String workspaceId, MetricQuery query) {
        return mapper.count(workspaceId, query);
    }

    @Override
    public Optional<MetricDerivedEntity> find(String workspaceId, String id) {
        var row = mapper.find(workspaceId, id);
        if (row == null) return Optional.empty();
        row.setModifiers(mapper.modifierLinks(workspaceId, id));
        return Optional.of(row);
    }

    @Override
    public void insert(MetricDerivedEntity node) {
        mapper.insert(node);
        var links = node.getModifiers() == null ? List.<MetricDerivedModifierEntity>of() : node.getModifiers();
        for (var index = 0; index < links.size(); index++) {
            mapper.insertModifierLink(link(node, links.get(index).getModifierId(), index + 1));
        }
    }

    @Override
    public boolean update(MetricDerivedEntity node) {
        var updated = mapper.update(node) == 1;
        if (updated) {
            var links = node.getModifiers() == null ? List.<MetricDerivedModifierEntity>of() : node.getModifiers();
            mapper.deleteModifierLinks(node.getWorkspaceId(), node.getId());
            for (var index = 0; index < links.size(); index++) {
                mapper.insertModifierLink(link(node, links.get(index).getModifierId(), index + 1));
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

    @Override
    public Optional<MetricDerivedEntity> row(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public Optional<MetricDerivedEntity> byCode(String workspaceId, String enCode) {
        return Optional.ofNullable(mapper.findByCode(workspaceId, enCode));
    }

    private void attach(List<MetricDerivedEntity> rows, List<MetricDerivedModifierEntity> links) {
        if (rows.isEmpty()) return;
        Map<String, List<MetricDerivedModifierEntity>> grouped =
                links.stream().collect(Collectors.groupingBy(MetricDerivedModifierEntity::getDerivedId));
        for (var row : rows) {
            row.setModifiers(grouped.getOrDefault(row.getId(), List.of()));
        }
    }

    private static MetricDerivedModifierEntity link(MetricDerivedEntity row, String modifierId, int ordinal) {
        var link = new MetricDerivedModifierEntity();
        link.setId(IdUtils.nextId());
        link.setWorkspaceId(row.getWorkspaceId());
        link.setDerivedId(row.getId());
        link.setModifierId(modifierId);
        link.setOrdinal(ordinal);
        return link;
    }
}
