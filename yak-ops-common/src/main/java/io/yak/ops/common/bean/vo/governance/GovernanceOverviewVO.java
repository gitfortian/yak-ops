package io.yak.ops.common.bean.vo.governance;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Latest completed score with its coverage, the live Issue counts and the last scan. */
@Getter
@Setter
public class GovernanceOverviewVO {

    /** DEV and COMPUTE, in that order; a N/A dimension carries a null score. */
    private List<GovernanceDimensionScoreVO> dimensions;

    /** OVERALL row; null when fewer than two dimensions are evaluated. */
    private GovernanceDimensionScoreVO overall;

    /** How many of the two dimensions are evaluated: 0, 1 or 2. */
    private Integer evaluatedDimensionCount;

    /** Day the returned score describes; null before the first complete scan. */
    private String snapshotDate;

    /** Score formula the snapshot was written with. */
    private String formulaVersion;

    private GovernanceIssueCountsVO issueCounts;

    /** Score-bearing Issues per rule, for the rule distribution on the overview. */
    private List<GovernanceRuleCountVO> ruleCounts;

    /** Most recent run whatever its outcome; null before the first scan. */
    private GovernanceScanVO lastScan;
}
