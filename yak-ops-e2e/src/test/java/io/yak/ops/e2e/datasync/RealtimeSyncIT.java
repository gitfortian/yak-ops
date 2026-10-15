package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.e2e.support.BackendTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeSyncIT extends BackendTestBase {
    @Test
    void snapshotContinuesWithBinlogAndCancellationStopsTheJob() throws Exception {
        String instance = fixture.submit(fixture.realtime());
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        fixture.source.execute("UPDATE orders SET name='changed' WHERE id=1");
        fixture.source.execute("DELETE FROM orders WHERE id=2");
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        fixture.awaitTarget("orders", List.of(List.of(1L, "changed"), List.of(3L, "three")));
        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
        assertEquals(0, fixture.claimCount(instance));
    }
}
