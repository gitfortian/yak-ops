package io.yak.ops.dao.entity.modeling.metric;

import io.yak.ops.dao.entity.BaseEntity;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted Workspace-owned composite metric.
 *
 * <p>The expression is design-time data: it is parsed and its references resolved on save, but
 * nothing evaluates it and it produces no data type. References point at derived metrics only, so
 * the graph stays a two-level DAG by construction.
 */
@Getter
@Setter
public class MetricCompositeEntity extends BaseEntity {
    /** Workspace owning this metric. */
    private String workspaceId;
    /** Workspace-unique code inside composite metrics. */
    private String enCode;
    /** Display name. */
    private String cnName;
    /** Arithmetic expression over derived-metric codes, stored as submitted. */
    private String expression;
    /** Accountable owner; an active member of the Workspace. */
    private String ownerId;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
    /** References resolved from the expression for display; null on write paths. */
    private List<MetricCompositeRefEntity> references;
}
