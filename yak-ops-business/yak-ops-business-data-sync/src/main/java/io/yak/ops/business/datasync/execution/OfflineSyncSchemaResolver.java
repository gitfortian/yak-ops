package io.yak.ops.business.datasync.execution;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcSchemaMapper;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 Datasource Catalog 字段元数据整理为离线同步 Source Schema 与目标写入 Schema。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class OfflineSyncSchemaResolver {

    private OfflineSyncSchemaResolver() {}

    static YakTableSchema sourceSchema(List<DataSourceCatalogColumnVO> sourceColumns) {
        return JdbcSchemaMapper.fromColumns(toColumns(sourceColumns, false));
    }

    static YakTableSchema targetWriteSchema(
            List<DataSourceCatalogColumnVO> sourceColumns, List<DataSourceCatalogColumnVO> targetColumns) {
        List<DataSourceCatalogColumnVO> orderedSource = sourceColumns.stream()
                .sorted(Comparator.comparingInt(OfflineSyncSchemaResolver::ordinal))
                .toList();
        Map<String, DataSourceCatalogColumnVO> targetByName = new LinkedHashMap<>();
        for (DataSourceCatalogColumnVO target : targetColumns) {
            if (target.getName() != null) {
                targetByName.put(target.getName().toLowerCase(Locale.ROOT), target);
            }
        }

        List<DataSourceColumn> mapped = new ArrayList<>(orderedSource.size());
        for (int index = 0; index < orderedSource.size(); index++) {
            DataSourceCatalogColumnVO source = orderedSource.get(index);
            DataSourceCatalogColumnVO target = targetByName.get(source.getName().toLowerCase(Locale.ROOT));
            if (target == null) {
                throw new IllegalArgumentException("target column not found: " + source.getName());
            }
            mapped.add(toColumn(target, index + 1));
        }
        return JdbcSchemaMapper.fromColumns(mapped);
    }

    private static List<DataSourceColumn> toColumns(List<DataSourceCatalogColumnVO> columns, boolean rewriteOrdinal) {
        List<DataSourceCatalogColumnVO> ordered = columns.stream()
                .sorted(Comparator.comparingInt(OfflineSyncSchemaResolver::ordinal))
                .toList();
        List<DataSourceColumn> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            DataSourceCatalogColumnVO column = ordered.get(index);
            result.add(toColumn(column, rewriteOrdinal ? index + 1 : ordinal(column)));
        }
        return result;
    }

    private static DataSourceColumn toColumn(DataSourceCatalogColumnVO column, int ordinal) {
        if (column.getJdbcType() == null || column.getName() == null) {
            throw new IllegalArgumentException("catalog column metadata is incomplete");
        }
        return new DataSourceColumn(
                column.getName(),
                column.getTypeName(),
                column.getJdbcType(),
                column.getSize(),
                column.getScale(),
                Boolean.TRUE.equals(column.getNullable()),
                ordinal,
                Boolean.TRUE.equals(column.getPrimaryKey()),
                column.getRemarks());
    }

    private static int ordinal(DataSourceCatalogColumnVO column) {
        return column.getOrdinalPosition() == null ? Integer.MAX_VALUE : column.getOrdinalPosition();
    }
}
