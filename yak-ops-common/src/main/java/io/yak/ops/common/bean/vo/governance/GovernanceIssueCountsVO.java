package io.yak.ops.common.bean.vo.governance;

import lombok.Getter;
import lombok.Setter;

/** Live Issue counts of a Workspace, by status and by score-bearing severity. */
@Getter
@Setter
public class GovernanceIssueCountsVO {

    private Integer total;

    private Integer open;

    private Integer handling;

    private Integer ignored;

    private Integer resolved;

    /** Severity counts of the OPEN and HANDLING Issues only. */
    private Integer high;

    private Integer medium;

    private Integer low;
}
