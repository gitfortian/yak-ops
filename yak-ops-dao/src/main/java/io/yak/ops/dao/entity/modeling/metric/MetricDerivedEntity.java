package io.yak.ops.dao.entity.modeling.metric;

import io.yak.ops.dao.entity.BaseEntity;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted Workspace-owned derived metric.
 *
 * <p>The composed code and name are stored columns, not read-time derivations: uniqueness is
 * enforced on stored values and model fields cite what was saved. The parts remain the source of
 * truth for meaning; an edit recomposes both stored values under the same checks.
 */
@Getter
@Setter
public class MetricDerivedEntity extends BaseEntity {
    /** Workspace owning this metric. */
    private String workspaceId;
    /** Composed code: period, then modifier abbreviations in order, then atomic code. */
    private String enCode;
    /** Composed name: period, then modifier names, then atomic name concatenated. */
    private String cnName;
    /** Atomic metric of the same Workspace carrying the caliber. */
    private String atomicMetricId;
    /** Joined atomic code for display; null on write paths. */
    private String atomicEnCode;
    /** Joined atomic name for display; null on write paths. */
    private String atomicCnName;
    /** Time period of the same Workspace fixing the window. */
    private String periodId;
    /** Joined period abbreviation for display; null on write paths. */
    private String periodEnCode;
    /** Joined period name for display; null on write paths. */
    private String periodCnName;
    /** Accountable owner; an active member of the Workspace. */
    private String ownerId;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
    /** Ordered modifier parts for display; null on write paths. */
    private List<MetricDerivedModifierEntity> modifiers;
}
