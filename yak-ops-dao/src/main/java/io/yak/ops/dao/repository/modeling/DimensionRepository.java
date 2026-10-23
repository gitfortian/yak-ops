package io.yak.ops.dao.repository.modeling;

import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.dao.entity.modeling.DimensionEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace concept dimensions and their scope uniqueness. */
public interface DimensionRepository {
    /**
     * Lists dimensions in one scope-ordered result.
     * @param workspaceId authorized Workspace
     * @param scope optional owning layer filter
     * @param domainId optional data domain filter
     * @param datamartId optional data mart filter
     * @return matching rows with their owning scope name
     */
    List<DimensionEntity> dimensions(String workspaceId, DimensionScope scope, String domainId, String datamartId);

    /**
     * Reads one row without exposing another Workspace.
     * @param workspaceId authorized Workspace
     * @param id dimension identity
     * @return matching row with its owning scope name, or empty
     */
    Optional<DimensionEntity> find(String workspaceId, String id);

    /**
     * Locks one row for an optimistic update or deletion.
     * @param workspaceId authorized Workspace
     * @param id dimension identity
     * @return locked row or empty
     */
    Optional<DimensionEntity> lock(String workspaceId, String id);

    /**
     * Counts rows owning the abbreviation inside one scope, excluding the edited row.
     * @param workspaceId authorized Workspace
     * @param ownerScopeId owning data domain or data mart identity
     * @param code syntactically valid abbreviation
     * @param excludeId edited identity, or null for creation
     * @return number of conflicting rows
     */
    long countCode(String workspaceId, String ownerScopeId, String code, String excludeId);

    /**
     * Checks that a data domain belongs to the Workspace.
     * @param workspaceId authorized Workspace
     * @param id data domain identity
     * @return true when the row exists
     */
    boolean domainExists(String workspaceId, String id);

    /**
     * Checks that a data mart belongs to the Workspace.
     * @param workspaceId authorized Workspace
     * @param id data mart identity
     * @return true when the row exists
     */
    boolean datamartExists(String workspaceId, String id);

    /**
     * Inserts a dimension in the caller transaction.
     * @param row fully initialized identity, scope and audit fields
     */
    void add(DimensionEntity row);

    /**
     * Replaces editable fields with an optimistic guard.
     * @param row replacement fields with immutable identity and scope
     * @return true if the matching row was updated
     */
    boolean update(DimensionEntity row);

    /**
     * Removes a row with an optimistic guard.
     * @param workspaceId authorized Workspace
     * @param id dimension identity
     * @param revision expected version
     * @return true if the matching row was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
}
