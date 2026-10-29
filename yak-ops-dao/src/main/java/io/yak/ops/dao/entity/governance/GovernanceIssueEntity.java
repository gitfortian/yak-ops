package io.yak.ops.dao.entity.governance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists the single current governance Issue of one (rule, target) pair. */
@Getter
@Setter
public class GovernanceIssueEntity {
    /** Application-generated identity, stable across reopenings. */
    private String id;
    /** Workspace owning the Issue. */
    private String workspaceId;
    /** Rule that detected this target. */
    private String ruleCode;
    /** Rule version of the latest detection. */
    private Integer ruleVersion;
    /** DEV or COMPUTE. */
    private String dimensionCode;
    /** HIGH, MEDIUM or LOW; fixed by the rule. */
    private String severity;
    /** DATA_MAP_TABLE or TASK_DEFINITION. */
    private String targetType;
    /** Identity of the governed object. */
    private String targetId;
    /** Display name captured at detection. */
    private String targetName;
    /** OPEN, HANDLING, RESOLVED or IGNORED. */
    private String status;
    /** Assigned Workspace member while in HANDLING. */
    private String ownerId;
    /** Mandatory while IGNORED. */
    private LocalDateTime ignoreUntil;
    /** Terminal time of a resolution. */
    private LocalDateTime resolvedAt;
    /** Member who resolved the Issue. */
    private String resolvedBy;
    /** USER or AUTO. */
    private String resolution;
    /** Numeric evidence; the frontend owns the wording. */
    private BigDecimal observedValue;
    /** Threshold the observation was compared with. */
    private BigDecimal thresholdValue;
    /** Samples the observation was computed from. */
    private Integer sampleCount;
    /** Days the observation looked back over; 0 for an instantaneous fact. */
    private Integer windowDays;
    /** When this target first entered governance. */
    private LocalDateTime firstDetectedAt;
    /** When the condition was last observed. */
    private LocalDateTime lastDetectedAt;
    /** Run that last observed this target. */
    private String lastScanId;
    /** Optimistic guard for manual transitions. */
    private Long revision;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}
