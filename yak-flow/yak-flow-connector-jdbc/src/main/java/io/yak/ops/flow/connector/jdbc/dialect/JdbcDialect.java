package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.stream.Collectors;

/**
 * YakFlow JDBC Connector 的数据库 SQL 方言边界，统一拥有标识符、运行 SQL 与目标表 DDL / 原生类型映射。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface JdbcDialect {

    String quoteIdentifier(String identifier);

    String qualifiedTable(DataSourceTablePath table);

    /**
     * 把 YakFlow 逻辑字段映射为当前数据库可用于目标表 DDL 的原生类型。
     *
     * @param column 逻辑字段
     * @return 原生类型规划
     */
    JdbcNativeType nativeType(YakColumn column);

    /**
     * 生成一张目标表的 CREATE TABLE SQL；只生成，不执行。
     *
     * @param table 目标表路径
     * @param schema 目标逻辑 Schema
     * @return CREATE TABLE SQL
     */
    default String createTableSql(DataSourceTablePath table, YakTableSchema schema) {
        String definitions = schema.columns().stream()
                .map(column -> {
                    String nullable = column.nullable() ? "" : " NOT NULL";
                    return quoteIdentifier(column.name()) + " " + nativeType(column).ddl() + nullable;
                })
                .collect(Collectors.joining(", "));

        if (!schema.primaryKeys().isEmpty()) {
            String primaryKeys = schema.primaryKeys().stream()
                    .map(this::quoteIdentifier)
                    .collect(Collectors.joining(", "));
            definitions += ", PRIMARY KEY (" + primaryKeys + ")";
        }
        return "CREATE TABLE " + qualifiedTable(table) + " (" + definitions + ")";
    }

    default String selectSql(DataSourceTablePath table, YakTableSchema schema) {
        return selectSql(table, schema, null);
    }

    default String selectRangeSql(DataSourceTablePath table, YakTableSchema schema, String splitColumn) {
        requireSplitColumn(splitColumn);
        return selectSql(table, schema, splitColumn);
    }

    default String splitStatisticsSql(DataSourceTablePath table, String splitColumn) {
        requireSplitColumn(splitColumn);
        String column = quoteIdentifier(splitColumn);
        return "SELECT MIN(" + column + "), MAX(" + column + "), COUNT(*) FROM " + qualifiedTable(table);
    }

    default String truncateSql(DataSourceTablePath table) {
        return "TRUNCATE TABLE " + qualifiedTable(table);
    }

    default String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        throw new UnsupportedOperationException("UPSERT is not supported by this JDBC dialect");
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

    private void requireSplitColumn(String splitColumn) {
        if (splitColumn == null || splitColumn.isBlank()) {
            throw new IllegalArgumentException("splitColumn must not be blank");
        }
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
