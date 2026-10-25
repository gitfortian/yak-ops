package io.yak.ops.dao.entity.modeling.metric;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned modifier anchored to one data domain. */
@Getter
@Setter
public class MetricModifierEntity extends BaseEntity {
    /** Workspace owning this modifier. */
    private String workspaceId;
    /** Data domain of the same Workspace that anchors the modifier. */
    private String domainId;
    /** Joined domain name for display; null on write paths. */
    private String domainName;
    /** Workspace-unique abbreviation composed into derived-metric codes. */
    private String enCode;
    /** Chinese name composed into derived-metric names. */
    private String cnName;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
