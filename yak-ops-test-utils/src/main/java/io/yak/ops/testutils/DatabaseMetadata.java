package io.yak.ops.testutils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Connection details for a disposable test database. Credentials are never formatted. */
public final class DatabaseMetadata {
    private final String url;
    private final String username;
    private final String password;
    private final String host;
    private final int port;

    public DatabaseMetadata(String url, String username, String password, String host, int port) {
        this.url = Objects.requireNonNull(url);
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }

    public String url() {
        return url;
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

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public void execute(String sql) throws SQLException {
        try (Connection connection = connect();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            statement.execute(sql);
        }
    }

    @Override
    public String toString() {
        return "Disposable database connection [redacted]";
    }
}
