package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.modeling.ModelingRelationColumnEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace model relations and their ordered key pairs. */
public interface ModelingRelationRepository {
    /**
     * Reads one relation with joined model display names.
     * @param workspaceId authorized Workspace
     * @param id relation identity
     * @return the relation, or empty
     */
    Optional<ModelingRelationEntity> find(String workspaceId, String id);

    /**
     * Locks one relation row for update inside the owning transaction.
     * @param workspaceId authorized Workspace
     * @param id relation identity
     * @return the locked relation, or empty
     */
    Optional<ModelingRelationEntity> lock(String workspaceId, String id);

    /**
     * Counts relations using the same name.
     * @param workspaceId authorized Workspace
     * @param name candidate name
     * @param excludeId relation excluded from the count, or null
     * @return matching count
     */
    long countName(String workspaceId, String name, String excludeId);

    /**
     * Lists relations where the model is either side.
     * @param workspaceId authorized Workspace
     * @param modelId model identity
     * @return relations in update order
     */
    List<ModelingRelationEntity> listByModel(String workspaceId, String modelId);

    /**
     * Counts relations touching the model.
     * @param workspaceId authorized Workspace
     * @param modelId model identity
     * @return matching count
     */
    long countByModel(String workspaceId, String modelId);

    /**
     * Inserts one relation.
     * @param row relation to insert
     */
    void add(ModelingRelationEntity row);

    /**
     * Updates one relation guarded by its revision.
     * @param row relation carrying the expected revision
     * @return true when the row was updated
     */
    boolean update(ModelingRelationEntity row);

    /**
     * Deletes one relation guarded by its revision; key pairs cascade in the database.
     * @param workspaceId authorized Workspace
     * @param id relation identity
     * @param revision expected revision
     * @return true when the row was deleted
     */
    boolean delete(String workspaceId, String id, long revision);

    /**
     * Reads the ordered key pairs of one relation.
     * @param relationId owning relation
     * @return pairs in ordinal order
     */
    List<ModelingRelationColumnEntity> columns(String relationId);

    /**
     * Deletes every key pair of one relation.
     * @param relationId owning relation
     */
    void deleteColumns(String relationId);

    /**
     * Inserts one key pair.
     * @param row key pair to insert
     */
    void addColumn(ModelingRelationColumnEntity row);

    /**
     * Lists models accused by the key-standardization scan, once per offending relation.
     * @param workspaceId authorized Workspace
     * @return accused models with the relation that carries the offending key
     */
    List<RelationKeyScanRow> scanUnstandardizedKeys(String workspaceId);
}
