package io.yak.ops.dao.entity.modeling.metric;

import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned atomic metric carrying one statistical caliber. */
@Getter
@Setter
public class MetricAtomicEntity extends BaseEntity {
    /** Workspace owning this metric. */
    private String workspaceId;
    /** Business process of the same Workspace that produces the measure. */
    private String processId;
    /** Joined process name for display; null on write paths. */
    private String processName;
    /** Workspace-unique code inside atomic metrics. */
    private String enCode;
    /** Statistical caliber display name. */
    private String cnName;
    /** Logical type the measure returns. */
    private DataType dataType;
    /** Aggregate that is part of the caliber; must match {@link #dataType}. */
    private AggregateType aggregateType;
    /** Optional bound measurement unit annotation; never changes the type. */
    private String measureUnitId;
    /** Joined unit abbreviation for display; null on write paths or when unbound. */
    private String measureUnitEnCode;
    /** Accountable owner; an active member of the Workspace. */
    private String ownerId;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
