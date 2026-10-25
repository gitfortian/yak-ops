package io.yak.ops.dao.entity.modeling.metric;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned time period without payload diagnostics. */
@Getter
@Setter
public class MetricTimePeriodEntity extends BaseEntity {
    /** Workspace owning this period. */
    private String workspaceId;
    /** Workspace-unique abbreviation composed into derived-metric codes. */
    private String enCode;
    /** Chinese name composed into derived-metric names. */
    private String cnName;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
