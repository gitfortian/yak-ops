package io.yak.ops.dao.repository.modeling;

import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of scoped hierarchy rows and their atomically owned global codes. */
public interface PlanningRepository {
    /**
     * Reads all levels in one database snapshot, ordered by code and ID.
     * @param workspaceId authorized Workspace
     * @return complete flat node projection
     */
    List<PlanningNodeEntity> tree(String workspaceId);
    /**
     * Finds a row without exposing another Workspace.
     * @param workspaceId authorized Workspace
     * @param kind fixed table level
     * @param id node identity
     * @return matching row or empty
     */
    Optional<PlanningNodeEntity> find(String workspaceId, NodeKind kind, String id);
    /**
     * Locks a row after its ancestors have been locked by the caller.
     * @param workspaceId authorized Workspace
     * @param kind fixed table level
     * @param id node identity
     * @return locked row or empty
     */
    Optional<PlanningNodeEntity> lock(String workspaceId, NodeKind kind, String id);
    /**
     * Reserves a global code and inserts its row in the caller transaction.
     * @param node fully initialized identity, level, ownership and audit fields
     */
    void add(PlanningNodeEntity node);
    /**
     * Replaces editable fields and code reservation with an optimistic guard.
     * @param node replacement fields with immutable identity and parent
     * @param revision expected version
     * @param codeChanged whether the locked row uses a different code
     * @return true if the matching row was updated
     */
    boolean update(PlanningNodeEntity node, long revision, boolean codeChanged);
    /**
     * Removes a row with an optimistic guard and releases its code in the same transaction.
     * @param workspaceId authorized Workspace
     * @param kind fixed table level
     * @param id node identity
     * @param revision expected version
     * @return true if the matching row was deleted
     */
    boolean delete(String workspaceId, NodeKind kind, String id, long revision);
    /**
     * Checks direct child dependencies with a current locking read after locking the parent.
     * @param workspaceId authorized Workspace
     * @param kind parent level
     * @param id parent identity
     * @return true when a direct child exists
     */
    boolean hasChildren(String workspaceId, NodeKind kind, String id);
    /**
     * Checks global occupancy without disclosing its owner.
     * @param code syntactically valid code
     * @param excludeKind authorized edited level, or null
     * @param excludeId authorized edited identity, or null
     * @return true when another node owns the code
     */
    boolean occupied(String code, NodeKind excludeKind, String excludeId);
}
