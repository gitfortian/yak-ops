package io.yak.ops.dao.entity.governance;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists the outcome of one rule inside a scan run. */
@Getter
@Setter
public class GovernanceRuleResultEntity {
    /** Application-generated result identity. */
    private String id;
    /** Workspace owning the run. */
    private String workspaceId;
    /** Run this result belongs to. */
    private String scanId;
    /** Stable rule code compiled into the product. */
    private String ruleCode;
    /** Rule version the run evaluated. */
    private Integer ruleVersion;
    /** RUNNING, SUCCEEDED or FAILED. */
    private String status;
    /** Candidates the rule enumerated. */
    private Integer findingCount;
    /** Issues first created by this rule in this run. */
    private Integer issueCreatedCount;
    /** Issues reopened by this rule in this run. */
    private Integer issueReopenedCount;
    /** Issues resolved by this rule in this run. */
    private Integer issueResolvedCount;
    /** Safe machine code; never an exception message. */
    private String reasonCode;
    /** When the rule started. */
    private LocalDateTime startTime;
    /** When the rule reached its outcome. */
    private LocalDateTime finishTime;
}
