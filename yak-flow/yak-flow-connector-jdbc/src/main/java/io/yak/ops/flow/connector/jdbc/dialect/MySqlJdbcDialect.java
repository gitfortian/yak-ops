package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;

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
}
