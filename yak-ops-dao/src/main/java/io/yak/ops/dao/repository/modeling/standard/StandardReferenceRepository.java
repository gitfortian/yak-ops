package io.yak.ops.dao.repository.modeling.standard;

import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import java.util.List;

/** On-demand reference lists across the standard kinds and the models that consume them. */
public interface StandardReferenceRepository {
    /**
     * Reads the child standards and model fields that depend on one field standard.
     * @param workspaceId authorized current Workspace
     * @param standardId target field standard
     * @return dependants, child standards first, then model fields ordered by model and field code
     */
    List<StandardReferencesVO.Item> byStandard(String workspaceId, String standardId);
    /**
     * Reads the field standards and model fields that reference one lookup table.
     * @param workspaceId authorized current Workspace
     * @param lookupId target lookup table
     * @return dependants, standards first
     */
    List<StandardReferencesVO.Item> byLookup(String workspaceId, String lookupId);
    /**
     * Reads the field standards and model fields that reference one measurement unit.
     * @param workspaceId authorized current Workspace
     * @param unitId target unit
     * @return dependants, standards first
     */
    List<StandardReferencesVO.Item> byUnit(String workspaceId, String unitId);
    /**
     * Reads the models whose name was composed from one naming entry.
     * @param workspaceId authorized current Workspace
     * @param entryId target naming entry
     * @return dependant models ordered by table name
     */
    List<StandardReferencesVO.Item> byNamingRoot(String workspaceId, String entryId);
}
