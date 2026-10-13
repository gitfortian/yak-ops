package io.yak.ops.connector.jdbc;

import io.yak.ops.connector.base.factory.ConnectorResource;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import java.util.Objects;

/**
 * Execution-scoped JDBC connection binding with driver and credentials supplied by the
 * Datasource service. This object must not be persisted, compared, or logged.
 */
public final class JdbcConnectorResource implements ConnectorResource {

    private final JdbcConnectionProvider provider;
    private final String jdbcUrl;
    private final String datasourceType;
    private final String username;
    private final String password;
    private final String host;
    private final int port;
    private final boolean tunneled;

    public JdbcConnectorResource(
            JdbcConnectionProvider provider,
            String jdbcUrl,
            String datasourceType,
            String username,
            String password,
            String host,
            int port,
            boolean tunneled) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.datasourceType = Objects.requireNonNull(datasourceType, "datasourceType");
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
        this.tunneled = tunneled;
    }

    public JdbcConnectionProvider provider() {
        return provider;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String datasourceType() {
        return datasourceType;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean tunneled() {
        return tunneled;
    }

    @Override
    public String toString() {
        return "JdbcConnectorResource{credentials=redacted}";
    }
}
