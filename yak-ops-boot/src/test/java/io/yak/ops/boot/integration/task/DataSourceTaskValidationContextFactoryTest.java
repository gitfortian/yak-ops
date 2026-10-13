package io.yak.ops.boot.integration.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.yak.ops.business.datasource.runtime.DataSourceRuntimeConnection;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeService;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeTable;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Verifies background Task validation resolves metadata through explicit Workspace scope. */
class DataSourceTaskValidationContextFactoryTest {

    @Test
    void bindsDatasourceMetadataToWorkspaceWithoutAnHttpRequest() {
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        AtomicReference<String> observedWorkspace = new AtomicReference<>();
        DataSourceRuntimeService resources = new DataSourceRuntimeService() {
            @Override
            public DataSourceRuntimeTable table(
                    String workspaceId, String datasourceId, String database, String schemaName, String table) {
                observedWorkspace.set(workspaceId);
                assertEquals("source", datasourceId);
                return new DataSourceRuntimeTable("MYSQL", new TableId(database, schemaName, table), schema, 1);
            }

            @Override
            public DataSourceRuntimeConnection connection(String workspaceId, String datasourceId) {
                throw new UnsupportedOperationException("Connection resolution is not needed by this test");
            }
        };
        DataSourceTaskValidationContextFactory factory = new DataSourceTaskValidationContextFactory();
        inject(factory, resources);

        var first = factory.forWorkspace("workspace-a");
        var second = factory.forWorkspace("workspace-b");
        TableId tableId = new TableId("app", null, "orders");

        assertSame(schema, first.tableSchema("source", tableId));
        assertEquals("workspace-a", observedWorkspace.get());
        assertSame(schema, second.tableSchema("source", tableId));
        assertEquals("workspace-b", observedWorkspace.get());
    }

    private void inject(DataSourceTaskValidationContextFactory factory, DataSourceRuntimeService resources) {
        try {
            Field field = DataSourceTaskValidationContextFactory.class.getDeclaredField("resources");
            field.setAccessible(true);
            field.set(factory, resources);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
