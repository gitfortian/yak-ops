package io.yak.ops.testutils;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Oracle test user, with an explicitly versioned image and bounded database readiness. */
public final class OracleDatabase extends DatabaseExtension {
    public OracleDatabase() {
        super("oracle", true);
    }

    @Override
    protected GenericContainer<?> createContainer() {
        return new GenericContainer<>(DockerImageName.parse("gvenzl/oracle-free:23.5-slim-faststart"))
                .withEnv("ORACLE_PASSWORD", PASSWORD)
                .withEnv("APP_USER", "YAK_IT")
                .withEnv("APP_USER_PASSWORD", PASSWORD)
                .withExposedPorts(1521)
                .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*\n", 1)
                        .withStartupTimeout(Duration.ofMinutes(8)));
    }

    @Override
    protected DatabaseMetadata describe(GenericContainer<?> container) {
        return new DatabaseMetadata(
                "jdbc:oracle:thin:@//" + container.getHost() + ":" + container.getMappedPort(1521)
                        + "/FREEPDB1?oracle.net.CONNECT_TIMEOUT=5000&oracle.jdbc.ReadTimeout=30000",
                "YAK_IT",
                PASSWORD,
                container.getHost(),
                container.getMappedPort(1521));
    }
}
