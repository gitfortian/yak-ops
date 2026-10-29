package io.yak.ops.business.datamap.governance;

import java.util.List;

/**
 * Read-only catalog contract owned by the data map.
 *
 * <p>Governance reads through it instead of the catalog tables, so the data map keeps sole ownership
 * of its schema and never depends on governance.
 */
public interface DataMapGovernanceQuery {

    /** Whether a crawl currently holds the Workspace, which makes a scan skip instead of run. */
    boolean hasActiveCrawl(String workspaceId);

    /**
     * One keyset page of ACTIVE tables ordered by identity.
     *
     * @param afterId last identity of the previous page; empty for the first page
     * @param limit page size
     */
    List<GovernanceTableSnapshot> activeTables(String workspaceId, String afterId, int limit);
}
