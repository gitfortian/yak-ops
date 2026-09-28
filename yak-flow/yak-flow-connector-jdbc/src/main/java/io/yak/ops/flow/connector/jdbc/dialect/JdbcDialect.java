package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.stream.Collectors;

/**
 * YakFlow JDBC Connector 的数据库 SQL 方言边界，只处理标识符与当前批量同步需要的固定 SQL。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface JdbcDialect {

    String quoteIdentifier(String identifier);

    String qualifiedTable(DataSourceTablePath table);

    default String selectSql(DataSourceTablePath table, YakTableSchema schema) {
        return selectSql(table, schema, null);
    }

    default String selectRangeSql(DataSourceTablePath table, YakTableSchema schema, String splitColumn) {
        if (splitColumn == null || splitColumn.isBlank()) {
            throw new IllegalArgumentException("splitColumn must not be blank");
        }
        return selectSql(table, schema, splitColumn);
    }

    default String insertSql(DataSourceTablePath table, YakTableSchema schema) {
        String columns = schema.columns().stream()
                .map(YakColumn::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String placeholders = schema.columns().stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + qualifiedTable(table) + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    default String deleteSql(DataSourceTablePath table, YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("DELETE changelog requires primary key");
        }
        String predicate = schema.primaryKeys().stream()
                .map(primaryKey -> quoteIdentifier(primaryKey) + " = ?")
                .collect(Collectors.joining(" AND "));
        return "DELETE FROM " + qualifiedTable(table) + " WHERE " + predicate;
    }

    private String selectSql(DataSourceTablePath table, YakTableSchema schema, String splitColumn) {
        String columns = schema.columns().stream()
                .map(YakColumn::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String where = splitColumn == null
                ? ""
                : " WHERE " + quoteIdentifier(splitColumn) + " >= ? AND " + quoteIdentifier(splitColumn) + " <= ?";
        String orderBy = schema.primaryKeys().isEmpty()
                ? ""
                : " ORDER BY "
                        + schema.primaryKeys().stream()
                                .map(this::quoteIdentifier)
                                .collect(Collectors.joining(", "));
        return "SELECT " + columns + " FROM " + qualifiedTable(table) + where + orderBy;
    }
}
