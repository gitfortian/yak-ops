package io.yak.ops.business.modeling.dimension;

import io.yak.ops.common.bean.dto.modeling.DimensionInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDimensionDTO;
import io.yak.ops.common.bean.vo.modeling.DimensionVO;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import java.util.List;

/**
 * Workspace concept dimensions.
 *
 * <p>A dimension belongs to exactly one data domain (public layer) or one data mart (application
 * layer). Its abbreviation is unique inside that scope only, so the same abbreviation can exist once
 * per domain or data mart. Ownership is immutable after creation: moving a dimension would silently
 * change which abbreviations collide.
 */
public interface DimensionService {
    /**
     * Lists dimensions, optionally narrowed by scope.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param scope optional owning layer filter
     * @param domainId optional data domain filter
     * @param datamartId optional data mart filter
     * @return ordered dimensions with their owning scope name
     */
    List<DimensionVO> list(String workspaceId, String actor, DimensionScope scope, String domainId, String datamartId);

    /**
     * Creates a dimension inside the required scope.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param input editable fields including the owning scope
     * @return committed dimension
     */
    DimensionVO create(String workspaceId, String actor, DimensionInputDTO input);

    /**
     * Updates editable fields; ownership and revision are validated.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param id dimension identity
     * @param input replacement fields and expected revision
     * @return committed dimension with the incremented revision
     */
    DimensionVO update(String workspaceId, String actor, String id, UpdateDimensionDTO input);

    /**
     * Deletes a dimension that no dimension table references yet.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param id dimension identity
     * @param revision expected revision
     */
    void delete(String workspaceId, String actor, String id, long revision);
}
