package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import java.util.Objects;

/**
 * Mailbox-confined normalized snapshot output, preparation checkpoint, and Binlog phase/offset.
 *
 * <p>Fetcher prefetch never mutates checkpoint state. Each UPDATE pair advances the
 * Binlog offset only after both TableRecords have been emitted in one mailbox step.
 */
public final class MySqlSplitState {

    private MySqlSnapshotSplit snapshot;
    private MySqlHybridBinlogSplit binlog;
    private MySqlBinlogSplit standalone;
    private DebeziumOffset emittedOffset;
    private Long outputCheckpoint;
    private boolean outputDurable;

    public MySqlSplitState(MySqlSplit initial) {
        if (initial instanceof MySqlSnapshotSplit range) {
            snapshot = range;
            outputDurable = range.prepared();
        } else if (initial instanceof MySqlHybridBinlogSplit stream) {
            binlog = stream;
            emittedOffset = stream.binlog().offset();
        } else if (initial instanceof MySqlBinlogSplit stream) {
            standalone = stream;
            emittedOffset = stream.offset();
        } else {
            throw new IllegalArgumentException("Unknown MySQL split");
        }
    }

    /** Stable source scope from the original durable split, independent of physical attempts. */
    public String sourceId() {
        return snapshot != null
                ? snapshot.fingerprint()
                : binlog != null ? binlog.binlog().definitionFingerprint() : standalone.definitionFingerprint();
    }

    /** Snapshot identity is based on the original split and frozen output ordinal. */
    public String snapshotSplitId() {
        if (snapshot == null || !snapshot.prepared()) {
            throw new IllegalStateException("Snapshot event identity requires frozen output");
        }
        return snapshot.splitId();
    }

    public void emitSnapshotKey(Long key) {
        if (snapshot == null || key == null) {
            throw new IllegalStateException("Snapshot progress must contain a key");
        }
        snapshot = snapshot.withLastEmittedKey(key);
    }

    /** Anchors the Binlog BEFORE any JDBC snapshot split may be assigned. */
    public void captureLow(DebeziumOffset low, byte[] schemaHistory) {
        if (binlog == null) {
            throw new IllegalStateException("Low watermark is not a Binlog event");
        }
        binlog = binlog.withAnchor(Objects.requireNonNull(low, "low"), schemaHistory);
        emittedOffset = low;
    }

    public void prepareSnapshot(MySqlSnapshotSplit prepared) {
        if (snapshot == null
                || snapshot.prepared()
                || !snapshot.splitId().equals(prepared.splitId())
                || !snapshot.tableId().equals(prepared.tableId())
                || !snapshot.fingerprint().equals(prepared.fingerprint())
                || !Objects.equals(snapshot.lowerInclusive(), prepared.lowerInclusive())
                || !Objects.equals(snapshot.upperExclusive(), prepared.upperExclusive())
                || !Objects.equals(snapshot.chunkRange(), prepared.chunkRange())) {
            throw new IllegalStateException("Prepared snapshot differs from its assigned range");
        }
        snapshot = prepared;
        outputDurable = false;
    }

    public void snapshotCheckpoint(long checkpointId) {
        if (snapshot != null && snapshot.output() != null && !outputDurable && outputCheckpoint == null) {
            outputCheckpoint = checkpointId;
        }
    }

    public String completeCheckpoint(long checkpointId) {
        if (outputCheckpoint != null && checkpointId >= outputCheckpoint) {
            outputDurable = true;
            outputCheckpoint = null;
            return snapshot.splitId();
        }
        return null;
    }

    public void emitSnapshotRows(int position) {
        if (!outputDurable) throw new IllegalStateException("Snapshot output has no completed preparation checkpoint");
        snapshot = snapshot.withOutputPosition(position);
    }

    public void resume(
            DebeziumOffset high,
            java.util.List<io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo> finished) {
        if (binlog == null) {
            return;
        }
        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) {
            if (!Objects.equals(high, binlog.highWatermark()) || !finished.equals(binlog.finishedSplits())) {
                throw new IllegalStateException("Restored streaming metadata conflicts with coordinator handoff");
            }
            return;
        }
        binlog = binlog.startStreaming(high, finished);
        emittedOffset = binlog.binlog().offset();
    }

    public void emitBinlog(DebeziumOffset position) {
        if (standalone != null) {
            emittedOffset = Objects.requireNonNull(position, "position");
            return;
        }
        if (binlog == null || binlog.phase() != MySqlHybridBinlogSplit.Phase.STREAMING) {
            throw new IllegalStateException("Binlog changes cannot be emitted before snapshot handoff");
        }
        emittedOffset = Objects.requireNonNull(position, "position");
    }

    public boolean binlog() {
        return binlog != null || standalone != null;
    }

    public MySqlSplit checkpoint(byte[] history) {
        if (snapshot != null) {
            return snapshot;
        }
        if (standalone != null) {
            if (emittedOffset == null || history == null || history.length == 0) {
                throw new IllegalStateException("MySQL Binlog has no complete offset/schema-history checkpoint yet");
            }
            return standalone.withProgress(emittedOffset, history);
        }
        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP) {
            return binlog;
        }
        if (history == null || history.length == 0 || emittedOffset == null) {
            throw new IllegalStateException("Hybrid Binlog checkpoint requires offset and Schema History");
        }
        if (binlog.phase() == MySqlHybridBinlogSplit.Phase.PAUSED) {
            return binlog;
        }
        return binlog.withEmittedOffset(emittedOffset, history);
    }
}
