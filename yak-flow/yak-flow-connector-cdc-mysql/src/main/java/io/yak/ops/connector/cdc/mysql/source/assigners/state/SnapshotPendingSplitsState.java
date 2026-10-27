package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checkpoint of the remaining, assigned and completed incremental snapshot splits.
 *
 * <p>Follows Flink CDC's SnapshotPendingSplitsState ownership boundary: chunk planning
 * and assignment remain in the snapshot state, not in the hybrid binlog coordinator.
 * The typed ChunkRange is the YakFlow representation of Flink CDC's chunk splitter cursor.
 */
public final class SnapshotPendingSplitsState extends PendingSplitsState {
    private final boolean snapshotPlanned;
    private final int totalSnapshotSplits;
    private final List<MySqlSnapshotSplit> remainingSplits;
    private final Map<String, MySqlSnapshotSplit> assignedSplits;
    private final Set<String> finishedSplits;
    private final List<FinishedSnapshotSplitInfo> finishedSplitInfos;
    private final int nextTableIndex;
    private final int nextChunkIndex;
    private final ChunkRange nextChunkRange;

    public SnapshotPendingSplitsState(
            boolean snapshotPlanned,
            int totalSnapshotSplits,
            List<MySqlSnapshotSplit> remainingSplits,
            Map<String, MySqlSnapshotSplit> assignedSplits,
            Set<String> finishedSplits,
            List<FinishedSnapshotSplitInfo> finishedSplitInfos,
            int nextTableIndex,
            int nextChunkIndex,
            ChunkRange nextChunkRange) {
        this.remainingSplits = List.copyOf(Objects.requireNonNull(remainingSplits, "remainingSplits"));
        this.assignedSplits = Map.copyOf(Objects.requireNonNull(assignedSplits, "assignedSplits"));
        this.finishedSplits = Set.copyOf(Objects.requireNonNull(finishedSplits, "finishedSplits"));
        this.finishedSplitInfos = List.copyOf(Objects.requireNonNull(finishedSplitInfos, "finishedSplitInfos"));
        if (nextTableIndex < 0
                || nextChunkIndex < 0
                || nextChunkIndex > 10_000
                || (nextChunkIndex > 0 && nextChunkRange == null)
                || (nextChunkRange != null
                        && (nextChunkIndex < 1 || nextChunkRange.start() == null || nextChunkRange.end() != null))) {
            throw new IllegalArgumentException("Invalid snapshot chunk discovery cursor");
        }
        Set<String> ids = new HashSet<>();
        for (FinishedSnapshotSplitInfo info : this.finishedSplitInfos) {
            if (!ids.add(info.splitId())) {
                throw new IllegalArgumentException("Duplicate finished snapshot split metadata");
            }
        }
        if (!ids.equals(this.finishedSplits)) {
            throw new IllegalArgumentException("Finished split IDs have incomplete watermark metadata");
        }
        for (MySqlSnapshotSplit split : this.remainingSplits) {
            if (!ids.add(split.splitId())) {
                throw new IllegalArgumentException("Duplicate pending snapshot split");
            }
        }
        for (var entry : this.assignedSplits.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().splitId()) || !ids.add(entry.getKey())) {
                throw new IllegalArgumentException("Duplicate assigned snapshot split");
            }
        }
        if (totalSnapshotSplits < 0
                || totalSnapshotSplits > 10_000
                || this.remainingSplits.size() + this.assignedSplits.size() + this.finishedSplits.size()
                        != totalSnapshotSplits) {
            throw new IllegalArgumentException("Inconsistent snapshot split accounting");
        }
        this.snapshotPlanned = snapshotPlanned;
        this.totalSnapshotSplits = totalSnapshotSplits;
        this.nextTableIndex = nextTableIndex;
        this.nextChunkIndex = nextChunkIndex;
        this.nextChunkRange = nextChunkRange;
    }

    public boolean snapshotPlanned() {
        return snapshotPlanned;
    }

    public int totalSnapshotSplits() {
        return totalSnapshotSplits;
    }

    public List<MySqlSnapshotSplit> getRemainingSplits() {
        return remainingSplits;
    }

    public Map<String, MySqlSnapshotSplit> getAssignedSplits() {
        return assignedSplits;
    }

    public Set<String> getFinishedSplits() {
        return finishedSplits;
    }

    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
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
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SnapshotPendingSplitsState state)) return false;
        return snapshotPlanned == state.snapshotPlanned
                && totalSnapshotSplits == state.totalSnapshotSplits
                && nextTableIndex == state.nextTableIndex
                && nextChunkIndex == state.nextChunkIndex
                && remainingSplits.equals(state.remainingSplits)
                && assignedSplits.equals(state.assignedSplits)
                && finishedSplits.equals(state.finishedSplits)
                && finishedSplitInfos.equals(state.finishedSplitInfos)
                && Objects.equals(nextChunkRange, state.nextChunkRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotPlanned, totalSnapshotSplits, remainingSplits, assignedSplits,
                finishedSplits, finishedSplitInfos, nextTableIndex, nextChunkIndex, nextChunkRange);
    }
}
