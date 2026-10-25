package io.yak.ops.dao.repository.modeling.metric.impl;

import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.enums.modeling.metric.MetricReferenceKind;
import io.yak.ops.dao.mapper.modeling.metric.MetricReferenceMapper;
import io.yak.ops.dao.repository.modeling.metric.MetricReferenceRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Reads metric reference projections and converts their stored kinds into the response contract. */
@Repository
public class MetricReferenceRepositoryImpl implements MetricReferenceRepository {
    @Resource
    private MetricReferenceMapper mapper;

    @Override
    public List<MetricReferencesVO.Item> byAtomic(String workspaceId, String atomicId) {
        return project(mapper.derivedByAtomic(workspaceId, atomicId));
    }

    @Override
    public List<MetricReferencesVO.Item> byModifier(String workspaceId, String modifierId) {
        return project(mapper.derivedByModifier(workspaceId, modifierId));
    }

    @Override
    public List<MetricReferencesVO.Item> byPeriod(String workspaceId, String periodId) {
        return project(mapper.derivedByPeriod(workspaceId, periodId));
    }

    @Override
    public List<MetricReferencesVO.Item> byDerived(String workspaceId, String derivedId) {
        var composites = mapper.compositesByDerived(workspaceId, derivedId);
        var fields = mapper.modelFieldsByMetric(workspaceId, derivedId);
        var items = new ArrayList<MetricReferencesVO.Item>(composites.size() + fields.size());
        items.addAll(project(composites));
        items.addAll(project(fields));
        return List.copyOf(items);
    }

    private static List<MetricReferencesVO.Item> project(List<MetricReferenceMapper.ReferenceRow> rows) {
        return rows.stream()
                .map(row -> new MetricReferencesVO.Item(
                        MetricReferenceKind.valueOf(row.getKind()), row.getId(), row.getName(), row.getPath()))
                .toList();
    }
}
