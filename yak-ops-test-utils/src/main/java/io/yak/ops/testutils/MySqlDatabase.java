package io.yak.ops.testutils;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** MySQL with distinct resource keys for JDBC, CDC and process-recovery tests. */
public final class MySqlDatabase extends DatabaseExtension {
    private final boolean binlog;

    public MySqlDatabase() {
        this("mysql", false, true);
    }

    public MySqlDatabase(String name, boolean binlog, boolean shared) {
        super(name, shared);
        this.binlog = binlog;
    }

    public static MySqlDatabase cdc() {
        return new MySqlDatabase("mysql-cdc", true, true);
    }

    @Override
    protected GenericContainer<?> createContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("mysql:8.0.39"))
                .withEnv("MYSQL_ROOT_PASSWORD", PASSWORD)
                .withEnv("MYSQL_ROOT_HOST", "%")
                .withEnv("MYSQL_DATABASE", binlog ? "yak_cdc_it" : "yak_it")
                .withExposedPorts(3306)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        if (binlog)
            container.withCommand(
                    "--server-id=5560", "--log-bin=mysql-bin", "--binlog-format=ROW", "--binlog-row-image=FULL");
        return container;
    }

    @Override
    protected DatabaseMetadata describe(GenericContainer<?> container) {
        String host = container.getHost();
        int port = container.getMappedPort(3306);
        return new DatabaseMetadata(
                "jdbc:mysql://" + host + ":" + port + "/" + (binlog ? "yak_cdc_it" : "yak_it")
                        + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000",
                "root",
                PASSWORD,
                host,
                port);
    }
}
