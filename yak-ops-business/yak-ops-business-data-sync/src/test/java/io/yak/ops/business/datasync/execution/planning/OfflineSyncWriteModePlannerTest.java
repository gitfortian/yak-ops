package io.yak.ops.business.datasync.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.flow.connector.jdbc.JdbcSaveMode;
import org.junit.jupiter.api.Test;

class OfflineSyncWriteModePlannerTest {

    @Test
    void shouldMapAppendAndOverwrite() {
        assertEquals(JdbcSaveMode.APPEND, OfflineSyncExecutionPlanner.saveMode("APPEND"));
        assertEquals(JdbcSaveMode.OVERWRITE, OfflineSyncExecutionPlanner.saveMode("OVERWRITE"));
        assertEquals(JdbcSaveMode.APPEND, OfflineSyncExecutionPlanner.saveMode(null));
    }

    @Test
    void shouldRejectUpsertUntilImplemented() {
        assertThrows(IllegalArgumentException.class, () -> OfflineSyncExecutionPlanner.saveMode("UPSERT"));
    }
}
