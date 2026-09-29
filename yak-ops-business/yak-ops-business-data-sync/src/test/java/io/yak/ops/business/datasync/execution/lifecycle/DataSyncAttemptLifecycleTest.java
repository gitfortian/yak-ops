package io.yak.ops.business.datasync.execution.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DataSyncAttemptLifecycleTest {

    @Test
    void shouldMoveFailedAttemptToRetryWaitingWhenAttemptsRemain() throws Exception {
        DataSyncAttemptLifecycle lifecycle = new DataSyncAttemptLifecycle();
        DataSyncInstanceEntity execution = execution(DataSyncInstanceStatus.RUNNING);
        AtomicReference<DataSyncAttemptStatus> attemptTarget = new AtomicReference<>();
        AtomicReference<DataSyncInstanceStatus> executionTarget = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();

        inject(lifecycle, "attemptRepository", attemptRepository(attemptTarget, new AtomicInteger()));
        inject(lifecycle, "instanceRepository", instanceRepository(execution, executionTarget, completed));

        DataSyncRetryDecision decision = lifecycle.failAttempt(
                "workspace-1",
                "execution-1",
                "attempt-1",
                1,
                DataSyncAttemptStatus.RUNNING,
                DataSyncInstanceStatus.RUNNING,
                3,
                0,
                10,
                8,
                42012,
                "failed");

        assertTrue(decision.retry());
        assertEquals(DataSyncAttemptStatus.FAILED, attemptTarget.get());
        assertEquals(DataSyncInstanceStatus.RETRY_WAITING, executionTarget.get());
        assertEquals(0, completed.get());
    }

    @Test
    void shouldFinishExecutionWhenLastAttemptFails() throws Exception {
        DataSyncAttemptLifecycle lifecycle = new DataSyncAttemptLifecycle();
        DataSyncInstanceEntity execution = execution(DataSyncInstanceStatus.RUNNING);
        AtomicReference<DataSyncAttemptStatus> attemptTarget = new AtomicReference<>();
        AtomicReference<DataSyncInstanceStatus> executionTarget = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();

        inject(lifecycle, "attemptRepository", attemptRepository(attemptTarget, new AtomicInteger()));
        inject(lifecycle, "instanceRepository", instanceRepository(execution, executionTarget, completed));

        DataSyncRetryDecision decision = lifecycle.failAttempt(
                "workspace-1",
                "execution-1",
                "attempt-3",
                3,
                DataSyncAttemptStatus.RUNNING,
                DataSyncInstanceStatus.RUNNING,
                3,
                60,
                20,
                20,
                42012,
                "failed");

        assertFalse(decision.retry());
        assertEquals(DataSyncAttemptStatus.FAILED, attemptTarget.get());
        assertEquals(DataSyncInstanceStatus.FAILED, executionTarget.get());
        assertEquals(1, completed.get());
    }

    @Test
    void shouldNotRetryCanceledExecution() throws Exception {
        DataSyncAttemptLifecycle lifecycle = new DataSyncAttemptLifecycle();
        DataSyncInstanceEntity execution = execution(DataSyncInstanceStatus.CANCELED);
        AtomicReference<DataSyncAttemptStatus> attemptTarget = new AtomicReference<>();
        AtomicInteger canceledAttempts = new AtomicInteger();
        AtomicReference<DataSyncInstanceStatus> executionTarget = new AtomicReference<>();
        AtomicInteger completed = new AtomicInteger();

        inject(lifecycle, "attemptRepository", attemptRepository(attemptTarget, canceledAttempts));
        inject(lifecycle, "instanceRepository", instanceRepository(execution, executionTarget, completed));

        DataSyncRetryDecision decision = lifecycle.failAttempt(
                "workspace-1",
                "execution-1",
                "attempt-1",
                1,
                DataSyncAttemptStatus.RUNNING,
                DataSyncInstanceStatus.RUNNING,
                3,
                60,
                1,
                1,
                42012,
                "failed");

        assertFalse(decision.retry());
        assertEquals(1, canceledAttempts.get());
        assertEquals(null, attemptTarget.get());
        assertEquals(0, completed.get());
    }

    private DataSyncInstanceEntity execution(DataSyncInstanceStatus status) {
        DataSyncInstanceEntity execution = new DataSyncInstanceEntity();
        execution.setId("execution-1");
        execution.setWorkspaceId("workspace-1");
        execution.setStatus(status);
        return execution;
    }

    private DataSyncAttemptRepository attemptRepository(
            AtomicReference<DataSyncAttemptStatus> targetStatus, AtomicInteger canceledAttempts) {
        return (DataSyncAttemptRepository) Proxy.newProxyInstance(
                DataSyncAttemptRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncAttemptRepository.class},
                (proxy, method, args) -> {
                    if ("transitionStatus".equals(method.getName())) {
                        targetStatus.set((DataSyncAttemptStatus) args[3]);
                        return true;
                    }
                    if ("cancelActiveByExecution".equals(method.getName())) {
                        canceledAttempts.incrementAndGet();
                        return 1;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository instanceRepository(
            DataSyncInstanceEntity execution,
            AtomicReference<DataSyncInstanceStatus> targetStatus,
            AtomicInteger completed) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.of(execution);
                    if ("waitForRetry".equals(method.getName())) {
                        targetStatus.set(DataSyncInstanceStatus.RETRY_WAITING);
                        execution.setStatus(DataSyncInstanceStatus.RETRY_WAITING);
                        return true;
                    }
                    if ("completeExecution".equals(method.getName())) {
                        DataSyncInstanceStatus status = (DataSyncInstanceStatus) args[3];
                        targetStatus.set(status);
                        completed.incrementAndGet();
                        execution.setStatus(status);
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncAttemptLifecycle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
