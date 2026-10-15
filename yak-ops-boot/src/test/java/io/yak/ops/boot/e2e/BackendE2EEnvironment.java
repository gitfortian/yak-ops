package io.yak.ops.boot.e2e;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Starts isolated databases and a real packaged Yak Ops JVM, including restart tests. */
final class BackendE2EEnvironment implements AutoCloseable {
    static final String PASSWORD = "backend-e2e-only-password";
    private final GenericContainer<?> mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.0.39"))
            .withEnv("MYSQL_ROOT_PASSWORD", PASSWORD)
            .withEnv("MYSQL_DATABASE", "yak_ops")
            .withExposedPorts(3306)
            .withCommand("--server-id=5577", "--log-bin=mysql-bin", "--binlog-format=ROW", "--binlog-row-image=FULL");
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName("ods")
            .withUsername("yak")
            .withPassword(PASSWORD);
    private final Path root;
    private final Path stateDirectory;
    private final Path applicationLog;
    private Process application;
    private int port;

    BackendE2EEnvironment() {
        try {
            root = Files.createTempDirectory("yak-backend-e2e-");
            stateDirectory = Files.createDirectory(root.resolve("state"));
            applicationLog = root.resolve("backend.log");
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot create backend E2E state directory", failure);
        }
    }

    void start() {
        mysql.start();
        postgres.start();
        until("MySQL startup", Duration.ofSeconds(90), () -> {
            try {
                return mysql("SELECT 1");
            } catch (RuntimeException unavailable) {
                return "";
            }
        }, "1"::equals);
        mysql("CREATE DATABASE app");
        mysql("CREATE TABLE app.orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        mysql("INSERT INTO app.orders VALUES (1,'one'),(2,'two')");
        pg("CREATE TABLE public.ods_orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        boot();
    }

    String mysqlHost() { return mysql.getHost(); }
    int mysqlPort() { return mysql.getMappedPort(3306); }
    String postgresHost() { return postgres.getHost(); }
    int postgresPort() { return postgres.getMappedPort(5432); }

    String mysql(String query) {
        return sql("jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/yak_ops?useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=true",
                "root", PASSWORD, query, "\t", "NULL");
    }

    String pg(String query) {
        return sql(postgres.getJdbcUrl(), postgres.getUsername(), PASSWORD, query, "|", "");
    }

    private static String sql(String url, String user, String password, String query, String separator, String nullValue) {
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            boolean result = statement.execute(query);
            String text = "";
            while (true) {
                if (result) {
                    try (ResultSet rows = statement.getResultSet()) {
                        ResultSetMetaData meta = rows.getMetaData();
                        List<String> values = new ArrayList<>();
                        while (rows.next()) {
                            List<String> columns = new ArrayList<>();
                            for (int index = 1; index <= meta.getColumnCount(); index++) {
                                String value = rows.getString(index);
                                columns.add(value == null ? nullValue : value);
                            }
                            values.add(String.join(separator, columns));
                        }
                        text = String.join("\n", values);
                    }
                } else if (statement.getUpdateCount() == -1) {
                    break;
                }
                result = statement.getMoreResults();
            }
            return text;
        } catch (Exception failure) {
            throw new IllegalStateException("Database test statement failed (" + failure.getClass().getSimpleName() + ")", failure);
        }
    }

    private static int randomPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot reserve local HTTP port", failure);
        }
    }

    private Process startProcess(int httpPort, Path log) {
        String jar = System.getProperty("yak.e2e.jar");
        if (jar == null || !Files.isRegularFile(Path.of(jar))) {
            throw new IllegalStateException("Packaged Boot JAR is missing; use -Pbackend-e2e verify");
        }
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", jar, "--yak.task.execution.state-directory=" + stateDirectory);
        Map<String, String> env = builder.environment();
        env.put("SERVER_PORT", Integer.toString(httpPort));
        env.put("YAK_DATABASE_URL", "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                + "/yak_ops?useSSL=false&allowPublicKeyRetrieval=true");
        env.put("YAK_DATABASE_USERNAME", "root");
        env.put("YAK_DATABASE_PASSWORD", PASSWORD);
        env.put("YAK_SECURITY_BOOTSTRAP_ENABLED", "true");
        env.put("YAK_SECURITY_BOOTSTRAP_USERNAME", "taske2e");
        env.put("YAK_SECURITY_BOOTSTRAP_PASSWORD", PASSWORD);
        env.put("YAK_SECURITY_BOOTSTRAP_REAL_NAME", "Backend E2E");
        env.put("YAK_OPS_DATASOURCE_MASTER_KEY", "backend-e2e-isolated-master-secret");
        env.put("YAK_LOG_FILE", root.resolve("app.log").toString());
        env.put("YAK_TASK_LOG_DIRECTORY", root.resolve("tasks").toString());
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        try {
            return builder.start();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot launch the packaged Boot application", failure);
        }
    }

    void boot() {
        port = randomPort();
        application = startProcess(port, applicationLog);
        HttpClient health = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        until("Boot readiness", Duration.ofSeconds(150), () -> {
            if (!application.isAlive()) {
                throw new AssertionError("The Boot process exited before reaching readiness: " + applicationLog);
            }
            try {
                return health.send(java.net.http.HttpRequest.newBuilder()
                                .uri(URI.create(url() + "/v3/api-docs"))
                                .timeout(Duration.ofSeconds(3)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            } catch (Exception unavailable) {
                return 0;
            }
        }, code -> code == 200);
    }

    TaskApiClient login() {
        TaskApiClient client = new TaskApiClient(url());
        client.call("POST", "/api/v1/account/login", Map.of("userName", "taske2e", "pw", PASSWORD));
        return client;
    }

    String url() {
        return "http://127.0.0.1:" + port;
    }

    Path stateDirectory() {
        return stateDirectory;
    }

    Path applicationLog() {
        return applicationLog;
    }

    Process startContender() {
        return startProcess(randomPort(), root.resolve("rejected-owner.log"));
    }

    String rejectedOwnerLog() {
        try {
            return Files.readString(root.resolve("rejected-owner.log"));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    void terminate(boolean force) {
        if (application != null && application.isAlive()) {
            if (force) application.destroyForcibly();
            else application.destroy();
            try {
                if (!application.waitFor(30, TimeUnit.SECONDS)) {
                    application.destroyForcibly();
                    application.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruption);
            }
        }
    }

    void restart(boolean force) {
        terminate(force);
        boot();
    }

    static <T> T until(String description, Duration timeout, Supplier<T> read, Predicate<T> done) {
        long limit = System.nanoTime() + timeout.toNanos();
        T observed = null;
        while (System.nanoTime() < limit) {
            observed = read.get();
            if (done.test(observed)) return observed;
            try {
                TimeUnit.MILLISECONDS.sleep(250);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruption);
            }
        }
        throw new AssertionError(description + " did not reach the expected state within " + timeout
                + " (last value: " + String.valueOf(observed).replace(PASSWORD, "[REDACTED]") + ")");
    }

    @Override
    public void close() {
        try {
            terminate(true);
        } finally {
            try {
                postgres.stop();
            } finally {
                mysql.stop();
            }
        }
    }
}
