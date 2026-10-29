package io.yak.ops.common.bean.vo.governance;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** One scan run with its terminal counters and safe reason code. */
@Getter
@Setter
public class GovernanceScanVO {

    private String id;

    /** MANUAL or SCHEDULED. */
    private String triggerType;

    /** PENDING, RUNNING, SUCCEEDED, PARTIAL, FAILED or SKIPPED. */
    private String status;

    private Integer ruleCount;

    private Integer succeededRuleCount;

    private Integer failedRuleCount;

    private Integer issueCreatedCount;

    private Integer issueReopenedCount;

    private Integer issueResolvedCount;

    /** Safe machine code such as DATA_MAP_BUSY or APP_RESTART. */
    private String reasonCode;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    private String createBy;

    /** Wall-clock milliseconds between claim and terminal state; null while in flight. */
    private Long durationMillis;
}
