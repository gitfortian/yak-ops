package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.e2e.support.BackendTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchSyncIT extends BackendTestBase {
    @Test
    void oneTaskTransfersTwoIndependentTables() throws Exception {
        fixture.source.execute("CREATE TABLE items (id BIGINT PRIMARY KEY,name VARCHAR(32) NOT NULL)");
        fixture.source.execute("INSERT INTO items VALUES(10,'item')");
        fixture.createTarget("orders");
        fixture.createTarget("items");
        String definition = fixture.create(
                fixture.parameters(
                        "DATABASE_OFFLINE",
                        List.of("FULL"),
                        List.of(fixture.route("orders", "orders"), fixture.route("items", "items"))),
                true);
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        fixture.assertTarget("items", List.of(List.of(10L, "item")));
        assertEquals(0, fixture.claimCount(instance));
    }

    @Test
    void writeFailureIsVisibleAndDoesNotLeaveClaims() throws Exception {
        String definition = fixture.batch();
        fixture.target.execute("ALTER TABLE " + fixture.table("orders") + " ADD CONSTRAINT reject_rows CHECK (id < 0)");
        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "FAILED");
        assertEquals(0, fixture.claimCount(instance));
        fixture.assertTarget("orders", List.of());
    }
}
