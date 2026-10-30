package io.yak.ops.business.governance.service;

import io.yak.ops.common.bean.vo.governance.GovernanceHealthVO;
import io.yak.ops.common.bean.vo.governance.GovernanceOverviewVO;
import io.yak.ops.common.bean.vo.governance.GovernanceRuleVO;
import java.time.LocalDate;
import java.util.List;

/** Score, trend and rule definition queries of one Workspace. */
public interface GovernanceHealthService {

    /** Latest completed score with live Issue counts and the last run. */
    GovernanceOverviewVO overview(String workspaceId);

    /**
     * Daily scores from {@code from} to {@code to}.
     *
     * @throws io.yak.ops.common.exception.BusinessException {@code INVALID_DATE_RANGE} when the range
     *     is reversed or longer than ninety days
     */
    GovernanceHealthVO health(String workspaceId, LocalDate from, LocalDate to);

    /** The compiled rules, read from the in-memory registry rather than a table. */
    List<GovernanceRuleVO> rules();
}
