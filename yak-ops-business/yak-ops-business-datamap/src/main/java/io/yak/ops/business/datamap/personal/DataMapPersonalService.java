package io.yak.ops.business.datamap.personal;

import io.yak.ops.common.bean.vo.datamap.DataMapTableSummaryVO;
import io.yak.ops.common.page.PagingData;

/**
 * Owns the per-member surfaces of the data map: favourites and recorded visits.
 *
 * <p>A visit is an explicit call, never a side effect of reading the detail payload, so the hot
 * list measures interest rather than navigation.
 */
public interface DataMapPersonalService {

    /**
     * Returns the caller's favourite assets, newest first.
     *
     * @param workspaceId current Workspace
     * @param userId authenticated caller
     * @param pageNo one-based page number
     * @param pageSize page size
     * @return one page of favourite assets
     */
    PagingData<DataMapTableSummaryVO> favorites(String workspaceId, String userId, int pageNo, int pageSize);

    /**
     * Returns the caller's assets ordered by most recent recorded visit.
     *
     * @param workspaceId current Workspace
     * @param userId authenticated caller
     * @param pageNo one-based page number
     * @param pageSize page size
     * @return one page of recently visited assets
     */
    PagingData<DataMapTableSummaryVO> recent(String workspaceId, String userId, int pageNo, int pageSize);

    /**
     * Adds the caller's favourite; repeating the call is accepted.
     *
     * @param workspaceId current Workspace
     * @param userId authenticated caller
     * @param tableId asset identity
     */
    void addFavorite(String workspaceId, String userId, String tableId);

    /**
     * Removes the caller's favourite; repeating the call is accepted.
     *
     * @param workspaceId current Workspace
     * @param userId authenticated caller
     * @param tableId asset identity
     */
    void removeFavorite(String workspaceId, String userId, String tableId);

    /**
     * Records one visit for the caller on this asset.
     *
     * @param workspaceId current Workspace
     * @param userId authenticated caller
     * @param tableId asset identity
     */
    void recordView(String workspaceId, String userId, String tableId);
}
