package io.yak.ops.dao.repository.modeling.metric;

import io.yak.ops.dao.entity.modeling.metric.MetricTimePeriodEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace time periods inside the caller's transaction. */
public interface MetricTimePeriodRepository {
    /**
     * Reads one page of periods.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page
     */
    List<MetricTimePeriodEntity> page(String workspaceId, MetricQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, MetricQuery query);
    /**
     * Finds a period without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the period, or empty when absent in this Workspace
     */
    Optional<MetricTimePeriodEntity> find(String workspaceId, String id);
    /**
     * Inserts a period; Workspace abbreviation conflicts propagate as DuplicateKeyException.
     * @param node owned entity with audit fields and expected revision
     */
    void insert(MetricTimePeriodEntity node);
    /**
     * Replaces attributes and advances the revision atomically.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean update(MetricTimePeriodEntity node);
    /**
     * Deletes a period at its expected revision; inbound references restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching period was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace abbreviation namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param enCode exact validated abbreviation
     * @param excludeId edited identity, or null for creation
     * @return true when another period owns the abbreviation
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
}
