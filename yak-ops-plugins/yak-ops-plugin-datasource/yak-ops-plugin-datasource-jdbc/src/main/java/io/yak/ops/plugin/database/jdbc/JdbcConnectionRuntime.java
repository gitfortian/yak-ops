package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.plugin.database.jdbc.mysql.MySqlDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.oracle.OracleDataSourcePlugin;
import io.yak.ops.plugin.database.jdbc.postgresql.PostgreSqlDataSourcePlugin;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;

/**
 * 复用 Datasource JDBC Provider 的 Driver、SSH 与连接规则，为需要真实 JDBC Connection 的内部能力提供统一入口。
 *
 * <p>调用方必须传入 Provider 已解析并规范化的 DataSourceConnection；该入口不重新解析连接 JSON，也不得记录凭证。</p>
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class JdbcConnectionRuntime implements JdbcConnectionProvider {

    private static final JdbcConnectionRuntime INSTANCE = new JdbcConnectionRuntime();

    private final List<AbstractJdbcDataSourcePlugin> plugins =
            List.of(new MySqlDataSourcePlugin(), new PostgreSqlDataSourcePlugin(), new OracleDataSourcePlugin());

    private JdbcConnectionRuntime() {}

    public static JdbcConnectionRuntime getInstance() {
        return INSTANCE;
    }

    @Override
    public Connection open(DataSourceConnection connection, int timeoutSeconds) throws Exception {
        Objects.requireNonNull(connection, "connection must not be null");
        AbstractJdbcDataSourcePlugin plugin = plugins.stream()
                .filter(candidate -> candidate.descriptor().matchesType(connection.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("暂不支持 JDBC 数据源类型：" + connection.type()));
        JdbcConnectionProperties jdbcConnection = plugin.requireJdbcConnection(connection);
        return plugin.openJdbcConnection(jdbcConnection, Math.max(1, timeoutSeconds));
    }
}
