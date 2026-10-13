package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import java.util.Objects;

/**
 * Checkpointable Binlog split with the initial snapshot handoff phase and watermark.
 *
 * <p>BOOTSTRAP captures a resume point before any snapshot is assigned. PAUSED retains
 * that point while snapshot ranges are emitted. STREAMING reads from the anchor after
 * a completed snapshot checkpoint. No Binlog offset advances while paused.
 *
 * <p>Each phase transition returns a new split. The nested Binlog split owns its
 * serialized Schema History bytes and exposes them only through defensive copies.
 * An assigned split has execution identity, rather than implicit record value equality.
 */
public final class MySqlHybridBinlogSplit implements MySqlHybridSplit {

    public static final String ID = MySqlBinlogSplit.ID;

    /** Snapshot handoff phases of the Binlog subscription. */
    public enum Phase {
        BOOTSTRAP,
        PAUSED,
        STREAMING
    }

    private final MySqlBinlogSplit binlog;
    private final Phase phase;
    private final BinlogOffset highWatermark;

    /**
     * Creates one immutable Binlog handoff state.
     *
     * @param binlog Binlog subscription and checkpoint state
     * @param phase current snapshot handoff phase
     * @param highWatermark completed snapshot boundary, when known
     */
    public MySqlHybridBinlogSplit(MySqlBinlogSplit binlog, Phase phase, BinlogOffset highWatermark) {
        this.binlog = Objects.requireNonNull(binlog, "binlog");
        this.phase = Objects.requireNonNull(phase, "phase");
        if (phase != Phase.BOOTSTRAP && (binlog.offset() == null || binlog.schemaHistory().length == 0)) {
            throw new IllegalArgumentException("Snapshot handoff requires a Binlog anchor and schema history");
        }
        this.highWatermark = highWatermark;
    }

    /** Returns the underlying Binlog split. */
    public MySqlBinlogSplit binlog() {
        return binlog;
    }

    /** Returns the current snapshot handoff phase. */
    public Phase phase() {
        return phase;
    }

    /** Returns the completed snapshot boundary, when available. */
    public BinlogOffset highWatermark() {
        return highWatermark;
    }

    /** Returns the stable identifier of the Binlog subscription. */
    @Override
    public String splitId() {
        return ID;
    }

    /** Captures the low watermark and schema history before snapshot assignment. */
    public MySqlHybridBinlogSplit withAnchor(BinlogOffset offset, byte[] history) {
        if (phase != Phase.BOOTSTRAP) {
            throw new IllegalStateException("Hybrid Binlog anchor has already been captured");
        }
        return new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), Phase.PAUSED, null);
    }

    /** Resumes streaming after a completed snapshot checkpoint. */
    public MySqlHybridBinlogSplit startStreaming(BinlogOffset high) {
        if (phase != Phase.PAUSED) {
            throw new IllegalStateException("Hybrid Binlog is not waiting for snapshot handoff");
        }
        return new MySqlHybridBinlogSplit(binlog, Phase.STREAMING, Objects.requireNonNull(high, "high"));
    }

    /** Advances the checkpoint cursor after the emitted Binlog change is acknowledged. */
    public MySqlHybridBinlogSplit withEmittedOffset(BinlogOffset offset, byte[] history) {
        if (phase != Phase.STREAMING) {
            throw new IllegalStateException("A paused Binlog split must not advance its offset");
        }
        return new MySqlHybridBinlogSplit(binlog.withProgress(offset, history), phase, highWatermark);
    }

    /** Excludes checkpoint offsets and Schema History from diagnostic output. */
    @Override
    public String toString() {
        return "MySqlHybridBinlogSplit{phase=" + phase + ", state=redacted}";
    }
}
