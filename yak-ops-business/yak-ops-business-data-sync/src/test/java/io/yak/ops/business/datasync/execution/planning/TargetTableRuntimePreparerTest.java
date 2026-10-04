package io.yak.ops.business.datasync.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.schema.TargetTablePlanner;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncDefinitionSnapshotVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncEndpointSnapshotVO;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TargetTableRuntimePreparerTest {

    @Test
    void shouldUseExistingCompatibleTargetWithoutCreatingTable() throws Exception {
        AtomicInteger creates = new AtomicInteger();
        TargetTableRuntimePreparer preparer = preparer(dataSourceService(new AtomicBoolean(true)), creates);

        TargetTablePreparation result = preparer.prepare(snapshot(false), 30);

        assertFalse(result.targetCreated());
        assertEquals(List.of("id"), result.targetWriteSchema().primaryKeys());
        assertEquals(0, creates.get());
    }

    @Test
    void shouldRejectMissingTargetWhenAutoCreateDisabled() throws Exception {
        TargetTableRuntimePreparer preparer =
                preparer(dataSourceService(new AtomicBoolean(false)), new AtomicInteger());

        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> preparer.prepare(snapshot(false), 30));

        assertEquals(DataSyncErrorCode.TARGET_TABLE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldCreateMissingTargetAndReIntrospectBeforeUse() throws Exception {
        AtomicBoolean targetExists = new AtomicBoolean(false);
        AtomicInteger creates = new AtomicInteger();
        TargetTableRuntimePreparer preparer = preparer(dataSourceService(targetExists), creates);
        inject(preparer, "ddlExecutor", (TargetTableDdlExecutor) (connection, table, schema, timeoutSeconds) -> {
            creates.incrementAndGet();
            targetExists.set(true);
            return "CREATE TABLE";
        });

        TargetTablePreparation result = preparer.prepare(snapshot(true), 30);

        assertTrue(result.targetCreated());
        assertEquals(1, creates.get());
        assertEquals(2, result.targetWriteSchema().columnCount());
        assertEquals(List.of("id"), result.targetWriteSchema().primaryKeys());
    }

    @Test
    void shouldRejectExistingIncompatibleTargetSchema() throws Exception {
        AtomicBoolean targetExists = new AtomicBoolean(true);
        TargetTableRuntimePreparer preparer =
                preparer(dataSourceService(targetExists, true), new AtomicInteger());

        DataSyncException exception =
                assertThrows(DataSyncException.class, () -> preparer.prepare(snapshot(true), 30));

        assertEquals(DataSyncErrorCode.TARGET_SCHEMA_INCOMPATIBLE, exception.getErrorCode());
    }

    private TargetTableRuntimePreparer preparer(DataSourceService service, AtomicInteger creates) throws Exception {
        TargetTableRuntimePreparer preparer = new TargetTableRuntimePreparer();
        inject(preparer, "dataSourceService", service);
        inject(preparer, "targetTablePlanner", new TargetTablePlanner());
        inject(preparer, "ddlExecutor", (TargetTableDdlExecutor) (connection, table, schema, timeoutSeconds) -> {
            creates.incrementAndGet();
            return "CREATE TABLE";
        });
        return preparer;
    }

    private DataSourceService dataSourceService(AtomicBoolean targetExists) {
        return dataSourceService(targetExists, false);
    }

    private DataSourceService dataSourceService(AtomicBoolean targetExists, boolean incompatible) {
        List<DataSourceCatalogColumnVO> sourceColumns = List.of(
                column("id", Types.BIGINT, 19, false, 1, true, 1),
                column("name", Types.VARCHAR, 100, true, 2, false, null));
        List<DataSourceCatalogColumnVO> targetColumns = incompatible
                ? List.of(
                        column("id", Types.BIGINT, 19, false, 1, true, 1),
                        column("name", Types.VARCHAR, 50, false, 2, false, null))
                : List.of(
                        column("id", Types.BIGINT, 19, false, 1, true, 1),
                        column("name", Types.VARCHAR, 100, true, 2, false, null));

        return (DataSourceService) Proxy.newProxyInstance(
                DataSourceService.class.getClassLoader(),
                new Class<?>[] {DataSourceService.class},
                (proxy, method, args) -> {
                    String dataSourceId = args != null && args.length > 0 ? String.valueOf(args[0]) : null;
                    if ("queryCatalogTable".equals(method.getName())) {
                        return table("source_table");
                    }
                    if ("findCatalogTable".equals(method.getName())) {
                        return targetExists.get() ? Optional.of(table("target_table")) : Optional.empty();
                    }
                    if ("queryCatalogColumns".equals(method.getName())) {
                        return "source".equals(dataSourceId) ? sourceColumns : targetColumns;
                    }
                    if ("resolveRuntimeConnection".equals(method.getName())) {
                        return connection();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private DataSyncDefinitionSnapshotVO snapshot(boolean autoCreateTable) {
        DataSyncDefinitionSnapshotVO snapshot = new DataSyncDefinitionSnapshotVO();
        snapshot.setTaskId("task-1");
        snapshot.setTaskName("task");
        snapshot.setTaskVersion(1);
        snapshot.setSyncType(DataSyncType.OFFLINE.name());
        snapshot.setWriteMode(DataSyncWriteMode.APPEND.name());
        snapshot.setAutoCreateTable(autoCreateTable);
        snapshot.setSource(endpoint("source", "MYSQL", "source_db", null, "source_table"));
        snapshot.setTarget(endpoint("target", "POSTGRE_SQL", "target_db", "public", "target_table"));
        return snapshot;
    }

    private DataSyncEndpointSnapshotVO endpoint(
            String id, String type, String database, String schema, String table) {
        DataSyncEndpointSnapshotVO endpoint = new DataSyncEndpointSnapshotVO();
        endpoint.setDataSourceId(id);
        endpoint.setDataSourceName(id);
        endpoint.setDataSourceType(type);
        endpoint.setDatabase(database);
        endpoint.setSchema(schema);
        endpoint.setTable(table);
        return endpoint;
    }

    private DataSourceCatalogTableVO table(String name) {
        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
        table.setName(name);
        table.setType("TABLE");
        return table;
    }

    private DataSourceCatalogColumnVO column(
            String name,
            int jdbcType,
            Integer size,
            boolean nullable,
            int ordinal,
            boolean primaryKey,
            Integer primaryKeyPosition) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(jdbcType == Types.BIGINT ? "BIGINT" : "VARCHAR");
        column.setJdbcType(jdbcType);
        column.setSize(size);
        column.setScale(jdbcType == Types.BIGINT ? 0 : null);
        column.setNullable(nullable);
        column.setOrdinalPosition(ordinal);
        column.setPrimaryKey(primaryKey);
        column.setPrimaryKeyPosition(primaryKeyPosition);
        return column;
    }

    private DataSourceConnection connection() {
        return new DataSourceConnection() {
            @Override
            public String type() {
                return "POSTGRE_SQL";
            }

            @Override
            public String jdbcUrl() {
                return "jdbc:test";
            }

            @Override
            public String driverClassName() {
                return "test.Driver";
            }

            @Override
            public String username() {
                return "test";
            }

            @Override
            public String password() {
                return "test";
            }

            @Override
            public String database() {
                return "target_db";
            }

            @Override
            public String schema() {
                return "public";
            }

            @Override
            public Map<String, String> properties() {
                return Map.of();
            }

            @Override
            public String normalizedJson() {
                return "{}";
            }
        };
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = TargetTableRuntimePreparer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
