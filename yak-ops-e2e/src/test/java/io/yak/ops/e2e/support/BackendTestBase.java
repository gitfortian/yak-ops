package io.yak.ops.e2e.support;

import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.PostgresDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** A fresh Workspace and data namespace per test, with one shared ordinary Boot process. */
@Tag("backend-e2e")
@Timeout(300)
public abstract class BackendTestBase {
    @Order(0)
    @RegisterExtension
    static final MySqlDatabase MYSQL = MySqlDatabase.cdc();

    @Order(1)
    @RegisterExtension
    static final PostgresDatabase POSTGRES = new PostgresDatabase();

    @Order(2)
    @RegisterExtension
    static final BootExtension BOOT = new BootExtension(MYSQL::metadata);

    protected DataSyncFixture fixture;

    @BeforeEach
    void newWorkspace() throws Exception {
        fixture = new DataSyncFixture(BOOT.application(), POSTGRES.metadata());
        try {
            fixture.start();
        } catch (Exception | AssertionError failure) {
            try {
                fixture.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @AfterEach
    void stopTasksBeforeDroppingData() throws Exception {
        if (fixture == null) return;
        try {
            fixture.close();
        } catch (Exception | AssertionError failure) {
            fixture.app.diagnostics();
            try {
                fixture.app.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }
}
