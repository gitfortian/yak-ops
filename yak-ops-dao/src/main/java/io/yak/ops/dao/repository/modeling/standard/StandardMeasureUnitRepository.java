package io.yak.ops.dao.repository.modeling.standard;

import io.yak.ops.dao.entity.modeling.standard.StandardMeasureUnitEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace measurement units inside the caller's transaction. */
public interface StandardMeasureUnitRepository {
    /**
     * Reads one page of units, optionally restricted to a category.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page
     */
    List<StandardMeasureUnitEntity> page(String workspaceId, StandardQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, StandardQuery query);
    /**
     * Finds a unit without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the unit, or empty when absent in this Workspace
     */
    Optional<StandardMeasureUnitEntity> find(String workspaceId, String id);
    /**
     * Inserts a unit; Workspace abbreviation conflicts propagate as DuplicateKeyException.
     * @param node owned entity with audit fields and expected revision
     */
    void insert(StandardMeasureUnitEntity node);
    /**
     * Replaces attributes and advances the revision atomically.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean update(StandardMeasureUnitEntity node);
    /**
     * Deletes a unit at its expected revision; inbound references restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching unit was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace abbreviation namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param enCode exact validated abbreviation
     * @param excludeId edited identity, or null for creation
     * @return true when another unit owns the abbreviation
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
}
