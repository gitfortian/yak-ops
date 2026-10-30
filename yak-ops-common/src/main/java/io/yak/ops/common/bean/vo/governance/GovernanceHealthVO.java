package io.yak.ops.common.bean.vo.governance;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Daily dimension and overall scores of one closed range. */
@Getter
@Setter
public class GovernanceHealthVO {

    private String from;

    private String to;

    private List<GovernanceHealthPointVO> points;
}
