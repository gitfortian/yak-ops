package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PostgreSQL JDBC SQL 标识符和 Schema 表路径规则。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class PostgreSqlJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifiedTable(DataSourceTablePath table) {
        if (table.schema() == null || table.schema().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.schema()) + "." + quoteIdentifier(table.table());
    }

    @Override
    public String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        String conflictColumns = primaryKeys.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        List<YakColumn> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        if (updateColumns.isEmpty()) {
            return insertSql(table, schema) + " ON CONFLICT (" + conflictColumns + ") DO NOTHING";
        }
        String updateClause = updateColumns.stream()
                .map(column -> {
                    String name = quoteIdentifier(column.name());
                    return name + " = EXCLUDED." + name;
                })
                .collect(Collectors.joining(", "));
        return insertSql(table, schema)
                + " ON CONFLICT ("
                + conflictColumns
                + ") DO UPDATE SET "
                + updateClause;
    }

    private void requirePrimaryKey(YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL UPSERT requires primary key");
        }
    }
}
