package io.yak.ops.business.modeling.relation;

import io.yak.ops.common.bean.dto.modeling.CreateModelRelationDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelGrainDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelRelationDTO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationProposalVO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationVO;
import java.util.List;

/** Workspace semantic relations between logical models, their key proposals and the model grain. */
public interface ModelRelationService {
    /**
     * Lists relations where the model is either side, keys resolved against current fields.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param modelId model identity
     * @return relations ordered with attention-needing ones first
     */
    List<ModelRelationVO> list(String workspaceId, String actor, String modelId);

    /**
     * Creates one many-to-one relation from the model to a DIM model.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param modelId many-side model identity
     * @param input target model, optional name, join type and ordered key pairs
     * @return the created relation with resolved keys
     */
    ModelRelationVO create(String workspaceId, String actor, String modelId, CreateModelRelationDTO input);

    /**
     * Replaces the editable surface of one relation under an optimistic revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param relationId relation identity
     * @param input name, join type, key mapping and expected revision
     * @return the updated relation with resolved keys
     */
    ModelRelationVO update(String workspaceId, String actor, String relationId, UpdateModelRelationDTO input);

    /**
     * Deletes one relation; key pairs cascade with it.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param relationId relation identity
     * @param revision expected revision
     */
    void delete(String workspaceId, String actor, String relationId, long revision);

    /**
     * Proposes join key pairs between two models, ordered by standard match and capped.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param modelId many-side model identity
     * @param targetModelId one-side DIM model identity
     * @return at most three candidates, blocked ones included with a reason
     */
    List<ModelRelationProposalVO> proposals(String workspaceId, String actor, String modelId, String targetModelId);

    /**
     * Declares the meaning of one row of the model.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param modelId model identity
     * @param input grain statement and expected revision
     */
    void updateGrain(String workspaceId, String actor, String modelId, UpdateModelGrainDTO input);
}
