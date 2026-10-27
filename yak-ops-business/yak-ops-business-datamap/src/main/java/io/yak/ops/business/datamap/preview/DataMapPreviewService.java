package io.yak.ops.business.datamap.preview;

import io.yak.ops.common.bean.vo.datamap.DataMapPreviewVO;

/** Reads a bounded sample of one catalog asset. */
public interface DataMapPreviewService {

    /**
     * Samples at most one hundred rows of an asset.
     *
     * <p>The table is resolved from the catalog, never from the request, and its qualified name
     * is rendered by the dialect. Production datasources are refused before a connection opens.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated caller, recorded in the audit log line
     * @param tableId asset identity
     * @return the bounded sample with its privacy notice
     */
    DataMapPreviewVO preview(String workspaceId, String actor, String tableId);
}
