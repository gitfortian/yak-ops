package io.yak.ops.plugin.task.datasync.connector.cdc;

import com.google.auto.service.AutoService;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.util.List;
import io.yak.ops.plugin.task.datasync.connector.SyncSourceContext;
import io.yak.ops.plugin.task.datasync.connector.SyncSourceFactory;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;

/** Creates the initial Snapshot and continuous Binlog Source for an authorized MySQL table. */
@AutoService(SyncSourceFactory.class)
public final class MySqlCdcSyncSourceFactory implements SyncSourceFactory {

    @Override
    public String identifier() {
        return MySqlCdcSourceParameters.TYPE;
    }

    @Override
    public Class<? extends SourceParameters> parametersType() {
        return MySqlCdcSourceParameters.class;
    }

    @Override
    public Boundedness boundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public boolean producesChangelog() {
        return true;
    }

    @Override
    public List<ExecutionResource> exclusiveResources(
            SourceParameters parameters, TaskExecutionIdentity identity) {
        return List.of(MySqlCdcReplicationIdentity.exclusiveResource(identity));
    }

    @Override
    public void validateDatasource(String datasourceType) {
        if (!"MYSQL".equals(datasourceType)) {
            throw new TaskPluginException("MySQL CDC requires a MySQL datasource");
        }
    }

    @Override
    public Source<TableRecord, ?, ?> create(SourceParameters parameters, SyncSourceContext context) {
        var connection = context.connection();
        validateDatasource(connection.datasourceType());
        if (connection.tunneled() || connection.host() == null) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }

        return MySqlCdcSource.builder()
                .hostname(connection.host())
                .port(connection.port())
                .username(connection.username())
                .password(connection.password())
                .serverId(MySqlCdcReplicationIdentity.serverId(context.identity()))
                .topicPrefix(MySqlCdcReplicationIdentity.topicPrefix(context.identity()))
                .table(context.table().table(), context.table().schema())
                .queueCapacity(context.runtimeOptions().cdcQueueCapacity())
                .buildHybrid(context.runtimeOptions().snapshotChunkSize());
    }
}
