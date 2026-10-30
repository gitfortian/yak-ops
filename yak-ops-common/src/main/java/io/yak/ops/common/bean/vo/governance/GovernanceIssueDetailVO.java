package io.yak.ops.common.bean.vo.governance;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One Issue with its evidence, the rule that produced it and its transition history. */
@Getter
@Setter
public class GovernanceIssueDetailVO {

    private GovernanceIssueVO issue;

    /** Definition of the rule, read from the in-memory registry rather than a table. */
    private GovernanceRuleVO rule;

    /** Newest first; the detail surface shows the first page only. */
    private List<GovernanceIssueEventVO> events;
}
