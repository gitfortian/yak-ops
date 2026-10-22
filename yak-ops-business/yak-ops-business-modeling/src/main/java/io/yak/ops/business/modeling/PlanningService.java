package io.yak.ops.business.modeling;

import io.yak.ops.common.bean.dto.modeling.NodeInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateNodeDTO;
import io.yak.ops.common.bean.vo.modeling.AssociatedModelsVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.PlanningNodeVO;
import io.yak.ops.common.bean.vo.modeling.SavedNodeVO;
import io.yak.ops.common.enums.modeling.NodeKind;
import java.util.List;

/** Workspace-authorized warehouse planning and globally reserved codes. */
public interface PlanningService {
    /**
     * Reads the complete authorized tree in a consistent snapshot.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @return ordered categories with immutable child collections
     */
    List<PlanningNodeVO> tree(String workspaceId, String actor);
    /**
     * Projects only eligible Workspace members.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @return user IDs and display names without account-private fields
     */
    List<OwnerOptionVO> owners(String workspaceId, String actor);
    /**
     * Checks global availability without reserving or disclosing the occupying node.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param code exact candidate code
     * @param excludeKind edited level, null for creation
     * @param excludeId edited identity, null for creation
     * @return true if no other node owns the code
     */
    boolean available(String workspaceId, String actor, String code, NodeKind excludeKind, String excludeId);
    /**
     * Creates a node and code reservation in one transaction after locking its ancestors.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param kind fixed level
     * @param parentId immutable parent, null only for a category
     * @param input editable fields
     * @return committed identity and revision
     */
    SavedNodeVO create(String workspaceId, String actor, NodeKind kind, String parentId, NodeInputDTO input);
    /**
     * Updates fields and code atomically; stale revisions conflict.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param kind fixed level
     * @param id node identity
     * @param input replacement fields and expected revision
     * @return committed identity and incremented revision
     */
    SavedNodeVO update(String workspaceId, String actor, NodeKind kind, String id, UpdateNodeDTO input);
    /**
     * Physically deletes a dependency-free node and releases its code atomically.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param kind fixed level
     * @param id node identity
     * @param revision expected revision
     */
    void delete(String workspaceId, String actor, NodeKind kind, String id, long revision);
    /**
     * Authorizes a process before reporting the unavailable logical-model capability.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param processId process identity
     * @return explicit NOT_ENABLED state, not an empty successful model query
     */
    AssociatedModelsVO associatedModels(String workspaceId, String actor, String processId);
}
