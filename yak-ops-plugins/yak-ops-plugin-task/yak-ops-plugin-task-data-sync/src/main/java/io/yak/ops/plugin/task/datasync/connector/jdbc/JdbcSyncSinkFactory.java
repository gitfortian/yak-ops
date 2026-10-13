package io.yak.ops.plugin.task.datasync.connector.jdbc;

import com.google.auto.service.AutoService;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.datasync.connector.SyncSinkContext;
import io.yak.ops.plugin.task.datasync.connector.SyncSinkFactory;
import io.yak.ops.plugin.task.datasync.plan.SyncSchemaValidator;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;
import java.util.List;
import java.util.Map;

/** Resolves JDBC write semantics and constructs a target-aware YakFlow JDBC Sink. */
@AutoService(SyncSinkFactory.class)
public final class JdbcSyncSinkFactory implements SyncSinkFactory {

    @Override
    public String identifier() {
        return JdbcSinkParameters.TYPE;
    }

    @Override
    public Class<? extends SinkParameters> parametersType() {
        return JdbcSinkParameters.class;
    }

    @Override
    public void validate(SinkParameters parameters, boolean sourceProducesChangelog) {
        JdbcSinkParameters sink = (JdbcSinkParameters) parameters;
        if (sink.writeMode() == null || sink.writeMode() == DataSyncWriteMode.OVERWRITE) {
            throw new TaskPluginException("DATA_SYNC supports APPEND or UPSERT only");
        }
        if (sourceProducesChangelog && sink.writeMode() != DataSyncWriteMode.UPSERT) {
            throw new TaskPluginException("Changelog Source requires UPSERT to preserve changes");
        }
    }

    @Override
    public void validateSchema(
            SinkParameters parameters, TableSchema source, TableSchema target, boolean sourceProducesChangelog) {
        JdbcSinkParameters sink = (JdbcSinkParameters) parameters;
        validate(parameters, sourceProducesChangelog);
        SyncSchemaValidator.validate(
                source, target, sourceProducesChangelog || sink.writeMode() == DataSyncWriteMode.UPSERT);
    }

    @Override
    public Sink<TableRecord> create(SinkParameters parameters, SyncSinkContext context) {
        JdbcSinkParameters sink = (JdbcSinkParameters) parameters;
        JdbcWriteMode writeMode =
                switch (sink.writeMode()) {
                    case APPEND -> JdbcWriteMode.APPEND;
                    case UPSERT -> JdbcWriteMode.UPSERT;
                    case OVERWRITE -> throw new TaskPluginException("DATA_SYNC does not support OVERWRITE");
                };
        JdbcTableWritePlan route = new JdbcTableWritePlan(
                context.source().table(),
                context.target().table(),
                context.target().schema(),
                writeMode,
                context.source().schema(),
                Map.of());
        return JdbcSink.builder()
                .withConnectionProvider(context.connection().provider())
                .withDialect(JdbcFactoryLoader.loadDialect(context.connection().jdbcUrl()))
                .withTablePlans(List.of(route))
                .build();
    }
}
