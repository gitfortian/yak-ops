package io.yak.ops.common.bean.vo.governance;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One scan run together with the outcome of every rule it evaluated. */
@Getter
@Setter
public class GovernanceScanDetailVO {

    private GovernanceScanVO scan;

    private List<GovernanceRuleResultVO> rules;
}
