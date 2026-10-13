package io.yak.ops.connector.jdbc.source;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.TableSourceFactory;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectorResource;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import java.util.List;

/** Provides bounded JDBC table reads using a trusted, execution-scoped connection. */
@AutoService(TableSourceFactory.class)
public final class JdbcSourceFactory implements TableSourceFactory {

    @Override
    public String factoryIdentifier() {
        return "JDBC";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder().build();
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
    public Source<TableRecord, ?, ?> createSource(TableSourceFactoryContext context) {
        if (!(context.resource() instanceof JdbcConnectorResource connection)) {
            throw new IllegalArgumentException("JDBC Source requires a JDBC connection resource");
        }
        return new JdbcSource(
                connection.provider(), connection.jdbcUrl(), List.of(context.table()), context.configuration());
    }
}
