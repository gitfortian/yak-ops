package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import java.util.List;
import java.util.Objects;

/** Mutable binlog state for both initial and binlog-only source readers. */
public final class MySqlBinlogSplitState extends MySqlSplitState {
    private MySqlBinlogSplit binlog;
    private DebeziumOffset emittedOffset;

    public MySqlBinlogSplitState(MySqlSplit split) {
        super(split);
        if (!(split instanceof MySqlBinlogSplit source)) {
            throw new IllegalArgumentException("Expected MySqlBinlogSplit");
        }
        binlog = source;
        emittedOffset = source.offset();
    }

    @Override
    public void captureLow(DebeziumOffset low, byte[] schemaHistory) {
        binlog = binlog.withAnchor(Objects.requireNonNull(low, "low"), schemaHistory);
        emittedOffset = low;
    }

    @Override
    public void resume(DebeziumOffset high, List<FinishedSnapshotSplitInfo> finished) {
        if (binlog.phase() == MySqlBinlogSplit.Phase.STANDALONE) {
            return;
        }
        if (binlog.phase() == MySqlBinlogSplit.Phase.STREAMING) {
            if (!Objects.equals(high, binlog.highWatermark()) || !finished.equals(binlog.finishedSplits())) {
                throw new IllegalStateException("Restored streaming metadata conflicts with coordinator handoff");
            }
            return;
        }
        binlog = binlog.startStreaming(high, finished);
        emittedOffset = binlog.offset();
    }

    @Override
    public void emitBinlog(DebeziumOffset offset) {
        if (binlog.isSuspended()) {
            throw new IllegalStateException("Binlog changes cannot be emitted before snapshot handoff");
        }
        emittedOffset = Objects.requireNonNull(offset, "offset");
    }

    @Override
    public MySqlBinlogSplit toMySqlSplit() {
        return binlog;
    }

    @Override
    public MySqlSplit checkpoint(byte[] history) {
        if (binlog.phase() == MySqlBinlogSplit.Phase.BOOTSTRAP) {
            return binlog;
        }
        if (binlog.phase() == MySqlBinlogSplit.Phase.PAUSED) {
            if (emittedOffset == null || history == null || history.length == 0) {
                throw new IllegalStateException("Suspended Binlog requires anchor and schema history");
            }
            return binlog;
        }
        if (binlog.phase() == MySqlBinlogSplit.Phase.STANDALONE && emittedOffset == null && binlog.offset() == null) {
            // No event has reached the mailbox yet; restore the unresolved startup and
            // repeat Debezium schema bootstrap instead of checkpointing Engine read-ahead.
            return binlog;
        }
        if (emittedOffset == null || history == null || history.length == 0) {
            throw new IllegalStateException("MySQL Binlog has no complete offset/schema-history checkpoint");
        }
        return binlog.phase() == MySqlBinlogSplit.Phase.STREAMING
                ? binlog.withEmittedOffset(emittedOffset, history)
                : binlog.withProgress(emittedOffset, history);
    }
}
