package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.execution.executor.OfflineSyncExecutor;
import io.yak.ops.business.datasync.execution.executor.RealtimeSyncExecutor;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncAttemptLifecycle;
import io.yak.ops.business.datasync.execution.lifecycle.DataSyncRetryDecision;
import io.yak.ops.business.datasync.execution.realtime.RealtimeSyncStateManager;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFire;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * v1.1 Automation & Recovery 发布级业务验收。
 *
 * <p>真实 JDBC / CDC 数据库链路由现有 Cross-Database Acceptance 负责；本类聚焦产品层
 * Schedule / Retry / Desired State / Auto Recovery 语义。</p>
 *
 * @author weifuwan
 * @since 2026-09-30
 */
class DataSyncAutomationAcceptanceIT {

    @Test
    void shouldCreateOneScheduledExecutionAndSkipSecondFireWhileActive() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        AtomicBoolean active = new AtomicBoolean(false);
        AtomicInteger adds = new AtomicInteger();
        AtomicReference<DataSyncInstanceEntity> created = new AtomicReference<>();

        inject(service, "taskRepository", singleTaskRepository(offlineTask()));
        inject(service, "scheduleRepository", scheduleRepository(schedule(true)));
        inject(service, "instanceRepository", activeInstanceRepository(active, adds, created));
        inject(service, "dataSourceService", dataSourceService());
        inject(service, "offlineSyncExecutor", new NoopOfflineSyncExecutor());

        DataSyncScheduleFire fire = new DataSyncScheduleFire(
                "schedule-1", "workspace-1", "task-offline", Instant.parse("2026-10-01T02:00:00Z"));

        service.onFire(fire);
        service.onFire(fire);

        assertEquals(1, adds.get());
        assertNotNull(created.get());
        assertEquals(DataSyncTriggerType.SCHEDULE, created.get().getTriggerType());
        assertEquals(DataSyncInstanceStatus.PENDING, created.get().getStatus());
    }

    @Test
    void shouldIgnoreScheduleFireAfterScheduleIsDisabled() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        AtomicInteger adds = new AtomicInteger();

        inject(service, "scheduleRepository", scheduleRepository(schedule(false)));
        inject(
                service,
                "instanceRepository",
                activeInstanceRepository(new AtomicBoolean(false), adds, new AtomicReference<>()));

        service.onFire(new DataSyncScheduleFire(
                "schedule-1", "workspace-1", "task-offline", Instant.parse("2026-10-01T02:00:00Z")));

        assertEquals(0, adds.get());
    }

    @Test
    void shouldApplyRetryBackoffAndFailOnlyAfterMaxAttempts() throws Exception {
        DataSyncAttemptLifecycle lifecycle = new DataSyncAttemptLifecycle();
        DataSyncInstanceEntity execution = execution(DataSyncInstanceStatus.RUNNING);
        AtomicReference<DataSyncInstanceStatus> executionStatus =
                new AtomicReference<>(DataSyncInstanceStatus.RUNNING);
        AtomicInteger attemptFailures = new AtomicInteger();

        inject(lifecycle, "attemptRepository", attemptRepository(attemptFailures));
        inject(lifecycle, "instanceRepository", retryInstanceRepository(execution, executionStatus));

        LocalDateTime before = LocalDateTime.now();
        DataSyncRetryDecision first = lifecycle.failAttempt(
                "workspace-1",
                "execution-1",
                "attempt-1",
                1,
                DataSyncAttemptStatus.RUNNING,
                DataSyncInstanceStatus.RUNNING,
                2,
                5,
                10,
                8,
                42012,
                "temporary failure");

        assertTrue(first.retry());
        assertEquals(DataSyncInstanceStatus.RETRY_WAITING, executionStatus.get());
        assertNotNull(first.nextRetryTime());
        assertTrue(Duration.between(before, first.nextRetryTime()).toMillis() >= 4500);

        execution.setStatus(DataSyncInstanceStatus.RUNNING);
        executionStatus.set(DataSyncInstanceStatus.RUNNING);
        DataSyncRetryDecision second = lifecycle.failAttempt(
                "workspace-1",
                "execution-1",
                "attempt-2",
                2,
                DataSyncAttemptStatus.RUNNING,
                DataSyncInstanceStatus.RUNNING,
                2,
                5,
                20,
                18,
                42012,
                "final failure");

        assertFalse(second.retry());
        assertEquals(DataSyncInstanceStatus.FAILED, executionStatus.get());
        assertEquals(2, attemptFailures.get());
    }

    @Test
    void shouldNeverRetryCanceledExecution() throws Exception {
        DataSyncAttemptLifecycle lifecycle = new DataSyncAttemptLifecycle();
        DataSyncInstanceEntity execution = execution(DataSyncInstanceStatus.CANCELED);
        AtomicInteger canceledAttempts = new AtomicInteger();

        inject(lifecycle, "attemptRepository", canceledAttemptRepository(canceledAttempts));
        inject(lifecycle, "instanceRepository", queryOnlyInstanceRepository(execution));

        DataSyncRetryDecision decision = lifecycle.failAttempt(
                "workspace-1",
                "execution-1",
                "attempt-1",
                1,
                DataSyncAttemptStatus.RUNNING,
                DataSyncInstanceStatus.RUNNING,
                3,
                30,
                1,
                1,
                42012,
                "canceled");

        assertFalse(decision.retry());
        assertEquals(1, canceledAttempts.get());
    }

    @Test
    void shouldAutoRecoverRealtimeWithSameTaskVersionAndCdcStateIdentity() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncTaskEntity task = realtimeTask();
        AtomicReference<DataSyncInstanceEntity> created = new AtomicReference<>();

        inject(service, "taskRepository", desiredRunningRepository(task));
        inject(
                service,
                "instanceRepository",
                activeInstanceRepository(new AtomicBoolean(false), new AtomicInteger(), created));
        inject(service, "dataSourceService", dataSourceService());
        inject(service, "realtimeSyncExecutor", new NoopRealtimeSyncExecutor());

        service.restoreRealtimeDesiredState();

        assertNotNull(created.get());
        assertEquals(DataSyncTriggerType.AUTO_RECOVERY, created.get().getTriggerType());
        assertEquals(7, created.get().getTaskVersion());

        RealtimeSyncStateManager stateManager = new RealtimeSyncStateManager();
        assertEquals(
                "workspace-1/task-realtime/v7",
                stateManager.stateKey(
                        created.get().getWorkspaceId(), created.get().getTaskId(), created.get().getTaskVersion()));
    }

    private DataSyncTaskEntity offlineTask() {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-offline");
        task.setWorkspaceId("workspace-1");
        task.setName("offline-automation");
        task.setSyncType(DataSyncType.OFFLINE);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.setDesiredState(DataSyncDesiredState.STOPPED);
        task.setWriteMode(DataSyncWriteMode.APPEND);
        task.setSourceDataSourceId("source");
        task.setSourceDatabase("source_db");
        task.setSourceTable("source_table");
        task.setTargetDataSourceId("target");
        task.setTargetDatabase("target_db");
        task.setTargetTable("target_table");
        task.setRuntimeConfig(
                "{\"fetchSize\":500,\"readBatchSize\":500,\"writeBatchSize\":500,\"splitSize\":null,"
                        + "\"sourceParallelism\":1,\"timeoutSeconds\":30}");
        task.setRetryPolicy("{\"maxAttempts\":1,\"backoffSeconds\":60}");
        task.setDefinitionVersion(3);
        return task;
    }

    private DataSyncTaskEntity realtimeTask() {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-realtime");
        task.setWorkspaceId("workspace-1");
        task.setName("realtime-automation");
        task.setSyncType(DataSyncType.REALTIME);
        task.setStatus(DataSyncTaskStatus.PUBLISHED);
        task.setDesiredState(DataSyncDesiredState.RUNNING);
        task.setWriteMode(DataSyncWriteMode.APPEND);
        task.setSourceDataSourceId("source");
        task.setSourceDatabase("source_db");
        task.setSourceTable("source_table");
        task.setTargetDataSourceId("target");
        task.setTargetDatabase("target_db");
        task.setTargetTable("target_table");
        task.setRuntimeConfig(
                "{\"checkpointIntervalSeconds\":10,\"queueCapacity\":64,\"pollBatchSize\":500,"
                        + "\"writeBatchSize\":500,\"timeoutSeconds\":30}");
        task.setRetryPolicy("{\"maxAttempts\":1,\"backoffSeconds\":60}");
        task.setDefinitionVersion(7);
        return task;
    }

    private DataSyncScheduleEntity schedule(boolean enabled) {
        DataSyncScheduleEntity schedule = new DataSyncScheduleEntity();
        schedule.setId("schedule-1");
        schedule.setWorkspaceId("workspace-1");
        schedule.setTaskId("task-offline");
        schedule.setCronExpression("0 0 2 * * ?");
        schedule.setTimeZone("Asia/Shanghai");
        schedule.setEnabled(enabled);
        return schedule;
    }

    private DataSyncTaskRepository singleTaskRepository(DataSyncTaskEntity task) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.of(task);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncTaskRepository desiredRunningRepository(DataSyncTaskEntity task) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryRealtimeDesiredRunning".equals(method.getName())) return List.of(task);
                    if ("queryById".equals(method.getName())) return Optional.of(task);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncScheduleRepository scheduleRepository(DataSyncScheduleEntity schedule) {
        return (DataSyncScheduleRepository) Proxy.newProxyInstance(
                DataSyncScheduleRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncScheduleRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.of(schedule);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository activeInstanceRepository(
            AtomicBoolean active,
            AtomicInteger adds,
            AtomicReference<DataSyncInstanceEntity> created) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("existsActiveByTask".equals(method.getName())) return active.get();
                    if ("add".equals(method.getName())) {
                        DataSyncInstanceEntity entity = (DataSyncInstanceEntity) args[0];
                        created.set(entity);
                        adds.incrementAndGet();
                        active.set(true);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncAttemptRepository attemptRepository(AtomicInteger failures) {
        return (DataSyncAttemptRepository) Proxy.newProxyInstance(
                DataSyncAttemptRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncAttemptRepository.class},
                (proxy, method, args) -> {
                    if ("transitionStatus".equals(method.getName())) {
                        if (args[3] == DataSyncAttemptStatus.FAILED) failures.incrementAndGet();
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncAttemptRepository canceledAttemptRepository(AtomicInteger cancels) {
        return (DataSyncAttemptRepository) Proxy.newProxyInstance(
                DataSyncAttemptRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncAttemptRepository.class},
                (proxy, method, args) -> {
                    if ("cancelActiveByExecution".equals(method.getName())) {
                        cancels.incrementAndGet();
                        return 1;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository retryInstanceRepository(
            DataSyncInstanceEntity execution, AtomicReference<DataSyncInstanceStatus> status) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.of(execution);
                    if ("waitForRetry".equals(method.getName())) {
                        execution.setStatus(DataSyncInstanceStatus.RETRY_WAITING);
                        status.set(DataSyncInstanceStatus.RETRY_WAITING);
                        return true;
                    }
                    if ("completeExecution".equals(method.getName())) {
                        DataSyncInstanceStatus target = (DataSyncInstanceStatus) args[3];
                        execution.setStatus(target);
                        status.set(target);
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository queryOnlyInstanceRepository(DataSyncInstanceEntity execution) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.of(execution);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceEntity execution(DataSyncInstanceStatus status) {
        DataSyncInstanceEntity execution = new DataSyncInstanceEntity();
        execution.setId("execution-1");
        execution.setWorkspaceId("workspace-1");
        execution.setStatus(status);
        return execution;
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
        List<DataSourceCatalogColumnVO> columns = List.of(primaryKeyColumn("id"), column("name"));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    if ("queryDataSource".equals(method.getName())) {
                        return "source".equals(args[0]) ? source : target;
                    }
                    if ("queryCatalogColumns".equals(method.getName())) return columns;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceVO dataSource(String id, String database) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType("MYSQL");
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogColumnVO primaryKeyColumn(String name) {
        DataSourceCatalogColumnVO column = column(name);
        column.setPrimaryKey(true);
        column.setNullable(false);
        return column;
    }

    private DataSourceCatalogColumnVO column(String name) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName("BIGINT");
        column.setJdbcType(Types.BIGINT);
        column.setSize(19);
        column.setScale(0);
        column.setNullable(true);
        column.setOrdinalPosition("id".equals(name) ? 1 : 2);
        column.setPrimaryKey(false);
        return column;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class NoopOfflineSyncExecutor extends OfflineSyncExecutor {

        @Override
        public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {}
    }

    private static final class NoopRealtimeSyncExecutor extends RealtimeSyncExecutor {

        @Override
        public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {}
    }
}
