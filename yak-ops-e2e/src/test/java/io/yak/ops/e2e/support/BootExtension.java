package io.yak.ops.e2e.support;

import io.yak.ops.testutils.DatabaseMetadata;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/** Shares only the ordinary application; destructive recovery tests own a separate process. */
public final class BootExtension implements BeforeAllCallback, TestWatcher {
    private final Supplier<DatabaseMetadata> database;
    private BootProcess application;

    public BootExtension(Supplier<DatabaseMetadata> database) {
        this.database = database;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        application = context.getRoot()
                .getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(
                        "yak-ops-e2e:boot",
                        key -> {
                            BootProcess boot = null;
                            try {
                                boot = new BootProcess(database.get());
                                boot.start();
                                return boot;
                            } catch (Exception failure) {
                                if (boot != null)
                                    try {
                                        boot.close();
                                    } catch (Exception cleanup) {
                                        failure.addSuppressed(cleanup);
                                    }
                                throw new IllegalStateException("Backend E2E startup failed", failure);
                            }
                        },
                        BootProcess.class);
    }

    public BootProcess application() {
        return application;
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable failure) {
        if (application != null) application.diagnostics();
    }
}
