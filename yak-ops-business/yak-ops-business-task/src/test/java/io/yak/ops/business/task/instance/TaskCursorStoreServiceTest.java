package io.yak.ops.business.task.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.repository.task.TaskCursorRepository;
import io.yak.ops.dao.repository.task.TaskCursorRow;
import io.yak.ops.plugin.task.api.execution.TaskCursorBatch;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract tests for durable incremental window preparation. */
class TaskCursorStoreServiceTest {
    private static final String KEY = "a".repeat(64);
    private static final String BINDING = "b".repeat(64);
    private static final TaskExecutionIdentity IDENTITY = new TaskExecutionIdentity("ws", "d", 1, "i");
    private final TaskCursorStoreService service = new TaskCursorStoreService();
    private final TaskCursorRepository repository = mock(TaskCursorRepository.class);

    TaskCursorStoreServiceTest() throws Exception {
        Field field = TaskCursorStoreService.class.getDeclaredField("repository");
        field.setAccessible(true);
        field.set(service, repository);
        when(repository.frozen("ws", "i")).thenReturn(List.of());
    }

    @Test
    void initialFullLoadKeepsPreSnapshotUpperBound() {
        var proposal = TaskCursorBatch.proposal(KEY, BINDING, "TIMESTAMP", "updated_at", null,
                "2026-10-01T00:00:00", true);
        var batch = service.freeze(IDENTITY, List.of(proposal)).getFirst();
        assertTrue(batch.initialLoad());
        assertEquals("2026-10-01T00:00:00", batch.upper());
        assertEquals(null, batch.lower());
        assertEquals(0, batch.expectedVersion());
        verify(repository).insert(any(), any(), any());
        verify(repository).freeze(any(), any(), any());
    }

    @Test
    void previouslyFailedUpperBoundIsReusedNotRecomputed() {
        when(repository.lock("ws", "d", KEY)).thenReturn(Optional.of(new TaskCursorRow(
                KEY, BINDING, "2026-10-01T00:00:00", "2026-10-02T00:00:00", true, 7)));
        var proposal = TaskCursorBatch.proposal(KEY, BINDING, "TIMESTAMP", "updated_at", null,
                "2026-10-03T00:00:00", true);
        var batch = service.freeze(IDENTITY, List.of(proposal)).getFirst();
        assertFalse(batch.initialLoad());
        assertEquals("2026-10-02T00:00:00", batch.upper());
        assertEquals("2026-10-01T00:00:00", batch.lower());
        assertEquals(7, batch.expectedVersion());
    }

    @Test
    void changedBindingCannotInheritCommittedProgress() {
        when(repository.lock("ws", "d", KEY)).thenReturn(Optional.of(new TaskCursorRow(
                KEY, "c".repeat(64), "2026-10-01T00:00:00", null, true, 7)));
        var proposal = TaskCursorBatch.proposal(KEY, BINDING, "TIMESTAMP", "updated_at", null,
                "2026-10-02T00:00:00", true);
        assertThrows(IllegalStateException.class, () -> service.freeze(IDENTITY, List.of(proposal)));
    }

    @Test
    void standaloneIncrementalWithoutInitialCursorFailsClosed() {
        var proposal = TaskCursorBatch.proposal(KEY, BINDING, "INCREASING", "id", null, "100", false);
        assertThrows(IllegalArgumentException.class, () -> service.freeze(IDENTITY, List.of(proposal)));
    }
}
