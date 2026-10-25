package io.yak.ops.dao.repository.modeling.standard.impl;

import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO.Item;
import io.yak.ops.common.enums.modeling.standard.StandardReferenceKind;
import io.yak.ops.dao.mapper.modeling.standard.StandardReferenceMapper;
import io.yak.ops.dao.repository.modeling.standard.StandardReferenceRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Reads reference projections and converts their stored kinds into the response contract. */
@Repository
public class StandardReferenceRepositoryImpl implements StandardReferenceRepository {
    @Resource
    private StandardReferenceMapper mapper;

    @Override
    public List<Item> modelFieldsByStandard(String workspaceId, String standardId) {
        return project(mapper.modelFieldsByStandard(workspaceId, standardId));
    }

    @Override
    public List<Item> byLookup(String workspaceId, String lookupId) {
        var standards = mapper.fieldStandardsByLookup(workspaceId, lookupId);
        var fields = mapper.modelFieldsByLookup(workspaceId, lookupId);
        var items = new ArrayList<Item>(standards.size() + fields.size());
        items.addAll(project(standards));
        items.addAll(project(fields));
        return List.copyOf(items);
    }

    @Override
    public List<Item> byUnit(String workspaceId, String unitId) {
        var standards = mapper.fieldStandardsByUnit(workspaceId, unitId);
        var fields = mapper.modelFieldsByUnit(workspaceId, unitId);
        var items = new ArrayList<Item>(standards.size() + fields.size());
        items.addAll(project(standards));
        items.addAll(project(fields));
        return List.copyOf(items);
    }

    @Override
    public List<Item> byNamingRoot(String workspaceId, String entryId) {
        return project(mapper.modelsByNamingRoot(workspaceId, entryId));
    }

    private static List<Item> project(List<StandardReferenceMapper.ReferenceRow> rows) {
        return rows.stream()
                .map(row -> new Item(
                        StandardReferenceKind.valueOf(row.getKind()), row.getId(), row.getName(), row.getPath()))
                .toList();
    }
}
