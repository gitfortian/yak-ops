package io.yak.ops.dao.entity.modeling.metric;

import lombok.Getter;
import lombok.Setter;

/**
 * One ordered modifier of a derived metric.
 *
 * <p>A link has no revision and no audit columns: the list is replaced as a whole under the owning
 * metric's revision, so an individual link is never edited on its own.
 */
@Getter
@Setter
public class MetricDerivedModifierEntity {
    /** Application-generated identity. */
    private String id;
    /** Workspace owning the parent derived metric. */
    private String workspaceId;
    /** Parent derived metric; links cascade with it. */
    private String derivedId;
    /** Modifier referenced by the composition. */
    private String modifierId;
    /** Joined modifier abbreviation for display; null on write paths. */
    private String modifierEnCode;
    /** Joined modifier name for display; null on write paths. */
    private String modifierCnName;
    /** One-based position assigned from the submitted order. */
    private int ordinal;
}
