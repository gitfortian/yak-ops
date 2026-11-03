package io.yak.ops.business.modeling.governance;

import java.util.List;

/** Read-only modeling facts the governance scanner consumes. Owned by the modeling module. */
public interface ModelingGovernanceQuery {
    /**
     * Lists DWD/DWS/ADS models that declare no grain.
     * @param workspaceId authorized Workspace
     * @return one row per model, ordered by name
     */
    List<ModelingGovernanceRow> modelsMissingGrain(String workspaceId);

    /**
     * Lists models owning at least one relation key whose field is missing or unstandardized.
     * @param workspaceId authorized Workspace
     * @return one row per (relation, model) pair; the consumer deduplicates by model
     */
    List<ModelingGovernanceRow> modelsWithUnstandardizedRelationKeys(String workspaceId);
}
