package io.yak.ops.dao.entity.modeling.metric;

import lombok.Getter;
import lombok.Setter;

/**
 * One derived metric a composite expression references.
 *
 * <p>A link has no revision and no audit columns: the list is replaced as a whole under the owning
 * metric's revision, so an individual link is never edited on its own.
 */
@Getter
@Setter
public class MetricCompositeRefEntity {
    /** Application-generated identity. */
    private String id;
    /** Workspace owning the parent composite metric. */
    private String workspaceId;
    /** Parent composite metric; links cascade with it. */
    private String compositeId;
    /** Derived metric referenced by the expression. */
    private String derivedMetricId;
    /** Joined referenced code for display; null on write paths. */
    private String derivedEnCode;
    /** Joined referenced name for display; null on write paths. */
    private String derivedCnName;
}
