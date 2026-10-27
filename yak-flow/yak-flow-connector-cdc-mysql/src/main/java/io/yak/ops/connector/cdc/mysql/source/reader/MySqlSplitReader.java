package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.debezium.task.MySqlSnapshotSplitReadTask;
import io.yak.ops.connector.cdc.mysql.debezium.reader.BinlogSplitReader;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.source.reader.RecordsBySplits;
import io.yak.ops.connector.base.source.reader.RecordsWithSplitIds;
import io.yak.ops.connector.base.source.reader.splitreader.SplitReader;
import io.yak.ops.connector.cdc.mysql.debezium.task.MySqlBinlogSplitReadTask;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.core.data.TableId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fetches normalized chunks and a filtered Binlog stream without advancing mailbox checkpoint state.
 *
 * <p>Normalized snapshot chunks are emitted in one reader mailbox step. Incomplete chunks
 * are retried from their stable range after a failure, without checkpointing source row images.
 */
public final class MySqlSplitReader implements SplitReader<MySqlFetchedRecord, MySqlSplit> {
    private final MySqlSourceConfig config;
    private final MySqlSnapshotSplitReadTask snapshots;
    private final TableMetrics metrics;
    private final long generation;
    private final Set<TableId> tables;
    private final Deque<MySqlSnapshotSplit> pending = new ArrayDeque<>();
    private volatile MySqlBinlogSplitReadTask binlog;
    private volatile byte[] history = new byte[0];
    private volatile MySqlResumeBinlogEvent resume;
    private volatile boolean cancelled;
    private MySqlBinlogSplit binlogSplit;
    private BinlogSplitReader filter;
    private MySqlSnapshotSplit active;
    // Written by the fetcher and inspected by the source mailbox during checkpoints.
    private volatile boolean streaming;
    private volatile boolean snapshotsFinished;
    private boolean snapshotsClosed;
    private boolean closed;
    private BinlogEvent bootstrapCandidate;

    public MySqlSplitReader(MySqlSourceConfig config) {
        this(config, new TableMetrics(), 0);
    }

    public MySqlSplitReader(MySqlSourceConfig config, TableMetrics metrics) {
        this(config, metrics, 0);
    }

    public MySqlSplitReader(MySqlSourceConfig config, TableMetrics metrics, int reader) {
        this.config = Objects.requireNonNull(config, "config");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        generation = metrics.generation();
        tables = Set.copyOf(config.schemas().keySet());
        snapshots = new MySqlSnapshotSplitReadTask(config, reader);
    }

    @Override
    public void addSplits(List<MySqlSplit> splits) throws Exception {
        for (MySqlSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                pending.addLast(snapshot);
            } else if (split instanceof MySqlBinlogSplit stream) {
                if (binlogSplit != null) {
                    throw new IllegalStateException("MySQL Reader already owns a Binlog split");
                }
                binlogSplit = stream;
                history = stream.schemaHistory();
                if (stream.phase() == MySqlBinlogSplit.Phase.BOOTSTRAP) {
                    startBinlog(stream);
                } else if (stream.phase() == MySqlBinlogSplit.Phase.STREAMING) {
                    startStreaming(stream);
                } else if (stream.phase() == MySqlBinlogSplit.Phase.STANDALONE) {
                    binlog = new MySqlBinlogSplitReadTask(config);
                    binlog.start(stream);
                    streaming = true;
                }
            } else throw new IllegalArgumentException("Unknown MySQL split");
        }
    }

    @Override
    public RecordsWithSplitIds<MySqlFetchedRecord> fetch() throws Exception {
        try {
            var result = fetchRecords();
            metrics.observed(generation);
            return result;
        } catch (Exception failure) {
            if (!cancelled && !(failure instanceof InterruptedException)) {
                if (active != null) metrics.readFailed(generation, active.tableId(), false);
                else tables.forEach(table -> metrics.readFailed(generation, table, streaming));
            }
            throw failure;
        }
    }

    private RecordsWithSplitIds<MySqlFetchedRecord> fetchRecords() throws Exception {
        if (closed || cancelled) throw new IllegalStateException("MySQL hybrid reader is closed or cancelled");
        if (binlogSplit != null && binlogSplit.phase() == MySqlBinlogSplit.Phase.BOOTSTRAP) {
            if (bootstrapCandidate == null) bootstrapCandidate = binlog.poll();
            if (bootstrapCandidate == null) return empty();
            byte[] discovered = binlog.snapshotHistory();
            if (discovered.length == 0) {
                Thread.sleep(20);
                return empty();
            }
            BinlogEvent first = bootstrapCandidate;
            history = discovered;
            binlog.close();
            binlog = null;
            binlogSplit = binlogSplit.withAnchor(first.offset(), history);
            bootstrapCandidate = null;
            return records(
                    MySqlBinlogSplit.ID, MySqlFetchedRecord.lowWatermark(first.offset()), false);
        }
        if (active == null && !pending.isEmpty()) {
            active = pending.removeFirst();
            if (!active.prepared()) {
                active = snapshots.read(active);
            }
        }
        if (active != null) {
            MySqlSnapshotSplit chunk = active;
            active = null;
            if (chunk.finished() != null) {
                return new RecordsBySplits<>(Map.of(), Set.of(chunk.splitId()));
            }
            if (chunk.output() == null) {
                throw new IllegalStateException("Snapshot chunk has no normalized output");
            }
            // A chunk is emitted atomically in one mailbox operation. If interrupted,
            // restoration starts from its typed range and repeats Low/High/Backfill.
            return records(chunk.splitId(), MySqlFetchedRecord.prepared(chunk), true);
        }
        if (snapshotsFinished && !snapshotsClosed) {
            snapshots.close();
            snapshotsClosed = true;
        }
        if (binlogSplit != null && resume != null && !streaming) {
            startStreaming(binlogSplit.startStreaming(resume.highWatermark(), resume.finishedSplits()));
        }
        if (streaming) {
            BinlogEvent event = filter == null ? binlog.poll() : filter.poll(binlog);
            if (event != null) {
                return records(MySqlBinlogSplit.ID, MySqlFetchedRecord.binlog(event), false);
            }
        } else Thread.sleep(20);
        return empty();
    }

    private void startBinlog(MySqlBinlogSplit split) throws Exception {
        binlog = new MySqlBinlogSplitReadTask(config);
        binlog.start(split);
    }

    private void startStreaming(MySqlBinlogSplit split) throws Exception {
        filter = BinlogSplitReader.fromFinishedSplits(config.schemas(), split.finishedSplits());
        if (binlog != null) throw new IllegalStateException("Bootstrap Binlog was not closed before handoff");
        // Snapshot backfill connections are no longer needed once this Reader enters streaming.
        snapshots.close();
        binlogSplit = split;
        history = split.schemaHistory();
        startBinlog(split);
        streaming = true;
    }

    public void finishSnapshots() {
        snapshotsFinished = true;
    }

    public void resumeBinlog(MySqlResumeBinlogEvent event) {
        resume = Objects.requireNonNull(event, "event");
    }

    /**
     * Checkpoint the active Debezium schema history, not the initial snapshot anchor.
     *
     * <p>Debezium records every DDL in its history file before handing over subsequent
     * rows. The bootstrap history is only valid until streaming begins. Retaining it
     * with an advanced Binlog offset would restore an obsolete table shape.
     *
     * <p>The history file may contain later DDL from Debezium prefetch: Debezium's
     * history recovery selects records applicable to the restored Binlog offset.
     */
    public byte[] snapshotHistory() {
        if (streaming) {
            MySqlBinlogSplitReadTask active = binlog;
            if (active == null) {
                throw new IllegalStateException("Active MySQL Binlog reader is missing during checkpoint");
            }
            try {
                byte[] current = active.snapshotHistory();
                if (current.length == 0) {
                    throw new IllegalStateException("Active MySQL Binlog has no schema history");
                }
                history = current;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot checkpoint live MySQL schema history", failure);
            }
        }
        return history.clone();
    }

    @Override
    public void wakeUp() {
        /* New assignments must not cancel active JDBC or Binlog I/O. */
    }

    @Override
    public void cancel() {
        cancelled = true;
        snapshots.cancel();
        MySqlBinlogSplitReadTask running = binlog;
        if (running != null) running.requestStop();
    }

    @Override
    public void close() throws Exception {
        if (closed) return;
        closed = true;
        cancel();
        Exception failure = null;
        try {
            snapshots.close();
        } catch (Exception error) {
            failure = error;
        }
        try {
            if (binlog != null) binlog.close();
        } catch (Exception error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        pending.clear();
        active = null;
        if (failure != null) throw failure;
    }

    private static RecordsWithSplitIds<MySqlFetchedRecord> empty() {
        return new RecordsBySplits<>(Map.of(), Set.of());
    }

    private static RecordsWithSplitIds<MySqlFetchedRecord> records(
            String id, MySqlFetchedRecord record, boolean complete) {
        return new RecordsBySplits<>(Map.of(id, List.of(record)), complete ? Set.of(id) : Set.of());
    }
}
