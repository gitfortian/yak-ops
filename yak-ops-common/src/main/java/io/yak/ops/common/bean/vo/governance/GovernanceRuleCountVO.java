package io.yak.ops.common.bean.vo.governance;

import lombok.Getter;
import lombok.Setter;

/** Number of score-bearing Issues one rule currently contributes. */
@Getter
@Setter
public class GovernanceRuleCountVO {

    private String ruleCode;

    private Integer count;
}
