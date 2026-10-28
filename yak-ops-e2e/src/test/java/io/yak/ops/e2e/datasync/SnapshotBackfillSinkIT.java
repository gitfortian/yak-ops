package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.e2e.support.BackendTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks actual MySQL CDC -> runtime -> PostgreSQL Sink convergence, rather than
 * treating successfully emitted Source records as committed target rows.
 *
 * <p>The test waits for initial snapshot completion before applying mutations.
 * LOW/HIGH window changes are covered deterministically in MySqlSnapshotBackfillIT.
 * This test does not claim to inject a crash at the durable checkpoint boundary.
 */
class SnapshotBackfillSinkIT extends BackendTestBase {
    @Test
    void primaryKeyMigrationAndDeleteAfterSnapshotConvergeAtRealSink() throws Exception {
        String instance = fixture.submit(fixture.realtime());
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));

        // A physical PK change must retract the old key and insert the new one.
        // Matching Source output counts without checking Sink rows would miss it.
        fixture.source.execute("UPDATE orders SET id=11, name='moved' WHERE id=1");
        fixture.source.execute("INSERT INTO orders VALUES (3,'three')");
        fixture.source.execute("DELETE FROM orders WHERE id=2");
        fixture.awaitTarget("orders", List.of(List.of(3L, "three"), List.of(11L, "moved")));

        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
        assertEquals(0L, fixture.claimCount(instance));
    }
}
