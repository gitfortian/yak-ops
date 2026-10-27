package io.yak.ops.connector.cdc.mysql.source.config;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import io.yak.ops.connector.cdc.mysql.debezium.CheckpointOffsetBackingStore;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.TableSchema;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.debezium.config.Configuration;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.relational.RelationalTableFilters;
import java.util.function.Predicate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Immutable MySQL Binlog connection and schema contract independent of execution attempts.
 *
 * <p>Production credentials never appear in toString(), Split or Enumerator checkpoint state.
 * Exactly one Binlog reader is supported in the initial stream-only connector.
 */
public final class MySqlSourceConfig {

    private final String hostname;
    private final int port;
    private final String username;
    private final String password;
    private final int serverId;
    private final String topicPrefix;
    private final Map<TableId, TableSchema> schemas;
    private final Map<TableId, String> chunkKeyColumns;
    private final int queueCapacity;
    private final Map<String, String> connectionProperties;
    private final Map<TableId, Schema> physicalSchemas;
    private final boolean schemaEvolutionEnabled;
    private final MySqlSourceConfigFactory.SourceSettings sourceSettings;
    private final List<String> databaseList;
    private final List<String> tableList;
    private final String excludeTableList;
    private final ServerIdRange serverIdRange;
    private final StartupOptions startupOptions;
    private final int splitSize;
    private final int splitMetaGroupSize;
    private final int fetchSize;
    private final String serverTimeZone;
    private final Duration connectTimeout;
    private final int connectMaxRetries;
    private final int connectionPoolSize;
    private final double distributionFactorUpper;
    private final double distributionFactorLower;
    private final boolean includeSchemaChanges;
    private final boolean includeHeartbeatEvents;
    private final boolean includeTransactionMetadataEvents;
    private final boolean scanNewlyAddedTableEnabled;
    private final boolean closeIdleReaders;
    private final Properties jdbcProperties;
    private final boolean skipSnapshotBackfill;
    private final boolean parseOnLineSchemaChanges;
    private final boolean treatTinyInt1AsBoolean;
    private final boolean useLegacyJsonFormat;
    private final boolean assignUnboundedChunkFirst;
    private final boolean releaseSnapshotMetadataEnabled;
    private final Properties dbzProperties;

    public MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity) {
        this(hostname, port, username, password, serverId, topicPrefix, schemas, queueCapacity, Map.of());
    }

    public MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties) {
        this(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                schemas,
                queueCapacity,
                connectionProperties,
                Map.of());
    }

    public MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties,
            Map<TableId, String> chunkKeyColumns) {
        this(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                schemas,
                queueCapacity,
                connectionProperties,
                chunkKeyColumns,
                null,
                false);
    }

    MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties,
            Map<TableId, String> chunkKeyColumns,
            Map<TableId, Schema> physicalSchemas,
            boolean schemaEvolutionEnabled) {
        this(hostname, port, username, password, serverId, topicPrefix, schemas,
                queueCapacity, connectionProperties, chunkKeyColumns, physicalSchemas,
                schemaEvolutionEnabled,
                MySqlSourceConfigFactory.defaultSettings(serverId));
    }

    MySqlSourceConfig(
            String hostname,
            int port,
            String username,
            String password,
            int serverId,
            String topicPrefix,
            Map<TableId, TableSchema> schemas,
            int queueCapacity,
            Map<String, String> connectionProperties,
            Map<TableId, String> chunkKeyColumns,
            Map<TableId, Schema> physicalSchemas,
            boolean schemaEvolutionEnabled,
            MySqlSourceConfigFactory.SourceSettings settings) {
        this.schemaEvolutionEnabled = schemaEvolutionEnabled;
        this.hostname = requireText(hostname, "hostname");
        this.username = requireText(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.topicPrefix = requireText(topicPrefix, "topicPrefix");
        if (!topicPrefix.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("MySQL CDC topic prefix contains unsupported characters");
        }
        if (port < 1 || port > 65535 || serverId < 1 || queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalArgumentException("Invalid MySQL CDC port, replication server ID or queue capacity");
        }
        this.port = port;
        this.serverId = serverId;
        this.queueCapacity = queueCapacity;
        this.connectionProperties = prepareConnectionProperties(connectionProperties);
        Map<TableId, TableSchema> resolved = new LinkedHashMap<>();
        Objects.requireNonNull(schemas, "schemas").forEach((table, schema) -> {
            Objects.requireNonNull(table, "table");
            Objects.requireNonNull(schema, "schema");
            if (table.catalog() == null || table.schema() != null) {
                throw new IllegalArgumentException("MySQL CDC requires database-qualified tables");
            }
            if (schema.primaryKeys().isEmpty()) {
                ChunkUtils.getChunkKeyColumn(schema, chunkKeyColumns.get(table));
            }
            schema.columns().forEach(column -> {
                if (!column.dataType().isResolved()) {
                    throw new IllegalArgumentException("MySQL CDC requires resolved logical types");
                }
            });
            if (resolved.putIfAbsent(table, schema) != null) {
                throw new IllegalArgumentException("Duplicate MySQL CDC table");
            }
        });
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("MySQL CDC requires at least one table");
        }
        this.schemas = Collections.unmodifiableMap(resolved);
        Map<TableId, String> requestedKeys = new LinkedHashMap<>();
        Objects.requireNonNull(chunkKeyColumns, "chunkKeyColumns").forEach((table, selected) -> {
            TableSchema schema = resolved.get(table);
            if (schema == null) {
                throw new IllegalArgumentException("Unknown MySQL CDC chunk-key table: " + table);
            }
            requestedKeys.put(
                    table, ChunkUtils.getChunkKeyColumn(schema, selected).name());
        });
        this.chunkKeyColumns = Collections.unmodifiableMap(requestedKeys);
        Map<TableId, Schema> shapes = new LinkedHashMap<>();
        resolved.forEach((table, tableSchema) -> shapes.put(
                table,
                physicalSchemas == null
                        ? Schema.from(tableSchema, null, Map.of(), Map.of())
                        : Objects.requireNonNull(physicalSchemas.get(table), "Physical source schema is missing")));
        this.physicalSchemas = Collections.unmodifiableMap(shapes);
        this.sourceSettings = Objects.requireNonNull(settings, "source settings");
        this.databaseList = settings.databaseList();
        this.tableList = settings.tableList();
        this.excludeTableList = settings.excludeTableList();
        this.serverIdRange = settings.serverIdRange();
        this.startupOptions = settings.startupOptions();
        this.splitSize = settings.splitSize();
        this.splitMetaGroupSize = settings.splitMetaGroupSize();
        this.fetchSize = settings.fetchSize();
        this.serverTimeZone = settings.serverTimeZone();
        this.connectTimeout = settings.connectTimeout();
        this.connectMaxRetries = settings.connectMaxRetries();
        this.connectionPoolSize = settings.connectionPoolSize();
        this.distributionFactorUpper = settings.distributionFactorUpper();
        this.distributionFactorLower = settings.distributionFactorLower();
        this.includeSchemaChanges = settings.includeSchemaChanges();
        this.includeHeartbeatEvents = settings.includeHeartbeatEvents();
        this.includeTransactionMetadataEvents = settings.includeTransactionMetadataEvents();
        this.scanNewlyAddedTableEnabled = settings.scanNewlyAddedTableEnabled();
        this.closeIdleReaders = settings.closeIdleReaders();
        this.jdbcProperties = settings.jdbcProperties();
        this.skipSnapshotBackfill = settings.skipSnapshotBackfill();
        this.parseOnLineSchemaChanges = settings.parseOnLineSchemaChanges();
        this.treatTinyInt1AsBoolean = settings.treatTinyInt1AsBoolean();
        this.useLegacyJsonFormat = settings.useLegacyJsonFormat();
        this.assignUnboundedChunkFirst = settings.assignUnboundedChunkFirst();
        this.releaseSnapshotMetadataEnabled = settings.releaseSnapshotMetadataEnabled();
        this.dbzProperties = settings.dbzProperties();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MySQL CDC " + label + " must not be blank");
        }
        return value;
    }

    /**
     * Use a numeric UTC offset for new JDBC sessions when no time zone was explicitly configured.
     * This avoids ambiguous OS names and does not require MySQL named time zone tables.
     */
    private static Map<String, String> prepareConnectionProperties(Map<String, String> supplied) {
        Map<String, String> values = new LinkedHashMap<>();
        Objects.requireNonNull(supplied, "connectionProperties").forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]*") || value == null) {
                throw new IllegalArgumentException("Invalid MySQL JDBC connection property");
            }
            if (Set.of(
                            "user",
                            "username",
                            "password",
                            "host",
                            "hostname",
                            "port",
                            "database",
                            "database.hostname",
                            "database.user",
                            "database.password",
                            "database.port")
                    .contains(key.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "MySQL JDBC connection property must not override connection identity: " + key);
            }
            values.put(key, value);
        });
        String zone = getIgnoreCase(values, "connectionTimeZone");
        if (zone == null) {
            String forced = getIgnoreCase(values, "forceConnectionTimeZoneToSession");
            if (forced != null && !Boolean.parseBoolean(forced)) {
                throw new IllegalArgumentException(
                        "forceConnectionTimeZoneToSession=false requires an explicit connectionTimeZone");
            }
            values.put("connectionTimeZone", "+00:00");
            if (forced == null) {
                values.put("forceConnectionTimeZoneToSession", "true");
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static String getIgnoreCase(Map<String, String> values, String key) {
        for (var entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    /** Effective driver configuration, including the connection-local UTC compatibility default. */
    public Map<String, String> connectionProperties() {
        return connectionProperties;
    }

    public Map<TableId, TableSchema> schemas() {
        return schemas;
    }

    /** Explicit table-level chunk columns; absent entries default to the first physical primary-key column. */
    public Map<TableId, String> chunkKeyColumns() {
        return chunkKeyColumns;
    }

    public Map<TableId, Schema> physicalSchemas() {
        return physicalSchemas;
    }

    public boolean schemaEvolutionEnabled() {
        return schemaEvolutionEnabled;
    }

    /**
     * A checkpoint restores the same source schema and Debezium offset before the Reader starts.
     * A disabled evolution job retains its original schemas unchanged.
     */
    public MySqlSourceConfig withRuntimeState(SourceReaderContext context) {
        Objects.requireNonNull(context, "context");
        boolean enabled = context.getConfiguration().get(SchemaEvolutionOptions.ENABLED);
        Map<TableId, Schema> models = new LinkedHashMap<>(physicalSchemas);
        Map<TableId, TableSchema> shape = new LinkedHashMap<>(schemas);
        for (TableId table : schemas.keySet()) {
            var restored = context.restoredSourceSchema(table);
            if (restored.isPresent()) {
                if (!enabled) {
                    throw new IllegalStateException("Cannot restore a CDC schema version with evolution disabled");
                }
                models.put(table, restored.get());
                shape.put(table, restored.get().toTableSchema());
            }
        }
        return new MySqlSourceConfig(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                shape,
                queueCapacity,
                connectionProperties,
                chunkKeyColumns,
                models,
                enabled,
                sourceSettings);
    }

    /** Disables DDL adoption in snapshot-backfill readers, where schema changes cannot be replayed safely. */
    public MySqlSourceConfig withoutSchemaEvolution() {
        return new MySqlSourceConfig(
                hostname,
                port,
                username,
                password,
                serverId,
                topicPrefix,
                schemas,
                queueCapacity,
                connectionProperties,
                chunkKeyColumns,
                physicalSchemas,
                false,
                sourceSettings);
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public String hostname() {
        return hostname;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    /** Creates a one-shot binlog position discovery connection without exposing source credentials. */
    public BinaryLogClient newBinlogLookupClient() {
        return new BinaryLogClient(hostname, port, username, password);
    }

    /** Returns the reserved replication ID for one of at most sixteen snapshot readers. */
    public int snapshotServerId(int reader) {
        if (reader < 0 || reader >= 16 || serverId > Integer.MAX_VALUE - 16) {
            throw new IllegalArgumentException("Hybrid source requires a reserved range of seventeen replication IDs");
        }
        return serverId + reader + 1;
    }

    public int serverId() {
        return serverId;
    }

    public String topicPrefix() {
        return topicPrefix;
    }

    /**
     * Opens an independent, caller-owned JDBC connection for a hybrid Snapshot scan.
     *
     * <p>Never stores the connection in a Source definition or Checkpoint. This path uses
     * the MySQL JDBC driver provided by the Connector module.
     */
    public Connection openSnapshotConnection() throws SQLException {
        String url = "jdbc:mysql://" + hostname + ":" + port + "/";
        Properties properties = new Properties();
        properties.putAll(connectionProperties);
        properties.putIfAbsent("useCursorFetch", "true");
        properties.putIfAbsent("useServerPrepStmts", "true");
        properties.putIfAbsent("connectTimeout", "10000");
        properties.putIfAbsent("socketTimeout", "60000");
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return DriverManager.getConnection(url, properties);
    }

    /**
     * Constructs a Debezium engine that snapshots schemas only, then streams Binlog events.
     *
     * <p>A bootstrap run intentionally starts at the current offset. Older arbitrary offsets
     * require schema-history reconstruction and are not allowed without a completed checkpoint.
     */
    public Properties debeziumProperties(String session, Path schemaHistoryFile) {
        Properties properties = new Properties();
        properties.setProperty("name", "yakflow-cdc-" + topicPrefix);
        properties.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        properties.setProperty("database.hostname", hostname);
        properties.setProperty("database.port", Integer.toString(port));
        properties.setProperty("database.user", username);
        properties.setProperty("database.password", password);
        properties.setProperty("database.server.id", Integer.toString(serverId));
        // Debezium 2.7 passes driver.* values to its own JDBC connections.
        connectionProperties.forEach((key, value) -> properties.setProperty("driver." + key, value));
        properties.setProperty("topic.prefix", topicPrefix);
        Set<String> databaseNames = new TreeSet<>();
        schemas.keySet().forEach(table -> databaseNames.add(table.catalog()));
        properties.setProperty(
                "database.include.list",
                databaseNames.stream().map(Pattern::quote).collect(Collectors.joining(",")));
        properties.setProperty(
                "table.include.list",
                schemas.keySet().stream()
                        .map(table -> Pattern.quote(table.catalog()) + "\\." + Pattern.quote(table.table()))
                        .collect(Collectors.joining(",")));
        properties.setProperty("snapshot.mode", "no_data");
        properties.setProperty("include.schema.changes", Boolean.toString(schemaEvolutionEnabled));
        // Debezium's row schemas normally omit VARCHAR capacity. Propagate the
        // original column length so same-name ALTER TYPE changes can be fenced
        // before any newly widened row reaches a JDBC Sink.
        if (schemaEvolutionEnabled) {
            properties.setProperty("column.propagate.source.type", ".*");
        }
        properties.setProperty("tombstones.on.delete", "false");
        properties.setProperty("heartbeat.interval.ms", "1000");
        properties.setProperty("decimal.handling.mode", "precise");
        properties.setProperty("binary.handling.mode", "bytes");
        properties.setProperty("time.precision.mode", "adaptive_time_microseconds");
        properties.setProperty("max.batch.size", "128");
        properties.setProperty("max.queue.size", "512");
        properties.setProperty("offset.storage", CheckpointOffsetBackingStore.class.getName());
        properties.setProperty("yakflow.cdc.session.id", session);
        properties.setProperty("offset.flush.interval.ms", "0");
        properties.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        properties.setProperty(
                "schema.history.internal.file.filename",
                schemaHistoryFile.toAbsolutePath().toString());
        return properties;
    }

    @Override
    public String toString() {
        return "MySqlSourceConfig{credentials=redacted, tables=" + schemas.size() + "}";
    }
    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getDatabaseList() {
        return databaseList;
    }

    public List<String> getTableList() {
        return tableList;
    }

    public ServerIdRange getServerIdRange() {
        return serverIdRange;
    }

    public StartupOptions getStartupOptions() {
        return startupOptions;
    }

    public int getSplitSize() {
        return splitSize;
    }

    public int getSplitMetaGroupSize() {
        return splitMetaGroupSize;
    }

    public double getDistributionFactorUpper() {
        return distributionFactorUpper;
    }

    public double getDistributionFactorLower() {
        return distributionFactorLower;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public String getServerTimeZone() {
        return serverTimeZone;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public int getConnectMaxRetries() {
        return connectMaxRetries;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public boolean isIncludeSchemaChanges() {
        return includeSchemaChanges;
    }

    public boolean isIncludeHeartbeatEvents() {
        return includeHeartbeatEvents;
    }

    public boolean isIncludeTransactionMetadataEvents() {
        return includeTransactionMetadataEvents;
    }

    public boolean isScanNewlyAddedTableEnabled() {
        return scanNewlyAddedTableEnabled;
    }

    public boolean isReleaseSnapshotMetadataEnabled() {
        return releaseSnapshotMetadataEnabled;
    }

    public boolean isCloseIdleReaders() {
        return closeIdleReaders;
    }

    public boolean isParseOnLineSchemaChanges() {
        return parseOnLineSchemaChanges;
    }

    public boolean isAssignUnboundedChunkFirst() {
        return assignUnboundedChunkFirst;
    }

    public Properties getDbzProperties() {
        return dbzProperties;
    }

    public Configuration getDbzConfiguration() {
        return Configuration.from(dbzProperties);
    }

    public MySqlConnectorConfig getMySqlConnectorConfig() {
        return new MySqlConnectorConfig(getDbzConfiguration());
    }

    @Deprecated
    public RelationalTableFilters getTableFilters() {
        return getMySqlConnectorConfig().getTableFilters();
    }

    public Predicate<String> getDatabaseFilter() {
        RelationalTableFilters tableFilters = getMySqlConnectorConfig().getTableFilters();
        return (String databaseName) -> tableFilters.databaseFilter().test(databaseName);
    }

    public Predicate<io.debezium.relational.TableId> getTableFilter() {
        RelationalTableFilters tableFilters = getMySqlConnectorConfig().getTableFilters();
        return tableId -> tableFilters.dataCollectionFilter().isIncluded(tableId);
    }

    public Properties getJdbcProperties() {
        return jdbcProperties;
    }

    public Map<io.yak.ops.core.data.TableId, String> getChunkKeyColumns() {
        return chunkKeyColumns;
    }

    public boolean isSkipSnapshotBackfill() {
        return skipSnapshotBackfill;
    }

    public boolean isTreatTinyInt1AsBoolean() {
        return treatTinyInt1AsBoolean;
    }

    /** Compatibility with Flink CDC's source configuration model; YakFlow owns the runtime schema. */
    public boolean isSchemaEvolutionEnabled() {
        return schemaEvolutionEnabled;
    }
}
