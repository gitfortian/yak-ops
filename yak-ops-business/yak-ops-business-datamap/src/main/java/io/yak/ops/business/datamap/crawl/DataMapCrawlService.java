package io.yak.ops.business.datamap.crawl;

import io.yak.ops.common.bean.dto.datamap.DataMapCrawlSubmitDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlSummaryVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.enums.datamap.DataMapCrawlStatus;
import io.yak.ops.common.enums.datamap.DataMapCrawlTriggerType;
import io.yak.ops.common.page.PagingData;

/** Submits, lists and reads catalog crawl runs. */
public interface DataMapCrawlService {

    /**
     * Lists runs of the Workspace, newest first.
     *
     * @param workspaceId current Workspace
     * @param datasourceId optional datasource filter
     * @param taskId optional task filter
     * @param status optional run status filter
     * @param triggerType optional trigger filter, MANUAL or SCHEDULED
     * @param failed true for runs with failed tables, false for runs without, null for both
     * @param days when given, only runs submitted within this many days are returned
     * @param pageNo one-based page number
     * @param pageSize page size
     * @return one page of runs
     */
    PagingData<DataMapCrawlVO> list(
            String workspaceId,
            String datasourceId,
            String taskId,
            DataMapCrawlStatus status,
            DataMapCrawlTriggerType triggerType,
            Boolean failed,
            Integer days,
            int pageNo,
            int pageSize);

    /**
     * Reads the Workspace-wide collection health shown above the task table.
     *
     * <p>Counts cover the whole Workspace rather than the visible page, so the band cannot disagree
     * with the list. Run counts are limited to a rolling window because an all-time distribution
     * says nothing about whether collection is healthy now.
     *
     * @param workspaceId current Workspace
     * @param hours length of the run window in hours, or null for the default
     * @return task totals, run outcome distribution, failure backlog and comment coverage
     */
    DataMapCrawlSummaryVO summary(String workspaceId, Integer hours);

    /**
     * Records a pending run and hands it to the crawl executor.
     *
     * <p>Submission returns immediately. A second run for a datasource that already holds one is
     * refused by the active-scope unique key, not by a pre-check.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated submitter
     * @param input datasource and namespace to crawl
     * @return the pending run
     */
    DataMapCrawlVO submit(String workspaceId, String actor, DataMapCrawlSubmitDTO input);

    /**
     * Records and dispatches one run of a stored task.
     *
     * <p>The run snapshots the task's exclusions, so a later task edit cannot change what this run
     * skips. A second active run for the datasource is refused by the active-scope unique key. A
     * retry names an earlier run of the same task and re-enumerates only its failed tables.
     *
     * @param workspaceId current Workspace
     * @param actor submitter, or the scheduler's system identity
     * @param task task configuration snapshot
     * @param triggerType MANUAL or SCHEDULED
     * @param retryOf earlier run of this task whose failed tables are retried, or null for a full run
     * @return the pending run
     */
    DataMapCrawlVO submitTaskRun(
            String workspaceId,
            String actor,
            DataMapCrawlTaskSpec task,
            DataMapCrawlTriggerType triggerType,
            String retryOf);

    /**
     * Reads one run with up to two hundred per-table failures.
     *
     * @param workspaceId current Workspace
     * @param crawlId run identity
     * @return the run and its failures
     */
    DataMapCrawlDetailVO detail(String workspaceId, String crawlId);
}
