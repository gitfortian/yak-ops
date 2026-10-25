package io.yak.ops.dao.repository.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace atomic metrics inside the caller's transaction. */
public interface MetricAtomicRepository {
    /**
     * Reads one page of atomic metrics, optionally restricted to a business process.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page
     */
    List<MetricAtomicEntity> page(String workspaceId, MetricQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, MetricQuery query);
    /**
     * Finds an atomic metric without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the metric, or empty when absent in this Workspace
     */
    Optional<MetricAtomicEntity> find(String workspaceId, String id);
    /**
     * Inserts an atomic metric; Workspace code conflicts propagate as DuplicateKeyException.
     * @param node owned entity with audit fields and expected revision
     */
    void insert(MetricAtomicEntity node);
    /**
     * Replaces attributes and advances the revision atomically.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean update(MetricAtomicEntity node);
    /**
     * Deletes an atomic metric at its expected revision; derived metrics restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching metric was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace code namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param enCode exact validated code
     * @param excludeId edited identity, or null for creation
     * @return true when another atomic metric owns the code
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
}
