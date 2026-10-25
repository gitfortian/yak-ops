package io.yak.ops.dao.repository.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricModifierEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace modifiers inside the caller's transaction. */
public interface MetricModifierRepository {
    /**
     * Reads one page of modifiers, optionally restricted to a data domain.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page
     */
    List<MetricModifierEntity> page(String workspaceId, MetricQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, MetricQuery query);
    /**
     * Finds a modifier without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the modifier, or empty when absent in this Workspace
     */
    Optional<MetricModifierEntity> find(String workspaceId, String id);
    /**
     * Inserts a modifier; Workspace abbreviation conflicts propagate as DuplicateKeyException.
     * @param node owned entity with audit fields and expected revision
     */
    void insert(MetricModifierEntity node);
    /**
     * Replaces attributes and advances the revision atomically.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean update(MetricModifierEntity node);
    /**
     * Deletes a modifier at its expected revision; inbound references restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching modifier was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace abbreviation namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param enCode exact validated abbreviation
     * @param excludeId edited identity, or null for creation
     * @return true when another modifier owns the abbreviation
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
}
