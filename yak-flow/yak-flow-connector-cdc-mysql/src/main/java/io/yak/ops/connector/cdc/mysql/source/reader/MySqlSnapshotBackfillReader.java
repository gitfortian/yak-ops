package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.debezium.MySqlBinlogEngine;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
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
    private final Callable<Void> afterLow;
    private volatile Statement watermarkStatement;
    private volatile Statement readLockStatement;
    private volatile boolean cancelled;
    private boolean started;
    private Map<String, Object> partition;

    public MySqlSnapshotBackfillReader(MySqlCdcSourceConfig config, int reader) {
        this(config, reader, () -> null);
    }

    /** A package-local phase callback lets database tests place commits exactly inside the scan window. */
    MySqlSnapshotBackfillReader(MySqlCdcSourceConfig config, int reader, Callable<Void> afterSnapshot) {
        this(config, reader, afterSnapshot, () -> null);
    }

    /** Executes after a keyless read view is anchored and the short global read lock is released. */
    MySqlSnapshotBackfillReader(
            MySqlCdcSourceConfig config, int reader, Callable<Void> afterSnapshot, Callable<Void> afterLow) {
        this.config = Objects.requireNonNull(config, "config");
        this.afterSnapshot = Objects.requireNonNull(afterSnapshot, "afterSnapshot");
        this.afterLow = Objects.requireNonNull(afterLow, "afterLow");
        snapshots = new MySqlSnapshotSplitReader(config);
        binlog = new MySqlBinlogEngine(config.withoutSchemaEvolution(), config.snapshotServerId(reader));
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
        if (split.chunkRange() != null && !schema.primaryKeys().isEmpty()) {
            throw new UnsupportedOperationException("Typed backfill currently requires a keyless source table");
        }
        MySqlChunkBackfill buffer = split.chunkRange() == null
                ? new MySqlChunkBackfill(split, schema, MAX_ROWS, MAX_PAYLOAD_BYTES)
                : new MySqlChunkBackfill(split.chunkRange(), schema, MAX_ROWS, MAX_PAYLOAD_BYTES);
        try {
            BinlogOffset low;
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
            BinlogOffset high = watermark();
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
                                    "Keyless snapshot cannot reconcile schema changes during Backfill");
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

    private BinlogOffset watermark() throws SQLException {
        try (Connection connection = config.openSnapshotConnection()) {
            return watermark(connection);
        }
    }

    /**
     * Captures a Low watermark and starts a repeatable-read consistent snapshot while
     * MySQL's global read lock prevents commits between those two operations.
     *
     * <p>The lock is released immediately after the JDBC SELECT has opened on the
     * anchored read view. A failure always releases it before propagating.
     */
    private BinlogOffset openConsistentSnapshot(MySqlSnapshotSplit split) throws SQLException {
        checkCancelled();
        Connection view = null;
        BinlogOffset low;
        try (Connection lockConnection = config.openSnapshotConnection();
                Statement lock = lockConnection.createStatement()) {
            readLockStatement = lock;
            lock.setQueryTimeout(60);
            boolean acquired = false;
            try {
                lock.execute("FLUSH TABLES WITH READ LOCK");
                acquired = true;
                checkCancelled();
                low = watermark(lockConnection);
                view = config.openSnapshotConnection();
                view.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                view.setReadOnly(true);
                view.setAutoCommit(false);
                try (Statement transaction = view.createStatement()) {
                    transaction.setQueryTimeout(60);
                    transaction.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
                }
            } finally {
                try {
                    if (acquired) {
                        try (Statement unlock = lockConnection.createStatement()) {
                            unlock.setQueryTimeout(60);
                            unlock.execute("UNLOCK TABLES");
                        }
                    }
                } finally {
                    readLockStatement = null;
                }
            }
        } catch (SQLException | RuntimeException failure) {
            if (view != null) {
                try {
                    view.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw new SQLException("Cannot establish a consistent keyless snapshot read view; "
                    + "MySQL FLUSH TABLES WITH READ LOCK and REPEATABLE READ privileges are required", failure);
        }
        // A pinned InnoDB read view remains valid after UNLOCK TABLES. Never hold
        // the global lock while executing or fetching the potentially large SELECT.
        try {
            snapshots.open(split, view);
        } catch (SQLException | RuntimeException failure) {
            try {
                view.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return low;
    }

    private BinlogOffset watermark(Connection connection) throws SQLException {
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
                return new BinlogOffset(partition, Map.of("file", file, "pos", position));
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
        Statement lock = readLockStatement;
        if (running != null || lock != null)
            Thread.ofVirtual().name("yak-mysql-watermark-cancel").start(() -> {
                try {
                    if (running != null) running.cancel();
                    if (lock != null && lock != running) lock.cancel();
                } catch (SQLException ignored) {
                    /* Owning fetcher releases global locks and JDBC resources in finally. */
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
