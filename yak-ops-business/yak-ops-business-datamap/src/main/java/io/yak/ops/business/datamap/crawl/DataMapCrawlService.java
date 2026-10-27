package io.yak.ops.business.datamap.crawl;

import io.yak.ops.common.bean.dto.datamap.DataMapCrawlSubmitDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.page.PagingData;

/** Submits, lists and reads catalog crawl runs. */
public interface DataMapCrawlService {

    /**
     * Lists runs of the Workspace, newest first.
     *
     * @param workspaceId current Workspace
     * @param datasourceId optional datasource filter
     * @param pageNo one-based page number
     * @param pageSize page size
     * @return one page of runs
     */
    PagingData<DataMapCrawlVO> list(String workspaceId, String datasourceId, int pageNo, int pageSize);

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
     * Reads one run with up to two hundred per-table failures.
     *
     * @param workspaceId current Workspace
     * @param crawlId run identity
     * @return the run and its failures
     */
    DataMapCrawlDetailVO detail(String workspaceId, String crawlId);
}
