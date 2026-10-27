package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.debezium.MySqlBinlogEngine;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/** Fetcher-owned JDBC scan and bounded Binlog reconciliation for one chunk at a time. */
public final class MySqlSnapshotBackfillReader implements AutoCloseable {
    private static final int MAX_ROWS = 100_000;
    private static final long MAX_PAYLOAD_BYTES = 4L * 1024 * 1024;
    private static final long BINLOG_TIMEOUT_NANOS = Duration.ofSeconds(60).toNanos();
    private final MySqlCdcSourceConfig config;
    private final MySqlSnapshotSplitReader snapshots;
    private final MySqlBinlogEngine binlog;
    private final Callable<Void> afterSnapshot;
    private final Callable<Void> afterLowWatermark;
    private final Callable<Void> afterLow;
    private volatile Statement watermarkStatement;
    private volatile boolean cancelled;
    private boolean started;
    private Map<String, Object> partition;

    public MySqlSnapshotBackfillReader(MySqlCdcSourceConfig config, int reader) {
        this(config, reader, () -> null);
    }

    /** A package-local phase callback lets database tests place commits exactly inside the scan window. */
    MySqlSnapshotBackfillReader(MySqlCdcSourceConfig config, int reader, Callable<Void> afterSnapshot) {
        this(config, reader, afterSnapshot, () -> null, () -> null);
    }

    /** Executes after Low but before the consistent read view is established. */
    MySqlSnapshotBackfillReader(
            MySqlCdcSourceConfig config,
            int reader,
            Callable<Void> afterSnapshot,
            Callable<Void> afterLowWatermark,
            Callable<Void> afterLow) {
        this.config = Objects.requireNonNull(config, "config");
        this.afterSnapshot = Objects.requireNonNull(afterSnapshot, "afterSnapshot");
        this.afterLowWatermark = Objects.requireNonNull(afterLowWatermark, "afterLowWatermark");
        this.afterLow = Objects.requireNonNull(afterLow, "afterLow");
        snapshots = new MySqlSnapshotSplitReader(config);
        binlog = new MySqlBinlogEngine(config.withoutSchemaEvolution(), config.snapshotServerId(reader));
    }

    /** Preserves the callback after the view is anchored for existing integration scenarios. */
    MySqlSnapshotBackfillReader(
            MySqlCdcSourceConfig config, int reader, Callable<Void> afterSnapshot, Callable<Void> afterLow) {
        this(config, reader, afterSnapshot, () -> null, afterLow);
    }

    /**
     * Produces a complete normalized result without publishing any rows to the downstream.
     *
     * <p>Equal SQL watermarks prove an empty interval. Otherwise an ordered Debezium event
     * or heartbeat must reach High; neither an empty queue nor a timeout implies completion.
     */
    public MySqlSnapshotSplit read(MySqlSnapshotSplit split) throws Exception {
        checkCancelled();
        if (split.prepared() || split.lastEmittedKey() != null) {
            throw new IllegalArgumentException("Backfill requires an unprepared complete chunk range");
        }
        ensureBinlog();
        var schema = Objects.requireNonNull(config.schemas().get(split.tableId()), "chunk schema");
        MySqlChunkBackfill buffer = split.chunkRange() == null
                ? new MySqlChunkBackfill(split, schema, MAX_ROWS, MAX_PAYLOAD_BYTES)
                : new MySqlChunkBackfill(split.chunkRange(), schema, MAX_ROWS, MAX_PAYLOAD_BYTES);
        try {
            DebeziumOffset low;
            if (split.chunkRange() == null) {
                low = watermark();
                snapshots.open(split);
            } else {
                low = openConsistentSnapshot(split);
                afterLow.call();
            }
            boolean complete = false;
            while (!complete) {
                checkCancelled();
                if (split.chunkRange() == null) {
                    var batch = snapshots.fetch();
                    while (batch.nextSplit() != null) {
                        MySqlHybridFetchedRecord record;
                        while ((record = batch.nextRecordFromSplit()) != null) {
                            for (var row : record.rows()) buffer.addSnapshot(row);
                        }
                    }
                    complete = batch.finishedSplits().contains(split.splitId());
                    batch.recycle();
                } else {
                    var batch = snapshots.fetchTyped();
                    while (batch.nextSplit() != null) {
                        io.yak.ops.core.data.TableRecord record;
                        while ((record = batch.nextRecordFromSplit()) != null) {
                            buffer.addSnapshot(record);
                        }
                    }
                    complete = batch.finishedSplits().contains(split.splitId());
                    batch.recycle();
                }
            }
            afterSnapshot.call();
            DebeziumOffset high = watermark();
            if (low.compareTo(high) > 0) {
                throw new IllegalStateException("MySQL snapshot watermarks moved backwards");
            }
            if (low.compareTo(high) != 0) {
                long deadline = System.nanoTime() + BINLOG_TIMEOUT_NANOS;
                while (true) {
                    checkDeadline(deadline);
                    BinlogEvent event = binlog.poll();
                    if (event == null) continue;
                    int highOrder = event.offset().compareTo(high);
                    if (event.offset().compareTo(low) > 0 && highOrder <= 0) {
                        if (split.chunkRange() != null
                                && event.schemaChange() != null
                                && split.tableId().equals(event.schemaChange().tableId())) {
                            throw new UnsupportedOperationException(
                                    "Typed snapshot cannot reconcile schema changes during Backfill");
                        }
                        if (!event.records().isEmpty()
                                && split.tableId()
                                        .equals(event.records().getFirst().tableId())) {
                            buffer.apply(event);
                        }
                    }
                    if (highOrder >= 0) break;
                }
            }
            MySqlSnapshotSplit prepared = split.withOutput(new MySqlSnapshotOutput(low, high, buffer.finish()));
            // Fail before any downstream output if the exact persisted representation cannot fit.
            new MySqlHybridSplitSerializer().serialize(prepared);
            return prepared;
        } finally {
            snapshots.close();
        }
    }

    private void ensureBinlog() throws Exception {
        if (partition != null) return;
        if (!started) {
            started = true;
            binlog.start(new MySqlBinlogSplit("snapshot-backfill", null, new byte[0]));
        }
        long deadline = System.nanoTime() + BINLOG_TIMEOUT_NANOS;
        while (partition == null) {
            checkDeadline(deadline);
            BinlogEvent event = binlog.poll();
            if (event != null && binlog.snapshotHistory().length > 0)
                partition = event.offset().partition();
        }
    }

    private DebeziumOffset watermark() throws SQLException {
        try (Connection connection = config.openSnapshotConnection()) {
            return watermark(connection);
        }
    }

    /**
     * Captures Low before opening a repeatable-read consistent snapshot.
     *
     * <p>Transactions committed between the Low watermark and read-view creation may be
     * visible in both the snapshot and the Low-to-High Binlog interval. Chunk Backfill
     * reconciles those changes; a global read lock is not required to close this window.
     */
    DebeziumOffset openConsistentSnapshot(MySqlSnapshotSplit split) throws SQLException {
        checkCancelled();
        DebeziumOffset low;
        try {
            low = watermark();
            afterLowWatermark.call();
            checkCancelled();
        } catch (Exception failure) {
            throw new SQLException("Cannot capture typed snapshot Low watermark", failure);
        }
        openTypedSnapshotReadView(split);
        return low;
    }

    /** Opens and pins the typed InnoDB view without requiring a global table lock. */
    void openTypedSnapshotReadView(MySqlSnapshotSplit split) throws SQLException {
        checkCancelled();
        Connection view = null;
        try {
            view = config.openSnapshotConnection();
            view.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            view.setReadOnly(true);
            view.setAutoCommit(false);
            try (Statement transaction = view.createStatement()) {
                transaction.setQueryTimeout(60);
                transaction.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
            }
            // SnapshotSplitReader takes ownership of the pinned connection on success.
            snapshots.open(split, view);
        } catch (SQLException | RuntimeException failure) {
            if (view != null) {
                try {
                    view.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw new SQLException(
                    "Cannot establish a consistent typed snapshot read view; "
                            + "MySQL REPEATABLE READ and SELECT privileges are required",
                    failure);
        }
    }

    private DebeziumOffset watermark(Connection connection) throws SQLException {
        checkCancelled();
        try (Statement statement = connection.createStatement()) {
            watermarkStatement = statement;
            statement.setQueryTimeout(60);
            checkCancelled();
            ResultSet rows;
            try {
                rows = statement.executeQuery("SHOW BINARY LOG STATUS");
            } catch (SQLException unsupported) {
                checkCancelled();
                try {
                    rows = statement.executeQuery("SHOW MASTER STATUS");
                } catch (SQLException failure) {
                    failure.addSuppressed(unsupported);
                    throw failure;
                }
            }
            try (ResultSet status = rows) {
                if (!status.next()) throw new SQLException("MySQL binary logging is disabled");
                String file = status.getString(1);
                long position = status.getLong(2);
                if (file == null || file.isBlank() || position < 1)
                    throw new SQLException("Invalid MySQL Binlog boundary");
                return new DebeziumOffset(partition, Map.of("file", file, "pos", position));
            }
        } finally {
            watermarkStatement = null;
        }
    }

    private void checkDeadline(long deadline) throws SQLException {
        checkCancelled();
        if (System.nanoTime() - deadline >= 0) {
            throw new SQLException("MySQL Binlog did not reach the required snapshot boundary before timeout");
        }
    }

    private void checkCancelled() throws SQLException {
        if (cancelled || Thread.currentThread().isInterrupted())
            throw new SQLException("MySQL chunk backfill was cancelled");
    }

    public void cancel() {
        cancelled = true;
        snapshots.cancel();
        binlog.requestStop();
        Statement running = watermarkStatement;
        if (running != null)
            Thread.ofVirtual().name("yak-mysql-watermark-cancel").start(() -> {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    /* The owning fetcher releases JDBC resources in finally. */
                }
            });
    }

    @Override
    public void close() throws Exception {
        cancel();
        Exception failure = null;
        try {
            snapshots.close();
        } catch (Exception error) {
            failure = error;
        }
        try {
            binlog.close();
        } catch (Exception error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
