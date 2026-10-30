package io.yak.ops.dao.entity.governance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one daily score row per dimension, or the overall row when both dimensions exist. */
@Getter
@Setter
public class GovernanceHealthSnapshotEntity {
    /** Application-generated snapshot identity. */
    private String id;
    /** Workspace the score belongs to. */
    private String workspaceId;
    /** DEV, COMPUTE or OVERALL. */
    private String dimensionCode;
    /** UTC day the score describes. */
    private LocalDate snapshotDate;
    /** Score after the documented curve and clamp. */
    private BigDecimal score;
    /** Eligible assets the rate was computed over. */
    private Integer baseCount;
    /** Sum of OPEN and HANDLING issue weights. */
    private BigDecimal weightedIssueCount;
    /** Current Issues of this dimension. */
    private Integer issueCount;

    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    /** 1 for a dimension row, 2 for OVERALL. */
    private Integer evaluatedDimensionCount;
    /** Score formula the row was written with. */
    private String formulaVersion;
    /** Run that produced the snapshot. */
    private String scanId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
