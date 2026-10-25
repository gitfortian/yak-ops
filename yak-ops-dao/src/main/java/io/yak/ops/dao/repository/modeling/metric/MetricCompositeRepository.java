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
    /**
     * Reads the composite metrics whose stored expression cites a derived metric.
     *
     * <p>Identity, expression text and revision are the only populated attributes; the caller
     * rewrites the cited code in place after a part edit changed the derived metric code, and
     * needs the revision to avoid overwriting a concurrent edit of the same expression.
     * @param workspaceId authorized current Workspace
     * @param derivedId edited derived metric identity
     * @return citing composites, each with its stored expression and revision
     */
    List<MetricCompositeEntity> citingDerived(String workspaceId, String derivedId);
    /**
     * Replaces one stored expression, advancing the revision, without touching the references.
     *
     * <p>Used by the composition propagation: the resolved reference rows already point at the
     * renamed derived metric, so only the recorded text has to follow. The revision is part of the
     * predicate because the expression is authored text, not a derived audit field: a user who
     * saved the same composite in the meantime must not be overwritten silently.
     * @param workspaceId authorized current Workspace
     * @param id target composite metric identity
     * @param revision revision the caller read
     * @param expression committed expression text
     * @param actor identity recorded on the rewritten row
     * @return true only when the row is still at that revision in this Workspace
     */
    boolean rewriteExpression(String workspaceId, String id, long revision, String expression, String actor);
}
