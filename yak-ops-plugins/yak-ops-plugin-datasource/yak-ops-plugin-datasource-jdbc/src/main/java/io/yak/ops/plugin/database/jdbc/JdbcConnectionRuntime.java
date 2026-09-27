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
        AbstractJdbcDataSourcePlugin plugin = pluginFor(connection);
        JdbcConnectionProperties jdbcConnection = plugin.requireJdbcConnection(connection);
        return plugin.openJdbcConnection(jdbcConnection, Math.max(1, timeoutSeconds));
    }

    /**
     * 为非 JDBC 协议客户端解析真实数据库网络端点，并复用 Datasource 的 SSH 隧道生命周期。
     *
     * @param connection 已规范化 JDBC 连接
     * @param timeoutSeconds SSH 建连超时秒数
     * @return 可关闭的数据库端点
     * @throws Exception 端点或 SSH 建立失败
     */
    public JdbcEndpoint openEndpoint(DataSourceConnection connection, int timeoutSeconds) throws Exception {
        AbstractJdbcDataSourcePlugin plugin = pluginFor(connection);
        JdbcConnectionProperties jdbcConnection = plugin.requireJdbcConnection(connection);
        if (jdbcConnection.host() == null || jdbcConnection.host().isBlank() || jdbcConnection.port() <= 0) {
            throw new IllegalArgumentException("当前连接缺少结构化 host/port，无法用于 CDC 网络端点");
        }

        if (!jdbcConnection.sshTunnel().enabled()) {
            return new JdbcEndpoint(jdbcConnection.host(), jdbcConnection.port(), null);
        }

        SshTunnel tunnel = SshTunnel.open(
                jdbcConnection.sshTunnel(), jdbcConnection.host(), jdbcConnection.port(), Math.max(1, timeoutSeconds));
        return new JdbcEndpoint("127.0.0.1", tunnel.localPort(), tunnel);
    }

    private AbstractJdbcDataSourcePlugin pluginFor(DataSourceConnection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        return plugins.stream()
                .filter(candidate -> candidate.descriptor().matchesType(connection.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("暂不支持 JDBC 数据源类型：" + connection.type()));
    }
}
