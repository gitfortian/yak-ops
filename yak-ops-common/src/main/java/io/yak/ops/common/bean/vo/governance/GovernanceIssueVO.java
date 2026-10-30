package io.yak.ops.common.bean.vo.governance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One governance Issue.
 *
 * <p>Carries a stable rule code and numeric evidence only; the wording belongs to the caller.
 */
@Getter
@Setter
public class GovernanceIssueVO {

    private String id;

    private String ruleCode;

    private Integer ruleVersion;

    /** DEV or COMPUTE. */
    private String dimension;

    private String severity;

    /** DATA_MAP_TABLE or TASK_DEFINITION. */
    private String targetType;

    private String targetId;

    private String targetName;

    private String status;

    private String ownerId;

    private LocalDateTime ignoreUntil;

    private LocalDateTime resolvedAt;

    private String resolvedBy;

    /** USER or AUTO. */
    private String resolution;

    private BigDecimal observedValue;

    private BigDecimal thresholdValue;

    private Integer sampleCount;

    private Integer windowDays;

    private LocalDateTime firstDetectedAt;

    private LocalDateTime lastDetectedAt;

    private String lastScanId;

    private Long revision;
}
