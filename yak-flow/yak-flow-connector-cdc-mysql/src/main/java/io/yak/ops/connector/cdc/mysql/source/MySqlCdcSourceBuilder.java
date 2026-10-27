package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines MySQL CDC connection and table settings shared by both startup modes.
 *
 * <p>{@link MySqlSource} owns the runtime entry and its mode-specific checkpoint types.
 */
public final class MySqlCdcSourceBuilder {

    private String hostname;
    private int port = 3306;
    private String username;
    private String password;
    private Map<String, String> connectionProperties = Map.of();
    private int serverId;
    private String topicPrefix;
    private final Map<TableId, TableSchema> tables = new LinkedHashMap<>();
    private final Map<TableId, String> chunkKeyColumns = new LinkedHashMap<>();
    private int queueCapacity = 256;

    public MySqlCdcSourceBuilder hostname(String value) {
        hostname = value;
        return this;
    }

    public MySqlCdcSourceBuilder port(int value) {
        port = value;
        return this;
    }

    public MySqlCdcSourceBuilder username(String value) {
        username = value;
        return this;
    }

    public MySqlCdcSourceBuilder password(String value) {
        password = value;
        return this;
    }

    /** Shares MySQL JDBC settings across Snapshot and Debezium connections. */
    public MySqlCdcSourceBuilder connectionProperties(Map<String, String> value) {
        connectionProperties = Map.copyOf(Objects.requireNonNull(value, "connectionProperties"));
        return this;
    }

    /** Selects a unique MySQL replication client ID; it must not collide with other readers. */
    public MySqlCdcSourceBuilder serverId(int value) {
        serverId = value;
        return this;
    }

    /** Defines stable Debezium topic and offset namespace for checkpoint recovery. */
    public MySqlCdcSourceBuilder topicPrefix(String value) {
        topicPrefix = value;
        return this;
    }

    /** Adds a table and its exact, ordered source logical schema. */
    public MySqlCdcSourceBuilder table(TableId table, TableSchema schema) {
        return table(table, schema, null);
    }

    /** Configures the snapshot chunk column for this table independently of its physical primary key. */
    public MySqlCdcSourceBuilder table(TableId table, TableSchema schema, String chunkKeyColumn) {
        if (tables.putIfAbsent(Objects.requireNonNull(table, "table"), Objects.requireNonNull(schema, "schema"))
                != null) {
            throw new IllegalArgumentException("Duplicate MySQL CDC table");
        }
        if (chunkKeyColumn != null) {
            chunkKeyColumns.put(table, chunkKeyColumn);
        }
        return this;
    }

    public MySqlCdcSourceBuilder queueCapacity(int value) {
        queueCapacity = value;
        return this;
    }

    /** Creates the initial Snapshot + Binlog Source from the same validated connection plan. */
    public MySqlSource<MySqlHybridSplit, MySqlHybridEnumeratorState> buildHybrid(int chunkSize) {
        return MySqlSource.initial(config(), chunkSize, new TableMetrics());
    }

    /** Builds the shared snapshot and Binlog source with generation-scoped measurements. */
    public MySqlSource<MySqlHybridSplit, MySqlHybridEnumeratorState> buildHybrid(
            int chunkSize, TableMetrics metrics) {
        return MySqlSource.initial(config(), chunkSize, metrics);
    }

    /** Builds a reusable Source definition without opening a database connection. */
    public MySqlSource<MySqlBinlogSplit, MySqlPendingSplitsState> build() {
        return build(new TableMetrics());
    }

    /** Shares pipeline measurements with a standalone Binlog source. */
    public MySqlSource<MySqlBinlogSplit, MySqlPendingSplitsState> build(TableMetrics metrics) {
        return MySqlSource.latestOffset(config(), metrics);
    }

    private MySqlCdcSourceConfig config() {
        return new MySqlCdcSourceConfig(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                tables,
                queueCapacity,
                connectionProperties,
                chunkKeyColumns);
    }
}
