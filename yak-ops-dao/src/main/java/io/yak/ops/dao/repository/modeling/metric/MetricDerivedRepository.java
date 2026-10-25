package io.yak.ops.dao.repository.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import java.util.List;
import java.util.Optional;

/**
 * Persistence of Workspace derived metrics and their ordered modifier links inside the caller's
 * transaction.
 */
public interface MetricDerivedRepository {
    /**
     * Reads one page of derived metrics, optionally restricted to the atomic metric's process.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page with modifier links attached
     */
    List<MetricDerivedEntity> page(String workspaceId, MetricQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, MetricQuery query);
    /**
     * Finds a derived metric with its modifier links without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the metric, or empty when absent in this Workspace
     */
    Optional<MetricDerivedEntity> find(String workspaceId, String id);
    /**
     * Inserts a derived metric and its ordered modifier links in the caller transaction.
     * @param node owned entity with audit fields, expected revision and ordered links
     */
    void insert(MetricDerivedEntity node);
    /**
     * Replaces attributes and links, then advances the revision atomically.
     * @param node owned entity with audit fields, expected revision and ordered links
     * @return true only when the expected revision matched
     */
    boolean update(MetricDerivedEntity node);
    /**
     * Deletes a derived metric at its expected revision; composites and model fields restrict
     * deletion while its links cascade.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching metric was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace composed-code namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param enCode exact composed code
     * @param excludeId edited identity, or null for creation
     * @return true when another derived metric owns the code
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
    /**
     * Reads one derived metric row without its links, for existence checks.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the row, or empty when absent in this Workspace
     */
    Optional<MetricDerivedEntity> row(String workspaceId, String id);
    /**
     * Reads one derived metric row by its composed code, for expression resolution.
     * @param workspaceId authorized current Workspace
     * @param enCode exact composed code
     * @return the row, or empty when absent in this Workspace
     */
    Optional<MetricDerivedEntity> byCode(String workspaceId, String enCode);
}
