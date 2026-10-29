package io.yak.ops.common.bean.vo.governance;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** One daily score row; absent days are gaps rather than zeros. */
@Getter
@Setter
public class GovernanceHealthPointVO {

    /** UTC day in ISO form. */
    private String snapshotDate;

    private String dimensionCode;

    private BigDecimal score;

    private Integer baseCount;

    private BigDecimal weightedIssueCount;

    private Integer issueCount;

    private Integer highCount;

    private Integer mediumCount;

    private Integer lowCount;
}
