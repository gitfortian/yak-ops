package io.yak.ops.business.governance.service;

import io.yak.ops.common.bean.dto.governance.GovernanceIssueBatchDTO;
import io.yak.ops.common.bean.dto.governance.GovernanceIssueQueryDTO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueDetailVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueEventVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueVO;
import io.yak.ops.common.page.PageData;

/**
 * Read surface and manual state machine of governance Issues.
 *
 * <p>Every command validates the state it locked and writes an append-only event. A batch is one
 * transaction: a single invalid row rejects the whole command, so a partial outcome never reaches
 * the caller.
 */
public interface GovernanceIssueService {

    PageData<GovernanceIssueVO> page(String workspaceId, GovernanceIssueQueryDTO query);

    GovernanceIssueDetailVO detail(String workspaceId, String issueId);

    PageData<GovernanceIssueEventVO> events(String workspaceId, String issueId, int pageNo, int pageSize);

    /** {@code OPEN} or {@code HANDLING} to {@code HANDLING}; the assignee must be an active member. */
    int assign(String workspaceId, String actor, GovernanceIssueBatchDTO input);

    /** Any non-resolved state to {@code RESOLVED} with a manual resolution. */
    int resolve(String workspaceId, String actor, GovernanceIssueBatchDTO input);

    /** {@code RESOLVED} back to {@code OPEN}. */
    int reopen(String workspaceId, String actor, GovernanceIssueBatchDTO input);

    /** To {@code IGNORED} until a required deadline within ninety days. */
    int ignore(String workspaceId, String actor, GovernanceIssueBatchDTO input);

    /** {@code IGNORED} back to {@code OPEN}. */
    int unignore(String workspaceId, String actor, GovernanceIssueBatchDTO input);
}
