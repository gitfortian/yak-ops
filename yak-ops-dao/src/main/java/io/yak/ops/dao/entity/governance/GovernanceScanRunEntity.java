package io.yak.ops.dao.entity.governance;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one governance scan run and its terminal counters. */
@Getter
@Setter
public class GovernanceScanRunEntity {
    /** Application-generated run identity. */
    private String id;
    /** Workspace owning the run. */
    private String workspaceId;
    /** MANUAL or SCHEDULED. */
    private String triggerType;
    /** PENDING, RUNNING, SUCCEEDED, PARTIAL, FAILED or SKIPPED. */
    private String status;
    /** 1 while pending or running; null after any terminal state. */
    private Integer activeScope;
    /** Rules the run intended to evaluate. */
    private Integer ruleCount;
    /** Rules that completed a full enumeration. */
    private Integer succeededRuleCount;
    /** Rules that threw, timed out or exceeded the budget. */
    private Integer failedRuleCount;
    /** Issues first created by the run. */
    private Integer issueCreatedCount;
    /** Issues reopened by the run. */
    private Integer issueReopenedCount;
    /** Issues resolved or auto-resolved by the run. */
    private Integer issueResolvedCount;
    /** Safe machine code such as DATA_MAP_BUSY or APP_RESTART. */
    private String reasonCode;
    /** When the worker claimed the run. */
    private LocalDateTime startTime;
    /** Terminal time of the run. */
    private LocalDateTime finishTime;
    /** Submission timestamp. */
    private LocalDateTime createTime;
    /** Authenticated submitter, or system for the schedule. */
    private String createBy;
}
