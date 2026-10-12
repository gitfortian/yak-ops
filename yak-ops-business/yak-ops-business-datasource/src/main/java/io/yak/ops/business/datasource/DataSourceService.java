package io.yak.ops.business.datasource;

import io.yak.ops.common.bean.dto.datasource.DataSourceBatchIdsDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceBatchConnectTestResultVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceConnectionPropertyKeysVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/**
 * Defines the Datasource business boundary exposed to Boot.
 *
 * <p>Owns configuration CRUD, paged search and connectivity tests; delegates database-specific parameter handling to plugins.
 */
public interface DataSourceService {

    /**
     * Creates a datasource with normalized connection parameters.
     *
     * @param dto datasource configuration
     * @return {@code true} after successful persistence
     */
    boolean addDataSource(DataSourceDTO dto);

    /**
     * Updates a datasource without changing its registered database type.
     *
     * @param id datasource identifier
     * @param dto updated configuration
     * @return {@code true} after a successful update
     */
    boolean updateDataSource(String id, DataSourceDTO dto);

    /** Returns datasource details with sensitive connection values masked. */
    DataSourceVO queryDataSource(String id);

    /**
     * Lists advanced connection-property names recommended by a provider.
     *
     * @param dbType provider type or supported alias
     * @return candidate property names
     */
    DataSourceConnectionPropertyKeysVO queryConnectionPropertyKeys(String dbType);

    /** Deletes a datasource in the active Workspace. */
    boolean deleteDataSource(String id);

    /**
     * Deletes multiple datasources in one transaction; any missing ID or failure rolls back the operation.
     *
     * @param dto identifiers to delete
     * @return {@code true} only when all deletions succeed
     */
    boolean batchDeleteDataSources(DataSourceBatchIdsDTO dto);

    /** Returns datasource records using the established paging contract. */
    PagingData<DataSourceVO> queryDataSourcePage(DataSourceQueryDTO dto);

    /**
     * Tests a saved datasource connection and persists the resulting connection status.
     *
     * @param id datasource identifier
     * @return {@code true} on successful connectivity
     */
    boolean testConnection(String id);

    /**
     * Tests saved datasource connections independently, without aborting the remaining items after one failure.
     *
     * @param dto requested datasource identifiers
     * @return connection results in request order
     */
    List<DataSourceBatchConnectTestResultVO> batchTestConnections(DataSourceBatchIdsDTO dto);

    /**
     * Tests request-supplied connection parameters without persisting a datasource.
     *
     * @param dto connection test input
     * @return {@code true} on successful connectivity
     */
    boolean testConnection(DataSourceConnectTestDTO dto);
}
