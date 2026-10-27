package io.yak.ops.connector.cdc.mysql.debezium.task;

import io.debezium.embedded.Connect;
import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.debezium.CheckpointOffsetBackingStore;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.debezium.task.context.StatefulTaskContext;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.data.TableId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns one Debezium engine and bounded handover queue for a MySQL Binlog split.
 *
 * <p>The engine never writes records or checkpoint offsets into the Runtime mailbox.
 * It only enqueues detached complete change events. Its private volatile offsets may run
 * ahead of emission, but are never promoted to a completed YakFlow checkpoint.
 */
public final class MySqlBinlogSplitReadTask implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MySqlBinlogSplitReadTask.class);

    private final StatefulTaskContext context;

    private DebeziumEngine<RecordChangeEvent<SourceRecord>> engine;
    private Thread worker;
    private Path directory;
    private Path historyPath;
    private String sessionId;
    private volatile boolean stopped;
    private boolean started;
    private DebeziumOffset lastQueuedOffset;
    private boolean awaitingSchemaSnapshot;
    private final Set<TableId> bootstrapTables = new HashSet<>();

    public MySqlBinlogSplitReadTask(MySqlSourceConfig config) {
        this(config, config.serverId());
    }

    /** Uses a distinct replication connection while retaining the source partition and table contract. */
    public MySqlBinlogSplitReadTask(MySqlSourceConfig config, int serverId) {
        context = new StatefulTaskContext(Objects.requireNonNull(config, "config"), serverId);
    }

    private MySqlSourceConfig getSourceConfig() {
        return context.getSourceConfig();
    }

    /** Starts the engine, optionally seeding its offset and Schema History from a checkpoint. */
    public void start(MySqlBinlogSplit split) throws IOException {
        if (started || stopped) {
            throw new IllegalStateException("MySQL Binlog engine cannot start twice");
        }
        context.configure(split);
        started = true;
        lastQueuedOffset = context.restoredOffset();
        awaitingSchemaSnapshot = lastQueuedOffset == null;
        directory = Files.createTempDirectory("yakflow-binlog-");
        historyPath = directory.resolve("schema-history.dat");
        byte[] history = context.restoredSchemaHistory();
        if (history.length != 0) {
            Files.write(historyPath, history);
        }
        String connectorName = "yakflow-cdc-" + getSourceConfig().topicPrefix();
        sessionId = CheckpointOffsetBackingStore.register(connectorName, context.restoredOffset());
        var properties = getSourceConfig().debeziumProperties(sessionId, historyPath);
        properties.setProperty("database.server.id", Integer.toString(context.getServerId()));
        // Snapshot readers share a logical source but must own distinct JMX registrations.
        properties.setProperty("custom.metric.tags", "reader=" + context.getServerId());
        engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(properties)
                .notifying(this::handleBatch)
                .using((success, message, error) -> {
                    if (!success && !stopped) {
                        // Preserve the original driver exception instead of only reporting an engine stop.
                        context.recordFailure(error != null
                                        ? error
                                        : new IllegalStateException("Debezium MySQL connector failed: " + message));
                    }
                })
                .build();
        worker = Thread.ofVirtual().name("yakflow-mysql-debezium").start(() -> {
            try {
                engine.run();
                if (!stopped) {
                    context.recordFailure(new IllegalStateException("Debezium MySQL Binlog engine stopped unexpectedly"));
                }
            } catch (Throwable error) {
                if (!stopped) {
                    context.recordFailure(error);
                }
            }
        });
    }

    private void handleBatch(
            java.util.List<RecordChangeEvent<SourceRecord>> events,
            DebeziumEngine.RecordCommitter<RecordChangeEvent<SourceRecord>> committer)
            throws InterruptedException {
        for (RecordChangeEvent<SourceRecord> event : events) {
            if (stopped) {
                throw new InterruptedException("MySQL Binlog reader was stopped");
            }
            try {
                SourceRecord source = event.record();
                if (awaitingSchemaSnapshot) {
                    // Debezium's no_data snapshot emits CREATE table metadata before it completes.
                    // The forced heartbeat is emitted only after postSnapshot(), with the
                    // completed snapshot offset. Earlier records must never anchor Hybrid.
                    if (isHeartbeat(source)) {
                        if (getSourceConfig().schemaEvolutionEnabled()
                                && !bootstrapTables.containsAll(getSourceConfig().schemas().keySet())) {
                            throw new IllegalStateException(
                                    "MySQL CDC initial schema snapshot did not register all selected tables: "
                                            + getSourceConfig().schemas().keySet().stream()
                                                    .filter(table -> !bootstrapTables.contains(table))
                                                    .toList());
                        }
                        if (snapshotHistory().length == 0) {
                            throw new IllegalStateException("MySQL CDC initial schema snapshot has no Schema History");
                        }
                        BinlogEvent anchor = Objects.requireNonNull(context.converter().convert(source), "snapshot heartbeat");
                        enqueue(anchor);
                        lastQueuedOffset = anchor.offset();
                        awaitingSchemaSnapshot = false;
                        LOG.info(
                                "MySQL CDC initial schema snapshot completed, tables={}",
                                getSourceConfig().schemas().size());
                    } else {
                        context.converter().applyInitialSchemaTables(source, bootstrapTables);
                    }
                } else {
                    TableId changed = context.converter().schemaMismatch(source);
                    if (changed != null) {
                        // Some MySQL DDLs carry no tableChanges. Only the Debezium row
                        // schema at THIS Binlog position may prove a safe change.
                        // Current JDBC catalog metadata could already describe later DDL.
                        // Hold the preceding offset until SchemaCoordinator accepts the
                        // transition, so recovery cannot skip the discovering row.
                        BinlogEvent transition = context.converter().reconcileRowSchema(source, lastQueuedOffset);
                        LOG.info("MySQL CDC row schema reconciliation: table={}", changed);
                        enqueue(transition);
                    }
                    BinlogEvent converted = context.converter().convert(source);
                    if (converted != null) {
                        if (converted.schemaChange() != null) {
                            LOG.info(
                                    "MySQL CDC independent DDL event: table={}",
                                    converted.schemaChange().tableId());
                        }
                        enqueue(converted);
                        lastQueuedOffset = converted.offset();
                    }
                }
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Exception error) {
                context.recordFailure(error);
                throw new IllegalStateException("MySQL CDC event conversion failed", error);
            }
            // The engine may stage this offset, but the YakFlow checkpoint uses only
            // the last event delivered by the owning Source mailbox.
            committer.markProcessed(event);
        }
        committer.markBatchFinished();
    }

    private static boolean isHeartbeat(SourceRecord source) {
        return source.topic() != null && source.topic().startsWith("__debezium-heartbeat.");
    }

    private void enqueue(BinlogEvent record) throws InterruptedException {
        while (!stopped && !context.offer(record)) {
            // Bounded handover: preserve strict schema-event / row order.
        }
        if (stopped) {
            throw new InterruptedException("MySQL Binlog reader was stopped");
        }
    }

    public BinlogEvent poll() throws InterruptedException {
        return context.poll();
    }

    /**
     * Copies the local Schema History for the Reader's completed-checkpoint candidate.
     *
     * <p>Debezium Schema History is essential for
     * resuming Debezium at a saved Binlog offset without rebuilding an unrelated schema.
     */
    public byte[] snapshotHistory() throws IOException {
        if (historyPath == null || !Files.exists(historyPath)) {
            return new byte[0];
        }
        byte[] history = Files.readAllBytes(historyPath);
        if (history.length > 8 * 1024 * 1024) {
            throw new IOException("MySQL CDC Schema History checkpoint exceeds size limit");
        }
        return history;
    }

    public void requestStop() {
        if (stopped) {
            return;
        }
        stopped = true;
        DebeziumEngine<?> running = engine;
        if (running != null) {
            Thread.ofVirtual().name("yakflow-mysql-cdc-stop").start(() -> {
                try {
                    running.close();
                } catch (IOException ignored) {
                    // The fetcher's final close still bounds resource termination.
                }
            });
        }
    }

    @Override
    public void close() throws Exception {
        requestStop();
        if (worker != null) {
            worker.join(5_000);
            if (worker.isAlive()) {
                worker.interrupt();
                throw new IOException("Debezium MySQL engine failed to stop within timeout");
            }
        }
        if (sessionId != null) {
            CheckpointOffsetBackingStore.unregister(sessionId);
        }
        if (historyPath != null) {
            Files.deleteIfExists(historyPath);
        }
        if (directory != null) {
            Files.deleteIfExists(directory);
        }
    }
}
