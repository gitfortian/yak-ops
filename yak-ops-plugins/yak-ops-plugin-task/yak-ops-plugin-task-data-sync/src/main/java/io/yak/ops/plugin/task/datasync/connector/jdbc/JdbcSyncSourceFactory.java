package io.yak.ops.plugin.task.datasync.connector.jdbc;

import com.google.auto.service.AutoService;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.plugin.task.datasync.connector.SyncSourceContext;
import io.yak.ops.plugin.task.datasync.connector.SyncSourceFactory;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.util.List;

/** Creates a bounded JDBC Source using the authorized Datasource connection provider. */
@AutoService(SyncSourceFactory.class)
public final class JdbcSyncSourceFactory implements SyncSourceFactory {

    @Override
    public String identifier() {
        return "JDBC";
    }

    @Override
    public Class<? extends SourceParameters> parametersType() {
        return JdbcSourceParameters.class;
    }

    @Override
    public boolean producesChangelog() {
        return false;
    }

    @Override
    public Source<TableRecord, ?, ?> create(SourceParameters parameters, SyncSourceContext context) {
        return new JdbcSource(
                context.connection().provider(),
                context.connection().jdbcUrl(),
                List.of(context.table().table()),
                context.configuration());
    }
}
