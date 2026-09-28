package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL JDBC SQL 标识符和表路径规则。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
final class MySqlJdbcDialect implements JdbcDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public String qualifiedTable(DataSourceTablePath table) {
        if (table.database() == null || table.database().isBlank()) {
            return quoteIdentifier(table.table());
        }
        return quoteIdentifier(table.database()) + "." + quoteIdentifier(table.table());
    }

    @Override
    public String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        requirePrimaryKey(schema);
        List<String> primaryKeys = schema.primaryKeys();
        List<YakColumn> updateColumns = schema.columns().stream()
                .filter(column -> !primaryKeys.contains(column.name()))
                .toList();
        String updateClause = updateColumns.isEmpty()
                ? noOpUpdate(primaryKeys.getFirst())
                : updateColumns.stream()
                        .map(column -> {
                            String name = quoteIdentifier(column.name());
                            return name + " = VALUES(" + name + ")";
                        })
                        .collect(Collectors.joining(", "));
        return insertSql(table, schema) + " ON DUPLICATE KEY UPDATE " + updateClause;
    }

    private String noOpUpdate(String primaryKey) {
        String name = quoteIdentifier(primaryKey);
        return name + " = VALUES(" + name + ")";
    }

    private void requirePrimaryKey(YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("MySQL UPSERT requires primary key");
        }
    }
}
