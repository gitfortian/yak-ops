package io.yak.ops.connector.jdbc.database.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Exercises real loopback SSH forwarding followed by a failed JDBC connection attempt. */
class JdbcSshConnectionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(30)
    void closesTheForwardingPortAndSessionWhenJdbcConnectionFails() throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        SimpleGeneratorHostKeyProvider keys =
                new SimpleGeneratorHostKeyProvider(temporaryDirectory.resolve("host-key"));
        keys.setAlgorithm("RSA");
        keys.setKeySize(2048);
        server.setKeyPairProvider(keys);
        server.setPasswordAuthenticator((username, password, session) ->
                "test-user".equals(username) && "test-password".equals(password));
        server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        FailingDriver driver = new FailingDriver();
        int previousLoginTimeout = DriverManager.getLoginTimeout();
        server.start();
        try {
            DriverManager.registerDriver(driver);
            try (var executor = Executors.newSingleThreadExecutor();
                    ServerSocket target = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                target.setSoTimeout(5000);
                var exchange = executor.submit(() -> {
                    try (Socket accepted = target.accept()) {
                        accepted.setSoTimeout(5000);
                        assertEquals(42, accepted.getInputStream().read());
                        accepted.getOutputStream().write(43);
                    }
                    return null;
                });
                Configuration options = new Configuration()
                        .set(JdbcConnectionOptions.URL, FailingDriver.PREFIX + "127.0.0.1:" + target.getLocalPort() + "/app")
                        .set(JdbcConnectionOptions.HOST, "127.0.0.1")
                        .set(JdbcConnectionOptions.PORT, target.getLocalPort())
                        .set(JdbcConnectionOptions.TIMEOUT, 5)
                        .set(JdbcConnectionOptions.SSH_ENABLED, true)
                        .set(JdbcConnectionOptions.SSH_HOST, "127.0.0.1")
                        .set(JdbcConnectionOptions.SSH_PORT, server.getPort())
                        .set(JdbcConnectionOptions.SSH_USERNAME, "test-user")
                        .set(JdbcConnectionOptions.SSH_PASSWORD, "test-password")
                        .set(JdbcConnectionOptions.SSH_JDBC_URL, FailingDriver.PREFIX + "127.0.0.1:{port}/app");
                JdbcConnectionProvider provider =
                        new DefaultJdbcConnectionProvider(new JdbcConnectionOptions(options.toReadonlyConfig()));

                SQLException failure = assertThrows(SQLException.class, provider::getConnection);
                assertSame(driver.failure, failure);
                exchange.get(5, TimeUnit.SECONDS);
                assertTrue(driver.forwardedPort > 0);
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                    while (!server.getActiveSessions().isEmpty()) {
                        Thread.sleep(10);
                    }
                });
                assertThrows(ConnectException.class, () -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress("127.0.0.1", driver.forwardedPort), 1000);
                    }
                });
            } finally {
                DriverManager.deregisterDriver(driver);
            }
        } finally {
            DriverManager.setLoginTimeout(previousLoginTimeout);
            server.stop(true);
        }
    }

    /** A JDBC driver which proves the tunnel works before reporting connection failure. */
    private static final class FailingDriver implements Driver {
        private static final String PREFIX = "jdbc:yak-ssh-test://";
        private final SQLException failure = new SQLException("Test JDBC connection failed", "08001");
        private int forwardedPort;

        @Override
        public Connection connect(String url, Properties properties) throws SQLException {
            if (!acceptsURL(url)) {
                return null;
            }
            forwardedPort = URI.create(url.substring("jdbc:".length())).getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", forwardedPort), 5000);
                socket.setSoTimeout(5000);
                socket.getOutputStream().write(42);
                assertEquals(43, socket.getInputStream().read());
            } catch (IOException error) {
                throw new SQLException("Test SSH forwarding failed", error);
            }
            throw failure;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith(PREFIX);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
