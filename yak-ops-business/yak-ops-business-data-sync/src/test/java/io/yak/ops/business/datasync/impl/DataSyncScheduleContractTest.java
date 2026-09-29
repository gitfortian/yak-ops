package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.execution.executor.OfflineSyncExecutor;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleDefinition;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFire;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncScheduleContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldSaveNewOfflineScheduleDisabledAndValidateDefinition() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        AtomicReference<DataSyncScheduleEntity> added = new AtomicReference<>();
        TestScheduleEngine engine = new TestScheduleEngine();
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.UNPUBLISHED)));
        inject(service, "scheduleRepository", scheduleRepository(null, added, new AtomicReference<>()));
        inject(service, "scheduleEngine", engine);

        WorkspaceContext.bind("workspace-1");
        DataSyncScheduleVO schedule = service.saveSchedule("task-1", scheduleDto());

        assertNotNull(schedule.getId());
        assertFalse(schedule.getEnabled());
        assertEquals("0 0 2 * * ?", schedule.getCronExpression());
        assertEquals("Asia/Shanghai", schedule.getTimeZone());
        assertEquals("task-1", added.get().getTaskId());
        assertEquals(1, engine.validated.get());
        assertEquals(0, engine.scheduled.get());
    }

    @Test
    void shouldEnablePublishedOfflineScheduleAndRegisterRuntime() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncScheduleEntity schedule = schedule(false);
        AtomicReference<DataSyncScheduleEntity> updated = new AtomicReference<>();
        TestScheduleEngine engine = new TestScheduleEngine();
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.PUBLISHED)));
        inject(service, "scheduleRepository", scheduleRepository(schedule, new AtomicReference<>(), updated));
        inject(service, "scheduleEngine", engine);

        WorkspaceContext.bind("workspace-1");
        DataSyncScheduleVO enabled = service.enableSchedule("task-1");

        assertTrue(enabled.getEnabled());
        assertTrue(updated.get().getEnabled());
        assertEquals(1, engine.validated.get());
        assertEquals(1, engine.unscheduled.get());
        assertEquals(1, engine.scheduled.get());
        assertEquals("schedule-1", engine.lastDefinition.get().scheduleId());
    }

    @Test
    void shouldSkipScheduledFireWhenTaskAlreadyHasActiveInstance() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        AtomicInteger instanceAdds = new AtomicInteger();
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.PUBLISHED)));
        inject(service, "scheduleRepository", scheduleRepository(schedule(true), new AtomicReference<>(), new AtomicReference<>()));
        inject(service, "instanceRepository", instanceRepository(true, instanceAdds, new AtomicReference<>()));

        service.onFire(new DataSyncScheduleFire("schedule-1", "workspace-1", "task-1", Instant.now()));

        assertEquals(0, instanceAdds.get());
    }

    @Test
    void shouldCreateScheduleTriggeredInstanceAndSubmitOfflineExecution() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        AtomicInteger instanceAdds = new AtomicInteger();
        AtomicReference<DataSyncInstanceEntity> capturedInstance = new AtomicReference<>();
        AtomicReference<String> submittedInstanceId = new AtomicReference<>();

        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.PUBLISHED)));
        inject(service, "scheduleRepository", scheduleRepository(schedule(true), new AtomicReference<>(), new AtomicReference<>()));
        inject(service, "instanceRepository", instanceRepository(false, instanceAdds, capturedInstance));
        inject(service, "dataSourceService", dataSourceService());
        inject(service, "offlineSyncExecutor", new OfflineSyncExecutor() {
            @Override
            public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {
                submittedInstanceId.set(instanceId);
            }
        });

        service.onFire(new DataSyncScheduleFire("schedule-1", "workspace-1", "task-1", Instant.now()));

        assertEquals(1, instanceAdds.get());
        assertEquals(DataSyncTriggerType.SCHEDULE, capturedInstance.get().getTriggerType());
        assertEquals(DataSyncInstanceStatus.PENDING, capturedInstance.get().getStatus());
        assertEquals(capturedInstance.get().getId(), submittedInstanceId.get());
    }

    @Test
    void shouldRestoreEnabledScheduleFromDatabaseSourceOfTruth() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        DataSyncScheduleEntity schedule = schedule(true);
        TestScheduleEngine engine = new TestScheduleEngine();
        inject(service, "taskRepository", taskRepository(task(DataSyncTaskStatus.PUBLISHED)));
        inject(service, "scheduleRepository", enabledScheduleRepository(schedule));
        inject(service, "scheduleEngine", engine);

        service.restoreScheduleRuntime();

        assertEquals(1, engine.validated.get());
        assertEquals(1, engine.unscheduled.get());
        assertEquals(1, engine.scheduled.get());
    }

    private DataSyncScheduleDTO scheduleDto() {
        DataSyncScheduleDTO dto = new DataSyncScheduleDTO();
        dto.setCronExpression("0 0 2 * * ?");
        dto.setTimeZone("Asia/Shanghai");
        return dto;
    }

    private DataSyncTaskEntity task(DataSyncTaskStatus status) {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("task");
        task.setSyncType(DataSyncType.OFFLINE);
        task.setStatus(status);
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
        task.setDefinitionVersion(1);
        return task;
    }

    private DataSyncScheduleEntity schedule(boolean enabled) {
        DataSyncScheduleEntity schedule = new DataSyncScheduleEntity();
        schedule.setId("schedule-1");
        schedule.setWorkspaceId("workspace-1");
        schedule.setTaskId("task-1");
        schedule.setCronExpression("0 0 2 * * ?");
        schedule.setTimeZone("Asia/Shanghai");
        schedule.setEnabled(enabled);
        return schedule;
    }

    private DataSyncTaskRepository taskRepository(DataSyncTaskEntity task) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName())) return Optional.ofNullable(task);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncScheduleRepository scheduleRepository(
            DataSyncScheduleEntity existing,
            AtomicReference<DataSyncScheduleEntity> added,
            AtomicReference<DataSyncScheduleEntity> updated) {
        return (DataSyncScheduleRepository) Proxy.newProxyInstance(
                DataSyncScheduleRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncScheduleRepository.class},
                (proxy, method, args) -> {
                    if ("queryByTask".equals(method.getName()) || "queryById".equals(method.getName())) {
                        return Optional.ofNullable(existing);
                    }
                    if ("add".equals(method.getName())) {
                        DataSyncScheduleEntity entity = (DataSyncScheduleEntity) args[0];
                        added.set(entity);
                        return entity;
                    }
                    if ("update".equals(method.getName())) {
                        DataSyncScheduleEntity entity = (DataSyncScheduleEntity) args[1];
                        updated.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncScheduleRepository enabledScheduleRepository(DataSyncScheduleEntity schedule) {
        return (DataSyncScheduleRepository) Proxy.newProxyInstance(
                DataSyncScheduleRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncScheduleRepository.class},
                (proxy, method, args) -> {
                    if ("queryEnabled".equals(method.getName())) return List.of(schedule);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository instanceRepository(
            boolean active, AtomicInteger adds, AtomicReference<DataSyncInstanceEntity> captured) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("existsActiveByTask".equals(method.getName())) return active;
                    if ("add".equals(method.getName())) {
                        DataSyncInstanceEntity entity = (DataSyncInstanceEntity) args[0];
                        captured.set(entity);
                        adds.incrementAndGet();
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "source_db");
        DataSourceVO target = dataSource("target", "target_db");
        List<DataSourceCatalogColumnVO> columns = List.of(column("id"), column("name"));

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

    private DataSourceCatalogColumnVO column(String name) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName("VARCHAR");
        column.setJdbcType(Types.VARCHAR);
        column.setSize(128);
        column.setScale(0);
        column.setNullable(true);
        column.setOrdinalPosition("id".equals(name) ? 1 : 2);
        column.setPrimaryKey("id".equals(name));
        return column;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestScheduleEngine implements ScheduleEngine {

        private final AtomicInteger validated = new AtomicInteger();
        private final AtomicInteger scheduled = new AtomicInteger();
        private final AtomicInteger unscheduled = new AtomicInteger();
        private final AtomicReference<DataSyncScheduleDefinition> lastDefinition = new AtomicReference<>();

        @Override
        public void validate(DataSyncScheduleDefinition definition) {
            validated.incrementAndGet();
            lastDefinition.set(definition);
        }

        @Override
        public void schedule(DataSyncScheduleDefinition definition) {
            scheduled.incrementAndGet();
            lastDefinition.set(definition);
        }

        @Override
        public void reschedule(DataSyncScheduleDefinition definition) {
            scheduled.incrementAndGet();
            lastDefinition.set(definition);
        }

        @Override
        public void unschedule(String scheduleId) {
            unscheduled.incrementAndGet();
        }

        @Override
        public Optional<Instant> queryNextFireTime(String scheduleId) {
            return Optional.empty();
        }
    }
}
