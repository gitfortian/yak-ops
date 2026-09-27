package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;

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
}
