package io.yak.ops.testutils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.testcontainers.containers.GenericContainer;

/** Owns one container in the JUnit root store, or the test-class store for destructive tests. */
public abstract class DatabaseExtension implements BeforeAllCallback, TestWatcher {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.GLOBAL;
    protected static final String PASSWORD = "yak-test-only-password";
    private final String name;
    private final boolean shared;
    private Resource resource;

    protected DatabaseExtension(String name, boolean shared) {
        this.name = name;
        this.shared = shared;
    }

    protected abstract GenericContainer<?> createContainer();

    protected abstract DatabaseMetadata describe(GenericContainer<?> container);

    @Override
    public final void beforeAll(ExtensionContext context) {
        ExtensionContext owner = shared ? context.getRoot() : context;
        String key = getClass().getName() + ":" + name;
        resource = owner.getStore(NAMESPACE).getOrComputeIfAbsent(key, ignored -> start(context), Resource.class);
    }

    public final DatabaseMetadata metadata() {
        if (resource == null) throw new IllegalStateException("Database extension has not been started by JUnit");
        return resource.metadata;
    }

    private Resource start(ExtensionContext context) {
        GenericContainer<?> container = createContainer();
        String suffix = shared ? "" : "-" + context.getRequiredTestClass().getSimpleName();
        Path directory = Path.of("target", "test-artifacts", "containers", name + suffix);
        long started = System.nanoTime();
        try {
            container.start();
            long containerReady = System.nanoTime();
            DatabaseMetadata metadata = describe(container);
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            Exception last = null;
            while (System.nanoTime() < deadline) {
                try (var connection = metadata.connect();
                        var statement = connection.createStatement()) {
                    statement.setQueryTimeout(5);
                    statement.execute(metadata.url().startsWith("jdbc:oracle:") ? "SELECT 1 FROM DUAL" : "SELECT 1");
                    long sqlReady = System.nanoTime();
                    Files.createDirectories(directory);
                    Files.writeString(
                            directory.resolve("startup.json"),
                            "{\"elapsedMillis\":" + TimeUnit.NANOSECONDS.toMillis(sqlReady - started)
                                    + ",\"containerStartupMillis\":" + TimeUnit.NANOSECONDS.toMillis(containerReady - started)
                                    + ",\"sqlReadyMillis\":" + TimeUnit.NANOSECONDS.toMillis(sqlReady - containerReady)
                                    + "}");
                    return new Resource(container, metadata, directory);
                } catch (java.sql.SQLException unavailable) {
                    last = unavailable;
                    TimeUnit.MILLISECONDS.sleep(200);
                }
            }
            throw new IllegalStateException("Database did not become SQL-ready", last);
        } catch (Exception | AssertionError failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            saveLogs(container, directory);
            try {
                container.stop();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw new IllegalStateException("Cannot start disposable database " + name, failure);
        }
    }

    @Override
    public final void testFailed(ExtensionContext context, Throwable cause) {
        if (resource != null) saveLogs(resource.container, resource.directory);
    }

    private static void saveLogs(GenericContainer<?> container, Path directory) {
        try {
            Files.createDirectories(directory);
            Files.writeString(
                    directory.resolve("database.log"), container.getLogs().replace(PASSWORD, "[REDACTED]"));
        } catch (Exception unavailable) {
            // Diagnostics must not replace the original startup or test exception.
        }
    }

    private static final class Resource implements ExtensionContext.Store.CloseableResource {
        private final GenericContainer<?> container;
        private final DatabaseMetadata metadata;
        private final Path directory;

        private Resource(GenericContainer<?> container, DatabaseMetadata metadata, Path directory) {
            this.container = container;
            this.metadata = metadata;
            this.directory = directory;
        }

        @Override
        public void close() {
            saveLogs(container, directory);
            container.stop();
        }
    }
}
