package io.yak.ops.plugin.database.jdbc;

/**
 * Datasource JDBC Runtime 暴露给非 JDBC 协议客户端的数据库网络端点，必要时持有 SSH 隧道生命周期。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class JdbcEndpoint implements AutoCloseable {

    private final String host;
    private final int port;
    private final AutoCloseable resource;

    JdbcEndpoint(String host, int port, AutoCloseable resource) {
        this.host = host;
        this.port = port;
        this.resource = resource;
    }

    public static JdbcEndpoint direct(String host, int port) {
        return new JdbcEndpoint(host, port, null);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    @Override
    public void close() throws Exception {
        if (resource != null) {
            resource.close();
        }
    }
}
