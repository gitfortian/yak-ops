package io.yak.ops.boot.config;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskValidationContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Supplies Workspace-checked, read-only Datasource metadata to Task plugins.
 * Real credentials remain inside Datasource and are never passed to a Task plugin.
 */
@Component
public class DataSourceTaskValidationContext implements TaskValidationContext {
    @Resource
    private DataSourceService dataSourceService;

    @Override
    public DataSourceInfo dataSource(String id) {
        DataSourceVO value = dataSourceService.queryDataSource(id);
        return new DataSourceInfo(value.getDbType(), value.getDatabase(), value.getSchema());
    }

    @Override
    public TableSchema tableSchema(String id, String database, String schema, String table) {
        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(database);
        path.setSchema(schema);
        path.setTable(table);
        dataSourceService.queryCatalogTable(id, path);
        return dataSourceService.queryTableSchema(id, path);
    }
}
