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
    /**
     * Reads every derived metric built on one atomic metric, for composition propagation.
     * @param workspaceId authorized current Workspace
     * @param atomicMetricId edited atomic metric
     * @return affected derived metrics without their links
     */
    List<MetricDerivedEntity> byAtomic(String workspaceId, String atomicMetricId);
    /**
     * Reads every derived metric fixed to one time period, for composition propagation.
     * @param workspaceId authorized current Workspace
     * @param periodId edited time period
     * @return affected derived metrics without their links
     */
    List<MetricDerivedEntity> byPeriod(String workspaceId, String periodId);
    /**
     * Reads every derived metric whose composition uses one modifier, for composition propagation.
     * @param workspaceId authorized current Workspace
     * @param modifierId edited modifier
     * @return affected derived metrics without their links
     */
    List<MetricDerivedEntity> byModifier(String workspaceId, String modifierId);
    /**
     * Rewrites the composed code and name of one derived metric without touching its links.
     * @param node owned entity with the new values, expected revision and audit fields
     * @return true only when the expected revision matched
     */
    boolean rename(MetricDerivedEntity node);
}
