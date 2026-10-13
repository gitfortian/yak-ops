package io.yak.ops.connector.cdc.mysql.source;

import com.google.auto.service.AutoService;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.base.factory.ConnectorExecutionIdentity;
import io.yak.ops.connector.base.factory.ConnectorResourceClaim;
import io.yak.ops.connector.base.factory.TableSourceFactory;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectorResource;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.data.TableRecord;
import java.util.List;

/** Creates a hybrid MySQL Snapshot and Binlog Source from an authorized Connector resource. */
@AutoService(TableSourceFactory.class)
public final class MySqlCdcSourceFactory implements TableSourceFactory {

    @Override
    public String factoryIdentifier() {
        return "MYSQL_CDC";
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder().build();
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
    public List<ConnectorResourceClaim> exclusiveResources(
            ReadonlyConfig options, ConnectorExecutionIdentity identity) {
        return List.of(MySqlCdcReplicationIdentity.exclusiveResource(identity));
    }

    @Override
    public void validateDatasource(String datasourceType) {
        if (!"MYSQL".equals(datasourceType)) {
            throw new IllegalArgumentException("MySQL CDC requires a MySQL datasource");
        }
    }

    @Override
    public Source<TableRecord, ?, ?> createSource(TableSourceFactoryContext context) {
        if (!(context.resource() instanceof JdbcConnectorResource connection)) {
            throw new IllegalArgumentException("MySQL CDC requires a JDBC connection resource");
        }
        validateDatasource(connection.datasourceType());
        if (connection.tunneled() || connection.host() == null) {
            throw new UnsupportedOperationException("MySQL CDC requires a direct MySQL connection");
        }
        int chunkSize = context.configuration().get(MySqlCdcSourceOptions.SNAPSHOT_CHUNK_SIZE);
        int queueCapacity = context.configuration().get(MySqlCdcSourceOptions.QUEUE_CAPACITY);
        if (chunkSize < 1 || chunkSize > 100_000 || queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalArgumentException("Invalid MySQL CDC snapshot or queue capacity");
        }
        return MySqlCdcSource.builder()
                .hostname(connection.host())
                .port(connection.port())
                .username(connection.username())
                .password(connection.password())
                .serverId(MySqlCdcReplicationIdentity.serverId(context.identity()))
                .topicPrefix(MySqlCdcReplicationIdentity.topicPrefix(context.identity()))
                .table(context.table(), context.schema())
                .queueCapacity(queueCapacity)
                .buildHybrid(chunkSize);
    }
}
