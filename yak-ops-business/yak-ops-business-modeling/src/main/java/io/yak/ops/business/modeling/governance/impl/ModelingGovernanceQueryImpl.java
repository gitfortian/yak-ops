package io.yak.ops.business.modeling.governance.impl;

import io.yak.ops.business.modeling.governance.ModelingGovernanceQuery;
import io.yak.ops.business.modeling.governance.ModelingGovernanceRow;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.ModelingRelationRepository;
import io.yak.ops.dao.repository.modeling.RelationKeyScanRow;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Answers the two governance questions from modeling tables without leaking write access. */
@Component
public class ModelingGovernanceQueryImpl implements ModelingGovernanceQuery {
    @Resource
    private ModelRepository models;

    @Resource
    private ModelingRelationRepository relations;

    @Override
    public List<ModelingGovernanceRow> modelsMissingGrain(String workspaceId) {
        List<ModelingGovernanceRow> rows = new ArrayList<>();
        for (var model : models.listMissingGrain(workspaceId)) {
            rows.add(new ModelingGovernanceRow(model.getId(), model.getCnName()));
        }
        return rows;
    }

    @Override
    public List<ModelingGovernanceRow> modelsWithUnstandardizedRelationKeys(String workspaceId) {
        Map<String, ModelingGovernanceRow> accused = new LinkedHashMap<>();
        for (RelationKeyScanRow row : relations.scanUnstandardizedKeys(workspaceId)) {
            accused.computeIfAbsent(
                    row.getModelId(), key -> new ModelingGovernanceRow(row.getModelId(), row.getModelName()));
        }
        return new ArrayList<>(accused.values());
    }
}
