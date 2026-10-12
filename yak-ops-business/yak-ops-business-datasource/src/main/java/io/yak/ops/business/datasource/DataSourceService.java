package io.yak.ops.business.datasource;

import io.yak.ops.common.bean.dto.datasource.DataSourceBatchIdsDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceBatchConnectTestResultVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceConnectionPropertyKeysVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.core.types.TableSchema;
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

    /** Lists databases visible through the saved datasource connection. */
    List<String> queryCatalogDatabases(String id);

    /** Lists schemas visible under the selected database. */
    List<String> queryCatalogSchemas(String id, String database);

    /** Lists physical tables and views visible through the saved datasource. */
    List<DataSourceCatalogTableVO> queryCatalogTables(String id, DataSourceCatalogQueryDTO dto);

    /** Lists column metadata for the selected physical table. */
    List<DataSourceCatalogColumnVO> queryCatalogColumns(String id, DataSourceTablePathDTO dto);

    /**
     * Resolves the Connector-normalized physical table schema for internal execution planning.
     *
     * <p>Workspace ownership and saved credentials are validated by Datasource. HTTP clients
     * continue to use the existing column browse endpoint and never receive a Connection.
     *
     * @param id workspace-scoped datasource ID
     * @param path exact physical table
     * @return dialect-normalized ordered columns and primary keys
     */
    TableSchema queryTableSchema(String id, DataSourceTablePathDTO path);

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
