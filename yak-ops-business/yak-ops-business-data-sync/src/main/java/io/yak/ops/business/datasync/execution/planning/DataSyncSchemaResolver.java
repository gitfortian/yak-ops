package io.yak.ops.business.datasync.execution.planning;

import io.yak.ops.business.datasync.catalog.DataSyncCatalogColumns;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将 Datasource Catalog 字段元数据整理为 Data Sync Source Schema 与目标写入 Schema。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class DataSyncSchemaResolver {

    private DataSyncSchemaResolver() {}

    static YakTableSchema sourceSchema(List<DataSourceCatalogColumnVO> sourceColumns) {
        return JdbcSchemaMapper.fromColumns(toColumns(sourceColumns, false));
    }

    static YakTableSchema targetWriteSchema(
            List<DataSourceCatalogColumnVO> sourceColumns, List<DataSourceCatalogColumnVO> targetColumns) {
        List<DataSourceCatalogColumnVO> orderedSource = sourceColumns.stream()
                .sorted(Comparator.comparingInt(DataSyncSchemaResolver::ordinal))
                .toList();
        Map<String, DataSourceCatalogColumnVO> targetByName = DataSyncCatalogColumns.indexByName(targetColumns);

        List<DataSourceColumn> mapped = new ArrayList<>(orderedSource.size());
        for (int index = 0; index < orderedSource.size(); index++) {
            DataSourceCatalogColumnVO source = orderedSource.get(index);
            DataSourceCatalogColumnVO target = DataSyncCatalogColumns.findByName(targetByName, source.getName());
            if (target == null) {
                throw new IllegalArgumentException("target column not found: " + source.getName());
            }

            DataSourceColumn targetColumn = DataSyncCatalogColumns.toColumn(target, index + 1);
            if (targetColumn == null) {
                throw new IllegalArgumentException("catalog column metadata is incomplete");
            }
            mapped.add(targetColumn);
        }
        return JdbcSchemaMapper.fromColumns(mapped);
    }

    private static List<DataSourceColumn> toColumns(List<DataSourceCatalogColumnVO> columns, boolean rewriteOrdinal) {
        List<DataSourceCatalogColumnVO> ordered = columns.stream()
                .sorted(Comparator.comparingInt(DataSyncSchemaResolver::ordinal))
                .toList();
        List<DataSourceColumn> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            DataSourceCatalogColumnVO column = ordered.get(index);
            DataSourceColumn mapped =
                    DataSyncCatalogColumns.toColumn(column, rewriteOrdinal ? index + 1 : ordinal(column));
            if (mapped == null) throw new IllegalArgumentException("catalog column metadata is incomplete");
            result.add(mapped);
        }
        return result;
    }

    private static int ordinal(DataSourceCatalogColumnVO column) {
        return column.getOrdinalPosition() == null ? Integer.MAX_VALUE : column.getOrdinalPosition();
    }
}
