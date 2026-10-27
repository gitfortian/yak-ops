package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import java.util.List;
import java.util.Objects;

/** State of the Binlog split: advances only after the full change was emitted in the reader mailbox. */
public final class MySqlBinlogSplitState extends MySqlSplitState {

    private MySqlHybridBinlogSplit hybrid;
    private MySqlBinlogSplit standalone;
    private DebeziumOffset emittedOffset;

    public MySqlBinlogSplitState(MySqlSplit split) {
        super(split);
        if (split instanceof MySqlHybridBinlogSplit stream) {
            hybrid = stream;
            emittedOffset = stream.binlog().offset();
        } else if (split instanceof MySqlBinlogSplit stream) {
            standalone = stream;
            emittedOffset = stream.offset();
        } else {
            throw new IllegalArgumentException("Expected a Binlog split");
        }
    }

    @Override
    public String sourceId() {
        return hybrid != null
                ? hybrid.binlog().definitionFingerprint()
                : standalone.definitionFingerprint();
    }

    @Override
    public void captureLow(DebeziumOffset low, byte[] schemaHistory) {
        if (hybrid == null) {
            throw new IllegalStateException("Low watermark is not a Binlog bootstrap event");
        }
        hybrid = hybrid.withAnchor(Objects.requireNonNull(low, "low"), schemaHistory);
        emittedOffset = low;
    }

    @Override
    public void resume(DebeziumOffset high, List<FinishedSnapshotSplitInfo> finished) {
        if (hybrid == null) {
            return;
        }
        if (hybrid.phase() == MySqlHybridBinlogSplit.Phase.STREAMING) {
            if (!Objects.equals(high, hybrid.highWatermark()) || !finished.equals(hybrid.finishedSplits())) {
                throw new IllegalStateException("Restored streaming metadata conflicts with coordinator handoff");
            }
            return;
        }
        hybrid = hybrid.startStreaming(high, finished);
        emittedOffset = hybrid.binlog().offset();
    }

    @Override
    public void emitBinlog(DebeziumOffset offset) {
        if (standalone == null
                && (hybrid == null || hybrid.phase() != MySqlHybridBinlogSplit.Phase.STREAMING)) {
            throw new IllegalStateException("Binlog changes cannot be emitted before snapshot handoff");
        }
        emittedOffset = Objects.requireNonNull(offset, "offset");
    }

    @Override
    public MySqlSplit toMySqlSplit() {
        return hybrid != null ? hybrid : standalone;
    }

    @Override
    public MySqlSplit checkpoint(byte[] history) {
        if (hybrid == null) {
            if (emittedOffset == null || history == null || history.length == 0) {
                throw new IllegalStateException("MySQL Binlog has no complete offset/schema-history checkpoint yet");
            }
            return standalone.withProgress(emittedOffset, history);
        }
        if (hybrid.phase() == MySqlHybridBinlogSplit.Phase.BOOTSTRAP) {
            return hybrid;
        }
        if (history == null || history.length == 0 || emittedOffset == null) {
            throw new IllegalStateException("Hybrid Binlog checkpoint requires offset and Schema History");
        }
        if (hybrid.phase() == MySqlHybridBinlogSplit.Phase.PAUSED) {
            return hybrid;
        }
        return hybrid.withEmittedOffset(emittedOffset, history);
    }
}
