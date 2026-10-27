package io.yak.ops.connector.cdc.mysql.source;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.MySqlHybridEnumeratorStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.MySqlPendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridSourceEnumerator;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlSourceEnumerator;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlHybridSourceReader;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSourceReader;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * One MySQL CDC Source entry for initial Snapshot/Hybrid and latest-offset Binlog streams.
 *
 * <p>The two modes retain their existing split/state serializers and reader progress.
 * A checkpoint from one mode must never be interpreted as a checkpoint from the other.
 * No intermediate Source owns an additional checkpoint or metrics lifecycle.
 */
public final class MySqlSource<SplitT extends MySqlSplit, StateT extends PendingSplitsState>
        implements Source<TableRecord, SplitT, StateT> {

    private final StartupOptions startupOptions;
    private final MySqlCdcSourceConfig config;
    private final int snapshotChunkSize;
    private final Function<SplitEnumeratorContext<SplitT>, SplitEnumerator<SplitT, StateT>> createEnumerator;
    private final BiFunction<SplitEnumeratorContext<SplitT>, StateT, SplitEnumerator<SplitT, StateT>> restoreEnumerator;
    private final Function<SourceReaderContext, SourceReader<TableRecord, SplitT>> createReader;
    private final SimpleVersionedSerializer<SplitT> splitSerializer;
    private final SimpleVersionedSerializer<StateT> stateSerializer;

    private MySqlSource(
            StartupOptions startupOptions,
            MySqlCdcSourceConfig config,
            int snapshotChunkSize,
            Function<SplitEnumeratorContext<SplitT>, SplitEnumerator<SplitT, StateT>> createEnumerator,
            BiFunction<SplitEnumeratorContext<SplitT>, StateT, SplitEnumerator<SplitT, StateT>> restoreEnumerator,
            Function<SourceReaderContext, SourceReader<TableRecord, SplitT>> createReader,
            SimpleVersionedSerializer<SplitT> splitSerializer,
            SimpleVersionedSerializer<StateT> stateSerializer) {
        this.startupOptions = Objects.requireNonNull(startupOptions, "startupOptions");
        this.config = Objects.requireNonNull(config, "config");
        this.snapshotChunkSize = snapshotChunkSize;
        this.createEnumerator = Objects.requireNonNull(createEnumerator, "createEnumerator");
        this.restoreEnumerator = Objects.requireNonNull(restoreEnumerator, "restoreEnumerator");
        this.createReader = Objects.requireNonNull(createReader, "createReader");
        this.splitSerializer = Objects.requireNonNull(splitSerializer, "splitSerializer");
        this.stateSerializer = Objects.requireNonNull(stateSerializer, "stateSerializer");
    }

    public static MySqlCdcSourceBuilder builder() {
        return new MySqlCdcSourceBuilder();
    }

    public static MySqlSource<?, ?> create(
            MySqlCdcSourceBuilder builder, StartupOptions startupOptions, int snapshotChunkSize, TableMetrics metrics) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(startupOptions, "startupOptions");
        Objects.requireNonNull(metrics, "metrics");
        startupOptions.requireSupported();
        builder.startupOptions(startupOptions);
        return switch (startupOptions.startupMode()) {
            case INITIAL -> builder.buildHybrid(snapshotChunkSize, metrics);
            case LATEST_OFFSET -> builder.build(metrics);
            default ->
                throw new UnsupportedOperationException(
                        "MySQL CDC startup mode is not implemented: " + startupOptions.asModeString());
        };
    }

    static MySqlSource<MySqlBinlogSplit, MySqlPendingSplitsState> latestOffset(
            MySqlCdcSourceConfig config, TableMetrics metrics, StartupOptions startupOptions) {
        Objects.requireNonNull(startupOptions, "startupOptions");
        if (startupOptions.startupMode() != StartupMode.LATEST_OFFSET) {
            throw new UnsupportedOperationException("MySQL Binlog Source requires latest-offset startup");
        }
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(metrics, "metrics");
        config.schemas().keySet().forEach(metrics::register);
        String fingerprint = fingerprint(config);
        return new MySqlSource<>(
                startupOptions,
                config,
                0,
                context -> {
                    requirePhysicalPrimaryKeys(config);
                    metrics.beginSnapshot(false);
                    return new MySqlSourceEnumerator(startupOptions, context, fingerprint, null);
                },
                (context, state) -> {
                    requirePhysicalPrimaryKeys(config);
                    metrics.beginSnapshot(true);
                    return new MySqlSourceEnumerator(
                            startupOptions, context, fingerprint, Objects.requireNonNull(state, "checkpoint"));
                },
                context -> {
                    requirePhysicalPrimaryKeys(config);
                    if (context.currentParallelism() != 1 || context.getIndexOfSubtask() != 0) {
                        throw new IllegalArgumentException("MySQL Binlog Source requires exactly one SourceReader");
                    }
                    return new MySqlSourceReader(config.withRuntimeState(context), fingerprint, context, metrics);
                },
                new MySqlBinlogSplitSerializer(),
                new MySqlPendingSplitsStateSerializer());
    }

    static MySqlSource<MySqlHybridSplit, MySqlHybridEnumeratorState> initial(
            MySqlCdcSourceConfig config, int chunkSize, TableMetrics metrics, StartupOptions startupOptions) {
        Objects.requireNonNull(startupOptions, "startupOptions");
        if (startupOptions.startupMode() != StartupMode.INITIAL) {
            throw new UnsupportedOperationException("MySQL Hybrid Source requires initial startup");
        }
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(metrics, "metrics");
        config.snapshotServerId(15);
        if (chunkSize < 1 || chunkSize > 100_000) {
            throw new IllegalArgumentException("Hybrid snapshot chunk size must be between 1 and 100000");
        }
        config.schemas().forEach((table, schema) -> {
            String chunkKey = config.chunkKeyColumns().get(table);
            ChunkUtils.requireSupportedSnapshot(schema, chunkKey);
        });
        config.schemas().keySet().forEach(metrics::register);
        String fingerprint = fingerprint(config) + "-hybrid-typed-backfill-v3-" + chunkSize;
        return new MySqlSource<>(
                startupOptions,
                config,
                chunkSize,
                context -> new MySqlHybridSourceEnumerator(
                        startupOptions, context, config, fingerprint, chunkSize, null, metrics),
                (context, state) -> new MySqlHybridSourceEnumerator(
                        startupOptions,
                        context,
                        config,
                        fingerprint,
                        chunkSize,
                        Objects.requireNonNull(state, "checkpoint state"),
                        metrics),
                context -> {
                    Objects.requireNonNull(context, "context");
                    if (context.getConfiguration()
                            .get(CheckpointingOptions.CHECKPOINTING_INTERVAL)
                            .isZero()) {
                        throw new IllegalArgumentException(
                                "MySQL Hybrid Snapshot requires periodic durable checkpoints");
                    }
                    return new MySqlHybridSourceReader(config.withRuntimeState(context), context, metrics);
                },
                new MySqlHybridSplitSerializer(),
                new MySqlHybridEnumeratorStateSerializer());
    }

    public StartupOptions startupOptions() {
        return startupOptions;
    }

    public StartupMode startupMode() {
        return startupOptions.startupMode();
    }

    public MySqlCdcSourceConfig config() {
        return config;
    }

    public int snapshotChunkSize() {
        if (startupOptions.startupMode() != StartupMode.INITIAL) {
            throw new IllegalStateException("Latest-offset MySQL CDC does not use snapshot chunks");
        }
        return snapshotChunkSize;
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SplitEnumerator<SplitT, StateT> createEnumerator(SplitEnumeratorContext<SplitT> context) {
        return createEnumerator.apply(context);
    }

    @Override
    public SplitEnumerator<SplitT, StateT> restoreEnumerator(
            SplitEnumeratorContext<SplitT> context, StateT checkpointState) {
        return restoreEnumerator.apply(context, checkpointState);
    }

    @Override
    public SourceReader<TableRecord, SplitT> createReader(SourceReaderContext context) {
        return createReader.apply(context);
    }

    @Override
    public SimpleVersionedSerializer<SplitT> getSplitSerializer() {
        return splitSerializer;
    }

    @Override
    public SimpleVersionedSerializer<StateT> getEnumeratorCheckpointSerializer() {
        return stateSerializer;
    }

    private static void requirePhysicalPrimaryKeys(MySqlCdcSourceConfig config) {
        if (config.schemas().values().stream()
                .anyMatch(schema -> schema.primaryKeys().isEmpty())) {
            throw new UnsupportedOperationException(
                    "Keyless MySQL CDC is not executable until Binlog handoff and RETRACT are supported");
        }
    }

    private static String fingerprint(MySqlCdcSourceConfig config) {
        StringBuilder definition = new StringBuilder(config.hostname())
                .append('|')
                .append(config.port())
                .append('|')
                .append(config.topicPrefix())
                .append('|')
                .append(config.serverId());
        for (var entry : config.schemas().entrySet()) {
            TableId table = entry.getKey();
            TableSchema schema = entry.getValue();
            definition.append('|').append(table.catalog()).append('/').append(table.table());
            for (Column column : schema.columns()) {
                definition
                        .append('|')
                        .append(column.name())
                        .append(':')
                        .append(column.dataType().asSerializableString());
            }
            definition.append("|pk=").append(String.join(",", schema.primaryKeys()));
            String configured = config.chunkKeyColumns().get(table);
            if (configured != null) definition.append("|chunk=").append(configured);
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(definition.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
