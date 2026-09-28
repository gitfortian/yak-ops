package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.execution.executor.RealtimeSyncExecutor;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncTaskEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncTaskRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncInstanceSyncTypeContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldPersistRealtimeSyncTypeWhenCreatingInstance() throws Exception {
        DataSyncServiceImpl service = new DataSyncServiceImpl();
        AtomicReference<DataSyncInstanceEntity> captured = new AtomicReference<>();
        inject(service, "taskRepository", taskRepository(task()));
        inject(service, "instanceRepository", instanceRepository(captured));
        inject(service, "dataSourceService", dataSourceService());
        inject(service, "realtimeSyncExecutor", new NoopRealtimeSyncExecutor());

        WorkspaceContext.bind("workspace-1");
        service.runTask("task-1");

        DataSyncInstanceEntity instance = captured.get();
        assertNotNull(instance);
        assertEquals(DataSyncType.REALTIME, instance.getSyncType());
    }

    private DataSyncTaskRepository taskRepository(DataSyncTaskEntity task) {
        return (DataSyncTaskRepository) Proxy.newProxyInstance(
                DataSyncTaskRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTaskRepository.class},
                (proxy, method, args) -> {
                    if ("queryById".equals(method.getName()) && args != null && args.length == 2) {
                        return Optional.of(task);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncInstanceRepository instanceRepository(
            AtomicReference<DataSyncInstanceEntity> captured) {
        return (DataSyncInstanceRepository) Proxy.newProxyInstance(
                DataSyncInstanceRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("existsActiveByTask".equals(method.getName())) return false;
                    if ("add".equals(method.getName())) {
                        DataSyncInstanceEntity entity = (DataSyncInstanceEntity) args[0];
                        captured.set(entity);
                        return entity;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSourceService dataSourceService() {
        DataSourceVO source = dataSource("source", "source_db", "MYSQL");
        DataSourceVO target = dataSource("target", "target_db", "MYSQL");
        List<DataSourceCatalogColumnVO> columns = List.of(primaryKeyColumn());

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

    private DataSyncTaskEntity task() {
        DataSyncTaskEntity task = new DataSyncTaskEntity();
        task.setId("task-1");
        task.setWorkspaceId("workspace-1");
        task.setName("realtime-task");
        task.setSyncType(DataSyncType.REALTIME);
        task.setSourceDataSourceId("source");
        task.setSourceDatabase("source_db");
        task.setSourceTable("source_table");
        task.setTargetDataSourceId("target");
        task.setTargetDatabase("target_db");
        task.setTargetTable("target_table");
        task.setRuntimeConfig(
                "{\"checkpointIntervalSeconds\":10,\"queueCapacity\":64,\"pollBatchSize\":500,"
                        + "\"writeBatchSize\":500,\"timeoutSeconds\":30}");
        task.setDefinitionVersion(1);
        return task;
    }

    private DataSourceVO dataSource(String id, String database, String type) {
        DataSourceVO value = new DataSourceVO();
        value.setId(id);
        value.setName(id);
        value.setDbType(type);
        value.setDatabase(database);
        return value;
    }

    private DataSourceCatalogColumnVO primaryKeyColumn() {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName("id");
        column.setTypeName("BIGINT");
        column.setJdbcType(Types.BIGINT);
        column.setSize(19);
        column.setScale(0);
        column.setNullable(false);
        column.setOrdinalPosition(1);
        column.setPrimaryKey(true);
        return column;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = DataSyncServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class NoopRealtimeSyncExecutor extends RealtimeSyncExecutor {

        @Override
        public void submit(String workspaceId, String instanceId, DataSyncDefinitionSnapshotVO snapshot) {}
    }
}
