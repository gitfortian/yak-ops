package io.yak.ops.common.bean.vo.governance;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** One scored dimension of the Workspace health snapshot. */
@Getter
@Setter
public class GovernanceDimensionScoreVO {

    /** DEV, COMPUTE or OVERALL. */
    private String dimensionCode;

    /** Score after the documented curve; null when the dimension is N/A. */
    private BigDecimal score;

    /** Eligible assets the rate was computed over. */
    private Integer baseCount;

    /** Sum of the weights of the current OPEN and HANDLING Issues. */
    private BigDecimal weightedIssueCount;

    /** Current Issues counted in this dimension. */
    private Integer issueCount;

    private Integer highCount;

    private Integer mediumCount;

    private Integer lowCount;

    /** Whether the dimension has an eligible base and therefore a score. */
    private Boolean evaluated;
}
