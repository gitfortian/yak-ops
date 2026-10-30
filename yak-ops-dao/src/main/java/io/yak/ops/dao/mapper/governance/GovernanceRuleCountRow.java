package io.yak.ops.dao.mapper.governance;

import lombok.Getter;
import lombok.Setter;

/** Raw count of score-bearing Issues of one rule. */
@Getter
@Setter
public class GovernanceRuleCountRow {
    /** Stable rule code. */
    private String ruleCode;
    /** Number of OPEN and HANDLING Issues carrying that code. */
    private Integer total;
}
