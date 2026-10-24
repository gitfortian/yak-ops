package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace DWD/DIM models and their ordered fields. */
public interface ModelRepository {
    /**
     * Reads one filtered page of models together with their joined display names.
     * @param workspaceId authorized Workspace
     * @param type persisted model type filter, or null for every type
     * @param layerId layer filter, or null
     * @param ownerId owner filter, or null
     * @param domainId domain filter, or null
     * @param datamartId data mart filter, or null
     * @param keyword escaped {@code LIKE} pattern over table and display names, or null
     * @param limit page size
     * @param offset zero-based offset
     * @return matching rows in update order
     */
    List<ModelEntity> page(
            String workspaceId,
            String type,
            String layerId,
            String ownerId,
            String domainId,
            String datamartId,
            String keyword,
            int limit,
            int offset);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized Workspace
     * @param type persisted model type filter, or null for every type
     * @param layerId layer filter, or null
     * @param ownerId owner filter, or null
     * @param domainId domain filter, or null
     * @param datamartId data mart filter, or null
     * @param keyword escaped {@code LIKE} pattern, or null
     * @return matching total
     */
    long count(
            String workspaceId,
            String type,
            String layerId,
            String ownerId,
            String domainId,
            String datamartId,
            String keyword);
    /**
     * Reads one page of models hanging below a business process.
     * @param workspaceId authorized Workspace
     * @param processId owning business process
     * @param limit page size
     * @param offset zero-based offset
     * @return matching rows in update order
     */
    List<ModelEntity> pageByProcess(String workspaceId, String processId, int limit, int offset);
    /**
     * Counts the models hanging below a business process.
     * @param workspaceId authorized Workspace
     * @param processId owning business process
     * @return matching total
     */
    long countByProcess(String workspaceId, String processId);
    /**
     * Finds a model with its joined display names.
     * @param workspaceId authorized Workspace
     * @param id model identity
     * @return matching row or empty
     */
    Optional<ModelEntity> find(String workspaceId, String id);
    /**
     * Locks a model row for a revision-guarded write.
     * @param workspaceId authorized Workspace
     * @param id model identity
     * @return locked row without joined names, or empty
     */
    Optional<ModelEntity> lock(String workspaceId, String id);
    /**
     * Checks the Workspace-local table name without reserving it.
     * @param workspaceId authorized Workspace
     * @param name full table name
     * @param excludeId edited identity, or null for creation
     * @return true when another model owns the name
     */
    boolean occupied(String workspaceId, String name, String excludeId);
    /**
     * Inserts one model in the caller transaction.
     * @param row fully initialized row
     */
    void add(ModelEntity row);
    /**
     * Replaces the model row under its revision guard.
     * @param row replacement fields including the expected revision
     * @return true if the guarded row was updated
     */
    boolean update(ModelEntity row);
    /**
     * Deletes a model and cascades its fields.
     * @param workspaceId authorized Workspace
     * @param id model identity
     * @param revision expected version
     * @return true if the guarded row was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Reads the ordered fields of a model with their bound standard codes.
     * @param modelId owning model identity
     * @return fields in display order
     */
    List<ModelFieldEntity> fields(String modelId);
    /**
     * Removes every field of a model before a full replacement.
     * @param modelId owning model identity
     */
    void deleteFields(String modelId);
    /**
     * Inserts one replacement field.
     * @param row fully initialized field row
     */
    void addField(ModelFieldEntity row);
    /**
     * Reads the three attributes a bound standard forces onto its consumers.
     * @param workspaceId authorized Workspace
     * @param id standard identity
     * @return inherited attributes, or null when the standard is absent
     */
    StandardInheritance standard(String workspaceId, String id);
}
