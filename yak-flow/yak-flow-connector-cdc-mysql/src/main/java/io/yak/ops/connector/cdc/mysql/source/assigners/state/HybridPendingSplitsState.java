package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checkpointed global progress of the initial snapshot and deferred Binlog handoff.
 *
 * <p>The checkpoint covering HANDOFF must complete before the streaming Reader is
 * unpaused. Coordinator-assigned snapshot metadata never replaces Reader-owned cursors.
 */
public final class HybridPendingSplitsState extends PendingSplitsState {
    private final Phase phase;
    private final MySqlBinlogSplit pendingBinlog;
    private final boolean binlogAssigned;
    private final DebeziumOffset lowWatermark;
    private final DebeziumOffset highWatermark;
    private final boolean snapshotPlanned;
    private final int totalSnapshotSplits;
    private final List<MySqlSnapshotSplit> pendingSnapshots;
    private final Map<String, MySqlSnapshotSplit> assignedSnapshots;
    private final Set<String> finishedSnapshots;
    private final List<FinishedSnapshotSplitInfo> finishedSplitInfos;
    private final int nextTableIndex;
    private final int nextChunkIndex;
    private final ChunkRange nextChunkRange;


    public enum Phase {
        BOOTSTRAP,
        SNAPSHOT,
        HANDOFF,
        STREAMING
    }

    public HybridPendingSplitsState(
            Phase phase,
            MySqlBinlogSplit pendingBinlog,
            boolean binlogAssigned,
            DebeziumOffset lowWatermark,
            DebeziumOffset highWatermark,
            boolean snapshotPlanned,
            int totalSnapshotSplits,
            List<MySqlSnapshotSplit> pendingSnapshots,
            Map<String, MySqlSnapshotSplit> assignedSnapshots,
            Set<String> finishedSnapshots,
            List<FinishedSnapshotSplitInfo> finishedSplitInfos,
            int nextTableIndex,
            int nextChunkIndex,
            ChunkRange nextChunkRange) {
        finishedSplitInfos = List.copyOf(Objects.requireNonNull(finishedSplitInfos, "finishedSplitInfos"));
        if (nextChunkRange != null
                && (nextChunkIndex < 1 || nextChunkRange.start() == null || nextChunkRange.end() != null)) {
            throw new IllegalArgumentException("Invalid typed chunk discovery cursor");
        }
        if (nextTableIndex < 0
                || nextChunkIndex < 0
                || nextChunkIndex > 10_000
                || (nextChunkIndex > 0 && nextChunkRange == null)) {
            throw new IllegalArgumentException("Invalid snapshot discovery position");
        }
        Objects.requireNonNull(phase, "phase");
        pendingSnapshots = List.copyOf(Objects.requireNonNull(pendingSnapshots, "pendingSnapshots"));
        assignedSnapshots = Map.copyOf(Objects.requireNonNull(assignedSnapshots, "assignedSnapshots"));
        finishedSnapshots = Set.copyOf(Objects.requireNonNull(finishedSnapshots, "finishedSnapshots"));
        Set<String> completedIds = new java.util.HashSet<>();
        for (FinishedSnapshotSplitInfo info : finishedSplitInfos) {
            if (!completedIds.add(info.splitId()))
                throw new IllegalArgumentException("Duplicate completed chunk metadata");
        }
        if (!completedIds.equals(finishedSnapshots))
            throw new IllegalArgumentException("Incomplete chunk High metadata");
        Set<String> allIds = new java.util.HashSet<>(finishedSnapshots);
        for (MySqlSnapshotSplit split : pendingSnapshots) {
            if (!allIds.add(split.splitId())) throw new IllegalArgumentException("Duplicate pending chunk");
        }
        for (var entry : assignedSnapshots.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().splitId()) || !allIds.add(entry.getKey())) {
                throw new IllegalArgumentException("Duplicate or invalid assigned chunk");
            }
        }
        if (phase == Phase.HANDOFF || phase == Phase.STREAMING) {
            if (!snapshotPlanned
                    || totalSnapshotSplits == 0
                    || finishedSnapshots.size() != totalSnapshotSplits
                    || highWatermark == null
                    || lowWatermark == null) {
                throw new IllegalArgumentException("Hybrid handoff requires all normalized chunks to be completed");
            }
            DebeziumOffset maximum = finishedSplitInfos.stream()
                    .map(FinishedSnapshotSplitInfo::highWatermark)
                    .max(DebeziumOffset::compareTo)
                    .orElseThrow();
            if (highWatermark.compareTo(maximum) != 0 || lowWatermark.compareTo(highWatermark) > 0) {
                throw new IllegalArgumentException("Hybrid coordinator watermarks differ from completed chunks");
            }
        }
        if (binlogAssigned == (pendingBinlog != null)
                || totalSnapshotSplits < 0
                || totalSnapshotSplits > 10_000
                || pendingSnapshots.size() + assignedSnapshots.size() + finishedSnapshots.size() != totalSnapshotSplits
                || (phase != Phase.BOOTSTRAP && lowWatermark == null)
                || ((phase == Phase.HANDOFF || phase == Phase.STREAMING) && highWatermark == null)) {
            throw new IllegalArgumentException("Inconsistent MySQL hybrid coordinator state");
        }
    
        this.phase = phase;
        this.pendingBinlog = pendingBinlog;
        this.binlogAssigned = binlogAssigned;
        this.lowWatermark = lowWatermark;
        this.highWatermark = highWatermark;
        this.snapshotPlanned = snapshotPlanned;
        this.totalSnapshotSplits = totalSnapshotSplits;
        this.pendingSnapshots = pendingSnapshots;
        this.assignedSnapshots = assignedSnapshots;
        this.finishedSnapshots = finishedSnapshots;
        this.finishedSplitInfos = finishedSplitInfos;
        this.nextTableIndex = nextTableIndex;
        this.nextChunkIndex = nextChunkIndex;
        this.nextChunkRange = nextChunkRange;
    }

    public Phase phase() {
        return phase;
    }

    public MySqlBinlogSplit pendingBinlog() {
        return pendingBinlog;
    }

    public boolean binlogAssigned() {
        return binlogAssigned;
    }

    public DebeziumOffset lowWatermark() {
        return lowWatermark;
    }

    public DebeziumOffset highWatermark() {
        return highWatermark;
    }

    public boolean snapshotPlanned() {
        return snapshotPlanned;
    }

    public int totalSnapshotSplits() {
        return totalSnapshotSplits;
    }

    public List<MySqlSnapshotSplit> pendingSnapshots() {
        return pendingSnapshots;
    }

    public Map<String, MySqlSnapshotSplit> assignedSnapshots() {
        return assignedSnapshots;
    }

    public Set<String> finishedSnapshots() {
        return finishedSnapshots;
    }

    public List<FinishedSnapshotSplitInfo> finishedSplitInfos() {
        return finishedSplitInfos;
    }

    public int nextTableIndex() {
        return nextTableIndex;
    }

    public int nextChunkIndex() {
        return nextChunkIndex;
    }

    public ChunkRange nextChunkRange() {
        return nextChunkRange;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof HybridPendingSplitsState other)) return false;
        return Objects.equals(phase, other.phase)
                && Objects.equals(pendingBinlog, other.pendingBinlog)
                && binlogAssigned == other.binlogAssigned
                && Objects.equals(lowWatermark, other.lowWatermark)
                && Objects.equals(highWatermark, other.highWatermark)
                && snapshotPlanned == other.snapshotPlanned
                && totalSnapshotSplits == other.totalSnapshotSplits
                && Objects.equals(pendingSnapshots, other.pendingSnapshots)
                && Objects.equals(assignedSnapshots, other.assignedSnapshots)
                && Objects.equals(finishedSnapshots, other.finishedSnapshots)
                && Objects.equals(finishedSplitInfos, other.finishedSplitInfos)
                && nextTableIndex == other.nextTableIndex
                && nextChunkIndex == other.nextChunkIndex
                && Objects.equals(nextChunkRange, other.nextChunkRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, pendingBinlog, binlogAssigned, lowWatermark, highWatermark, snapshotPlanned, totalSnapshotSplits, pendingSnapshots, assignedSnapshots, finishedSnapshots, finishedSplitInfos, nextTableIndex, nextChunkIndex, nextChunkRange);
    }

    @Override
    public String toString() {
        return "HybridPendingSplitsState{phase=" + phase + ", chunks=" + totalSnapshotSplits
                + ", checkpoint=redacted}";
    }
}
