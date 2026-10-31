package io.yak.ops.business.datamap.catalog;

import io.yak.ops.common.bean.dto.datamap.DataMapCuratedUpdateDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapTableQueryDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapOverviewFacetsVO;
import io.yak.ops.common.bean.vo.datamap.DataMapOverviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableChangeVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableDetailVO;
import io.yak.ops.common.bean.vo.datamap.DataMapTableSummaryVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/**
 * Reads and curates the Workspace metadata catalog.
 *
 * <p>Browsing is a search over current state; curation replaces the three user-owned fields and
 * never touches crawler-owned metadata.
 */
public interface DataMapCatalogService {

    /**
     * Returns Workspace counts and the ten most viewed assets.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @return overview counters and hot assets
     */
    DataMapOverviewVO overview(String workspaceId, String actor);

    /**
     * Returns per-domain and per-datasource asset counts with their coverage numbers.
     *
     * @param workspaceId current Workspace
     * @return browse facet counts ordered by facet key
     */
    DataMapOverviewFacetsVO overviewFacets(String workspaceId);

    /**
     * Searches and browses assets.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param query keyword, datasource, namespace, domain, tag and presence filters
     * @return one page of matching assets
     */
    PagingData<DataMapTableSummaryVO> search(String workspaceId, String actor, DataMapTableQueryDTO query);

    /**
     * Reads one asset with its columns, tags and the caller's favourite state.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param tableId asset identity
     * @return the asset detail
     */
    DataMapTableDetailVO detail(String workspaceId, String actor, String tableId);

    /**
     * Replaces the business description, accountable owner and data domain.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param tableId asset identity
     * @param input replacement values; null clears the field
     * @return the updated asset detail
     */
    DataMapTableDetailVO curate(String workspaceId, String actor, String tableId, DataMapCuratedUpdateDTO input);

    /**
     * Reads the most recent crawl-detected changes of one asset.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param tableId asset identity
     * @return at most one hundred changes, newest first
     */
    List<DataMapTableChangeVO> changes(String workspaceId, String actor, String tableId);
}
