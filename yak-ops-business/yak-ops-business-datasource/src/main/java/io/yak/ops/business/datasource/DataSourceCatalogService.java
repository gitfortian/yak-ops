package io.yak.ops.business.datasource;

import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.List;

/**
 * Provides Workspace-scoped physical metadata for registered datasources.
 *
 * <p>Each operation resolves the saved connection under the active Workspace.
 * Credentials and JDBC connections remain inside the Datasource business boundary.
 */
public interface DataSourceCatalogService {

    /** Lists databases visible through the saved datasource. */
    List<String> listDatabases(String datasourceId);

    /** Lists schemas visible in the selected database. */
    List<String> listSchemas(String datasourceId, String database);

    /** Lists physical tables and views using the requested Catalog filters. */
    List<DataSourceCatalogTableVO> listTables(String datasourceId, DataSourceCatalogQueryDTO query);

    /** Lists columns and primary-key metadata for a physical table. */
    List<DataSourceCatalogColumnVO> listColumns(String datasourceId, DataSourceTablePathDTO path);

    /** Resolves the dialect-normalized table schema for internal task validation. */
    TableSchema getTableSchema(String datasourceId, TableId tableId);
}
