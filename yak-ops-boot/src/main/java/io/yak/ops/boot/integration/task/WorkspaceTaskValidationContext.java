package io.yak.ops.boot.integration.task;

import io.yak.ops.business.datasource.runtime.DataSourceRuntimeConnection;
import io.yak.ops.business.datasource.runtime.DataSourceRuntimeService;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.validation.DataSourceInfo;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.Objects;

/** Read-only Task validation view of one authorized Workspace. */
final class WorkspaceTaskValidationContext implements TaskValidationContext {

    private final String workspaceId;
    private final DataSourceRuntimeService resources;

    WorkspaceTaskValidationContext(String workspaceId, DataSourceRuntimeService resources) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @Override
    public DataSourceInfo dataSource(String id) {
        DataSourceRuntimeConnection connection = resources.connection(workspaceId, id);
        return new DataSourceInfo(connection.datasourceType(), connection.database(), connection.schema());
    }

    @Override
    public TableSchema tableSchema(String datasourceId, TableId tableId) {
        return resources
                .table(workspaceId, datasourceId, tableId.catalog(), tableId.schema(), tableId.table())
                .schema();
    }
}
