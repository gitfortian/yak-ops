package io.yak.ops.common.bean.vo.governance;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Outcome of one rule inside a scan run. */
@Getter
@Setter
public class GovernanceRuleResultVO {

    private String ruleCode;

    private Integer ruleVersion;

    /** RUNNING, SUCCEEDED or FAILED. */
    private String status;

    private Integer findingCount;

    private Integer issueCreatedCount;

    private Integer issueReopenedCount;

    private Integer issueResolvedCount;

    /** Safe machine code; never an exception message. */
    private String reasonCode;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;
}
