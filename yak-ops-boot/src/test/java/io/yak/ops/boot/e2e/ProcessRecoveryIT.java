package io.yak.ops.boot.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Reproduces the real single-owner lock and forced-JVM restart reconciliation path. */
class ProcessRecoveryIT extends BackendE2ETest {
    @Test
    void secondProcessCannotOwnTheSameLocalState() {
        Process contender = env.startContender();
        try {
            BackendE2EEnvironment.until("embedded execution owner rejected", Duration.ofSeconds(45),
                    () -> !contender.isAlive(), ready -> ready);
            assertTrue(env.rejectedOwnerLog().contains(
                    "Another Yak Ops embedded executor owns the local state directory"));
        } finally {
            contender.destroyForcibly();
            try {
                contender.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interruption);
            }
        }
    }

    @Test
    void crashedRuntimeReconcilesWithoutReleasingClaimsOrRewritingPinnedHistory() {
        String completedTask = tasks.single(false, false);
        String completed = tasks.run(completedTask, "FINISHED");
        String report = api.call("GET", "/api/v1/task-instances/" + completed)
                .path("executionReport").asText();
        String continuous = tasks.single(true, false);
        String active = tasks.run(continuous, "RUNNING");
        assertEquals("2", tasks.claimCount(active));
        String checkpoint = active;
        BackendE2EEnvironment.until("CDC durable checkpoint", Duration.ofSeconds(110), () -> {
            try {
                var file = env.stateDirectory().resolve(checkpoint).resolve("checkpoint.bin");
                return Files.isRegularFile(file) ? Files.size(file) : 0L;
            } catch (Exception unavailable) {
                return 0L;
            }
        }, count -> count > 32);

        String workspace = api.workspace();
        env.restart(true);
        api = env.login();
        api.workspace(workspace);
        JsonNode uncertain = BackendE2EEnvironment.until("crash reconciliation", Duration.ofSeconds(80),
                () -> api.call("GET", "/api/v1/task-instances/" + active),
                row -> "RECONCILING".equals(row.path("status").asText()));
        assertEquals("2", tasks.claimCount(active));
        assertEquals("EXECUTION_OWNER_UNCERTAIN", uncertain.path("failureCode").asText());
        assertFalse(uncertain.path("runtimeId").asText().isEmpty());
        api.submit(continuous, UUID.randomUUID().toString(), 409);
        assertEquals(report, api.call("GET", "/api/v1/task-instances/" + completed)
                .path("executionReport").asText());
        assertTrue(api.call("GET", "/api/v1/task-instances/" + active + "/logs?limit=200")
                .path("content").asText().contains("YakFlow Job started"));
    }
}
