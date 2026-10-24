package io.yak.ops.business.modeling;

import io.yak.ops.common.bean.dto.modeling.DatamartInputDTO;
import io.yak.ops.common.bean.dto.modeling.NodeInputDTO;
import io.yak.ops.common.bean.dto.modeling.SubjectInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDatamartDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateNodeDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateSubjectDTO;
import io.yak.ops.common.bean.vo.modeling.AssociatedModelsVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.PlanningTreeVO;
import io.yak.ops.common.bean.vo.modeling.SavedNodeVO;
import io.yak.ops.common.enums.modeling.NodeKind;
import java.util.List;

/**
 * Workspace-authorized warehouse planning and globally reserved codes.
 *
 * <p>The public-layer business tree and the application-layer data mart tree are separate views over
 * one aggregate: both reserve abbreviations in the same global namespace and share revision, locking
 * and deletion rules. Data marts and subjects therefore expose their own field contracts while
 * reusing the generic {@link #delete} operation.
 */
public interface PlanningService {
    /**
     * Reads both trees in one consistent snapshot.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @return category roots and data mart roots with immutable child collections
     */
    PlanningTreeVO tree(String workspaceId, String actor);
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
     * @param parentId immutable parent, null only for a root level
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
     * Creates a data mart as an application-layer root.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param input editable fields including the responsible owner
     * @return committed identity and revision
     */
    SavedNodeVO createDatamart(String workspaceId, String actor, DatamartInputDTO input);
    /**
     * Updates a data mart after locking it and its code reservation.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param id data mart identity
     * @param input replacement fields and expected revision
     * @return committed identity and incremented revision
     */
    SavedNodeVO updateDatamart(String workspaceId, String actor, String id, UpdateDatamartDTO input);
    /**
     * Creates a subject area under one data mart.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param datamartId owning data mart identity
     * @param input editable fields; subjects carry no owner
     * @return committed identity and revision
     */
    SavedNodeVO createSubject(String workspaceId, String actor, String datamartId, SubjectInputDTO input);
    /**
     * Updates a subject area after locking it and its code reservation.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param id subject identity
     * @param input replacement fields and expected revision
     * @return committed identity and incremented revision
     */
    SavedNodeVO updateSubject(String workspaceId, String actor, String id, UpdateSubjectDTO input);
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
     * Reads one page of models hanging below a business process.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param processId process identity
     * @param pageNo one-based page number
     * @param pageSize page size
     * @return available capability with one page of model summaries
     */
    AssociatedModelsVO associatedModels(String workspaceId, String actor, String processId, int pageNo, int pageSize);
}
