package io.yak.ops.business.governance.service;

import io.yak.ops.common.bean.vo.governance.GovernanceScanDetailVO;
import io.yak.ops.common.bean.vo.governance.GovernanceScanVO;
import io.yak.ops.common.page.PageData;

/** Submission and read surface of governance scan runs. */
public interface GovernanceScanService {

    /**
     * Requests a manual run.
     *
     * @throws io.yak.ops.common.exception.BusinessException {@code SCAN_BUSY} when the Workspace
     *     already runs a scan or the executor queue is saturated
     */
    GovernanceScanVO submit(String workspaceId, String actor);

    /** Requests one scheduled run per Workspace, skipping a Workspace that cannot accept it. */
    void submitScheduledForAllWorkspaces();

    /** Newest run first. */
    PageData<GovernanceScanVO> page(String workspaceId, int pageNo, int pageSize);

    /** One run with the outcome of every rule; unknown identities raise {@code RESOURCE_NOT_EXISTS}. */
    GovernanceScanDetailVO detail(String workspaceId, String scanId);

    /** Most recent run whatever its outcome; null before the first scan. */
    GovernanceScanVO latest(String workspaceId);
}
