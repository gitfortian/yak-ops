package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialects;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionRuntime;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.Objects;

/**
 * JDBC 目标表 DDL 执行边界。
 *
 * <p>只允许根据受控 DataSourceTablePath + YakTableSchema 通过 JdbcDialect 生成并执行 CREATE TABLE，
 * 不接受任意用户 SQL。</p>
 *
 * @author weifuwan
 * @since 2026-10-04
 */
public final class JdbcTargetTableProvisioner {

    private final JdbcConnectionProvider connectionProvider;

    public JdbcTargetTableProvisioner() {
        this(JdbcConnectionRuntime.getInstance());
    }

    JdbcTargetTableProvisioner(JdbcConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * 生成并执行 CREATE TABLE。
     *
     * @return 实际执行的 CREATE TABLE SQL
     */
    public String createTable(
            DataSourceConnection connection,
            DataSourceTablePath table,
            YakTableSchema schema,
            int timeoutSeconds)
            throws Exception {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(schema, "schema must not be null");

        JdbcDialect dialect = JdbcDialects.forType(connection.type());
        String sql = dialect.createTableSql(table, schema);
        try (var opened = connectionProvider.open(connection, Math.max(1, timeoutSeconds));
                var statement = opened.createStatement()) {
            statement.setQueryTimeout(Math.max(1, timeoutSeconds));
            statement.execute(sql);
        }
        return sql;
    }
}
