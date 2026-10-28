package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Objects;

/**
 * A checkpoint of hybrid source split assignment.
 *
 * <p>Snapshot split discovery and watermarks live in SnapshotPendingSplitsState, following
 * Flink CDC. YakFlow's Debezium 2.7 bootstrap anchor and deferred binlog reader are
 * retained here until the runtime can construct the upstream binlog split directly.
 */
public final class HybridPendingSplitsState extends PendingSplitsState {

    public enum Phase {
        BOOTSTRAP,
        SNAPSHOT,
        HANDOFF,
        STREAMING
    }

    private final SnapshotPendingSplitsState snapshotPendingSplits;
    private final boolean binlogAssigned;
    private final MySqlBinlogSplit pendingBinlog;
    private final Phase phase;
    private final DebeziumOffset lowWatermark;
    private final DebeziumOffset highWatermark;

    public HybridPendingSplitsState(
            SnapshotPendingSplitsState snapshotPendingSplits,
            boolean binlogAssigned,
            MySqlBinlogSplit pendingBinlog,
            Phase phase,
            DebeziumOffset lowWatermark,
            DebeziumOffset highWatermark) {
        this.snapshotPendingSplits = Objects.requireNonNull(snapshotPendingSplits, "snapshotPendingSplits");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.binlogAssigned = binlogAssigned;
        this.pendingBinlog = pendingBinlog;
        this.lowWatermark = lowWatermark;
        this.highWatermark = highWatermark;
        if (binlogAssigned == (pendingBinlog != null)
                || (phase != Phase.BOOTSTRAP && lowWatermark == null)
                || ((phase == Phase.HANDOFF || phase == Phase.STREAMING) && highWatermark == null)) {
            throw new IllegalArgumentException("Inconsistent hybrid binlog assignment state");
        }
        if (phase == Phase.HANDOFF || phase == Phase.STREAMING) {
            if (!snapshotPendingSplits.snapshotPlanned()
                    || snapshotPendingSplits.totalSnapshotSplits() == 0
                    || snapshotPendingSplits.getFinishedSplits().size()
                            != snapshotPendingSplits.totalSnapshotSplits()) {
                throw new IllegalArgumentException("Hybrid handoff requires all snapshot chunks");
            }
            DebeziumOffset maximum = snapshotPendingSplits.getFinishedSplitInfos().stream()
                    .map(FinishedSnapshotSplitInfo::highWatermark)
                    .max(DebeziumOffset::compareTo)
                    .orElseThrow();
            if (highWatermark.compareTo(maximum) != 0 || lowWatermark.compareTo(highWatermark) > 0) {
                throw new IllegalArgumentException("Hybrid checkpoint watermarks are inconsistent");
            }
        }
    }

    public SnapshotPendingSplitsState getSnapshotPendingSplits() {
        return snapshotPendingSplits;
    }

    public boolean isBinlogSplitAssigned() {
        return binlogAssigned;
    }

    public MySqlBinlogSplit pendingBinlog() {
        return pendingBinlog;
    }

    public Phase phase() {
        return phase;
    }

    public DebeziumOffset lowWatermark() {
        return lowWatermark;
    }

    public DebeziumOffset highWatermark() {
        return highWatermark;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HybridPendingSplitsState state)) return false;
        return binlogAssigned == state.binlogAssigned
                && snapshotPendingSplits.equals(state.snapshotPendingSplits)
                && Objects.equals(pendingBinlog, state.pendingBinlog)
                && phase == state.phase
                && Objects.equals(lowWatermark, state.lowWatermark)
                && Objects.equals(highWatermark, state.highWatermark);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotPendingSplits, binlogAssigned, pendingBinlog, phase, lowWatermark, highWatermark);
    }

    @Override
    public String toString() {
        return "HybridPendingSplitsState{phase=" + phase
                + ", chunks=" + snapshotPendingSplits.totalSnapshotSplits()
                + ", checkpoint=redacted}";
    }
}
