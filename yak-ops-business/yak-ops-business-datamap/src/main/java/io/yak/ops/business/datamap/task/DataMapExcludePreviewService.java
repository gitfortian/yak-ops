package io.yak.ops.business.datamap.task;

import io.yak.ops.common.bean.dto.datamap.DataMapCrawlExcludePreviewDTO;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlScopePreviewDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlExcludePreviewVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlScopePreviewVO;

/**
 * Resolves which tables an exclusion pattern matches in one namespace.
 *
 * <p>The expression is evaluated by the datasource engine, so preview never enumerates the whole
 * namespace into the application.
 */
public interface DataMapExcludePreviewService {

    /**
     * Matches the expression against the tables of the requested namespace.
     *
     * <p>A syntactically invalid expression is refused before a connection is opened.
     *
     * @param workspaceId current Workspace
     * @param input datasource, namespace and regular expression
     * @return matched table names, bounded by the preview limit
     */
    DataMapCrawlExcludePreviewVO preview(String workspaceId, DataMapCrawlExcludePreviewDTO input);

    /**
     * Counts the tables of the requested namespace.
     *
     * <p>The editor uses it to show a task's size before saving, so a namespace over the collection
     * ceiling is refused while the user can still change the scope.
     *
     * @param workspaceId current Workspace
     * @param input datasource and namespace to count
     * @return table count, the ceiling it is compared against, and whether it is reached
     */
    DataMapCrawlScopePreviewVO previewScope(String workspaceId, DataMapCrawlScopePreviewDTO input);
}
