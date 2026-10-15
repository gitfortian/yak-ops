package io.yak.ops.testutils;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Shared PostgreSQL process; each test owns its tables or schema. */
public final class PostgresDatabase extends DatabaseExtension {
    public PostgresDatabase() {
        super("postgres", true);
    }

    @Override
    protected GenericContainer<?> createContainer() {
        return new GenericContainer<>(DockerImageName.parse("postgres:16.4"))
                .withEnv("POSTGRES_DB", "yak_it")
                .withEnv("POSTGRES_USER", "yak")
                .withEnv("POSTGRES_PASSWORD", PASSWORD)
                .withExposedPorts(5432)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
    }

    @Override
    protected DatabaseMetadata describe(GenericContainer<?> container) {
        return new DatabaseMetadata(
                "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432)
                        + "/yak_it?connectTimeout=5&socketTimeout=30",
                "yak",
                PASSWORD,
                container.getHost(),
                container.getMappedPort(5432));
    }
}
