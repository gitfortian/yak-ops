package io.yak.ops.boot.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/** Each suite owns isolated databases, one real Boot process and a disposable Workspace. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BackendE2ETest {
    protected BackendE2EEnvironment env;
    protected TaskApiClient api;
    protected TaskFixtures tasks;

    @BeforeAll
    void beforeBackendE2E() {
        env = new BackendE2EEnvironment();
        try {
            env.start();
            api = env.login();
            tasks = new TaskFixtures(env, api);
        } catch (Throwable failure) {
            env.close();
            throw failure;
        }
    }

    @AfterAll
    void afterBackendE2E() {
        if (env != null) env.close();
    }
}
