package io.yak.ops.e2e.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.e2e.support.BootProcess;
import io.yak.ops.e2e.support.DataSyncFixture;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.PostgresDatabase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Restarts a dedicated real JVM, never the shared ordinary E2E process. */
@Tag("recovery-e2e")
@Timeout(420)
class ProcessRecoveryIT {
    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-recovery", true, false);

    @RegisterExtension
    static final PostgresDatabase POSTGRES = new PostgresDatabase();

    private BootProcess app;
    private DataSyncFixture fixture;

    @BeforeEach
    void startPrivateApplication() throws Exception {
        app = new BootProcess(MYSQL.metadata());
        try {
            app.start();
            fixture = new DataSyncFixture(app, POSTGRES.metadata());
            fixture.start();
        } catch (Exception | AssertionError failure) {
            try {
                app.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (app == null) return;
        app.diagnostics();
        try {
            if (fixture != null) fixture.close();
        } finally {
            app.close();
        }
    }

    @Test
    void forcedTerminationMarksOldExecutionReconcilingWithoutClaimingAutomaticResume() throws Exception {
        String instance = fixture.submit(fixture.realtime());
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        String workspace = fixture.api.workspace();
        app.restart(true);
        fixture.api = app.login();
        fixture.api.workspace(workspace);
        fixture.awaitState(instance, "RECONCILING");
        assertTrue(fixture.claimCount(instance) > 0, "Uncertain execution retains its claims");
    }

    @Test
    void gracefulShutdownWithoutConfirmedTerminationRetainsClaims() throws Exception {
        String instance = fixture.submit(fixture.realtime());
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        String workspace = fixture.api.workspace();
        app.restart(false);
        fixture.api = app.login();
        fixture.api.workspace(workspace);
        // Process exit is not proof that external writers stopped. The new owner must
        // reconcile uncertain execution without releasing the previous owner's claims.
        fixture.awaitState(instance, "RECONCILING");
        assertTrue(fixture.claimCount(instance) > 0);
    }
}
