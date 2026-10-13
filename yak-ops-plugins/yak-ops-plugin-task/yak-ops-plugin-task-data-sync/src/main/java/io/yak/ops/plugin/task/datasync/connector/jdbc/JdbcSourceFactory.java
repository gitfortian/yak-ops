package io.yak.ops.plugin.task.datasync.connector.jdbc;

import com.google.auto.service.AutoService;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.plugin.task.datasync.connector.SourceFactoryContext;
import io.yak.ops.plugin.task.datasync.connector.TableSourceFactory;
import io.yak.ops.plugin.task.datasync.source.JdbcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.util.List;

/** Creates a bounded JDBC Source using the authorized Datasource connection provider. */
@AutoService(TableSourceFactory.class)
public final class JdbcSourceFactory implements TableSourceFactory {

    @Override
    public String identifier() {
        return JdbcSourceParameters.TYPE;
    }

    @Override
    public Class<? extends SourceParameters> parametersType() {
        return JdbcSourceParameters.class;
    }

    @Override
    public Boundedness boundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public boolean producesChangelog() {
        return false;
    }

    @Override
    public Source<TableRecord, ?, ?> createSource(SourceParameters parameters, SourceFactoryContext context) {
        return new JdbcSource(
                context.connection().provider(),
                context.connection().jdbcUrl(),
                List.of(context.table().table()),
                context.configuration());
    }
}
