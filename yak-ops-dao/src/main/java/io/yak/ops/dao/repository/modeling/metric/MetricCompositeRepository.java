package io.yak.ops.dao.repository.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricCompositeEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace composite metrics and their resolved references. */
public interface MetricCompositeRepository {
    /**
     * Reads one page of composite metrics with their references attached.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page
     */
    List<MetricCompositeEntity> page(String workspaceId, MetricQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, MetricQuery query);
    /**
     * Finds a composite metric with its references without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the metric, or empty when absent in this Workspace
     */
    Optional<MetricCompositeEntity> find(String workspaceId, String id);
    /**
     * Inserts a composite metric and its resolved references in the caller transaction.
     * @param node owned entity with audit fields, expected revision and resolved references
     */
    void insert(MetricCompositeEntity node);
    /**
     * Replaces attributes and references, then advances the revision atomically.
     * @param node owned entity with audit fields, expected revision and resolved references
     * @return true only when the expected revision matched
     */
    boolean update(MetricCompositeEntity node);
    /**
     * Deletes a composite metric at its expected revision; its references cascade.
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
     * @return true when another composite metric owns the code
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
}
