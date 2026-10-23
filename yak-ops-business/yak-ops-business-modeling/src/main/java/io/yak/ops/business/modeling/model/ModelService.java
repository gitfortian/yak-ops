package io.yak.ops.business.modeling.model;

import io.yak.ops.common.bean.dto.modeling.ModelInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelDTO;
import io.yak.ops.common.bean.vo.modeling.ModelVO;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.page.PageData;

/** Workspace logical models: DWD detail tables and DIM dimension tables with their ordered fields. */
public interface ModelService {
    /**
     * Reads a filtered model page without fields.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param type kind filter, or null for every kind
     * @param layerId layer filter, or null
     * @param ownerId owner filter, or null
     * @param domainId data domain filter, or null
     * @param datamartId data mart filter, or null
     * @param keyword table name or display name fragment, or null
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return matching models and the filter total
     */
    PageData<ModelVO> page(
            String workspaceId,
            String actor,
            ModelType type,
            String layerId,
            String ownerId,
            String domainId,
            String datamartId,
            String keyword,
            int pageNo,
            int pageSize);

    /**
     * Reads one model with its ordered fields.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id model identity
     * @return the model and its fields
     */
    ModelVO detail(String workspaceId, String actor, String id);

    /**
     * Creates a model and its fields in one transaction.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input editable model fields and the submitted field list
     * @return the committed model with its fields
     */
    ModelVO create(String workspaceId, String actor, ModelInputDTO input);

    /**
     * Replaces basic attributes and the whole field list under the observed revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id model identity
     * @param input replacement fields and the expected revision
     * @return the committed model with its new fields
     */
    ModelVO update(String workspaceId, String actor, String id, UpdateModelDTO input);

    /**
     * Deletes a model; its fields follow.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id model identity
     * @param revision expected version
     */
    void delete(String workspaceId, String actor, String id, long revision);

    /**
     * Checks the full table name built from the layer prefix and the suffix.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param layerId selected layer supplying the prefix
     * @param nameSuffix user-entered suffix
     * @param excludeId edited model identity, or null for creation
     * @return true when no other model owns the full name
     */
    boolean nameAvailable(String workspaceId, String actor, String layerId, String nameSuffix, String excludeId);
}
