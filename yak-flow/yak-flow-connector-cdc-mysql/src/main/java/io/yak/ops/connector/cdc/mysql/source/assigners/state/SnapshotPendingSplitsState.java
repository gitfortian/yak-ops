package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.AssignerStatus;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final Map<String, DebeziumOffset> splitFinishedOffsets;
    private final List<FinishedSnapshotSplitInfo> finishedSplitInfos;
    private final int nextTableIndex;
    private final ChunkSplitterState chunkSplitterState;
    private final ChunkRange nextChunkRange;
    private final List<TableId> alreadyProcessedTables;
    private final List<TableId> remainingTables;
    private final AssignerStatus assignerStatus;
    private final boolean tableIdCaseSensitive;
    private final boolean remainingTablesCheckpointed;
    private final boolean snapshotMetaReleased;

    /** Creates the initial Snapshot State before table discovery metadata is available. */
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
        this(
                snapshotPlanned,
                totalSnapshotSplits,
                remainingSplits,
                assignedSplits,
                finishedSplits,
                finishedSplitInfos,
                nextTableIndex,
                nextChunkIndex,
                nextChunkRange,
                List.of(),
                List.of(),
                AssignerStatus.INITIAL_ASSIGNING,
                false,
                false,
                false);
    }

    public SnapshotPendingSplitsState(
            boolean snapshotPlanned,
            int totalSnapshotSplits,
            List<MySqlSnapshotSplit> remainingSplits,
            Map<String, MySqlSnapshotSplit> assignedSplits,
            Set<String> finishedSplits,
            List<FinishedSnapshotSplitInfo> finishedSplitInfos,
            int nextTableIndex,
            int nextChunkIndex,
            ChunkRange nextChunkRange,
            List<TableId> alreadyProcessedTables,
            List<TableId> remainingTables,
            AssignerStatus assignerStatus,
            boolean tableIdCaseSensitive,
            boolean remainingTablesCheckpointed,
            boolean snapshotMetaReleased) {
        this.remainingSplits = List.copyOf(Objects.requireNonNull(remainingSplits, "remainingSplits"));
        this.assignedSplits = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(assignedSplits, "assignedSplits")));
        this.finishedSplitInfos = List.copyOf(Objects.requireNonNull(finishedSplitInfos, "finishedSplitInfos"));
        Map<String, DebeziumOffset> finishedOffsets = new LinkedHashMap<>();
        for (FinishedSnapshotSplitInfo info : this.finishedSplitInfos) {
            if (finishedOffsets.putIfAbsent(info.splitId(), info.highWatermark()) != null) {
                throw new IllegalArgumentException("Duplicate finished snapshot split metadata");
            }
        }
        this.splitFinishedOffsets = Collections.unmodifiableMap(finishedOffsets);
        this.alreadyProcessedTables =
                List.copyOf(Objects.requireNonNull(alreadyProcessedTables, "alreadyProcessedTables"));
        this.remainingTables = List.copyOf(Objects.requireNonNull(remainingTables, "remainingTables"));
        this.assignerStatus = Objects.requireNonNull(assignerStatus, "assignerStatus");
        if (nextTableIndex < 0
                || nextChunkIndex < 0
                || nextChunkIndex > 10_000
                || (nextChunkIndex > 0 && nextChunkRange == null)
                || (nextChunkRange != null
                        && (nextChunkIndex < 1 || nextChunkRange.start() == null || nextChunkRange.end() != null))) {
            throw new IllegalArgumentException("Invalid snapshot chunk discovery cursor");
        }
        Set<String> ids = new HashSet<>(this.splitFinishedOffsets.keySet());
        if (!snapshotMetaReleased && !ids.equals(Objects.requireNonNull(finishedSplits, "finishedSplits"))) {
            throw new IllegalArgumentException("Finished split IDs have incomplete watermark metadata");
        }
        if (snapshotMetaReleased
                && (!snapshotPlanned
                        || !this.remainingSplits.isEmpty()
                        || !this.assignedSplits.isEmpty()
                        || !this.splitFinishedOffsets.isEmpty()
                        || !this.finishedSplitInfos.isEmpty()
                        || !AssignerStatus.isAssigningFinished(assignerStatus))) {
            throw new IllegalArgumentException("Snapshot metadata may only be released after assignment is finished");
        }
        for (MySqlSnapshotSplit split : this.remainingSplits) {
            if (!ids.add(split.splitId())) throw new IllegalArgumentException("Duplicate pending snapshot split");
        }
        for (var entry : this.assignedSplits.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().splitId()) || !ids.add(entry.getKey())) {
                throw new IllegalArgumentException("Duplicate assigned snapshot split");
            }
        }
        if (totalSnapshotSplits < 0
                || totalSnapshotSplits > 10_000
                || (!snapshotMetaReleased
                        && this.remainingSplits.size() + this.assignedSplits.size() + this.splitFinishedOffsets.size()
                                != totalSnapshotSplits)) {
            throw new IllegalArgumentException("Inconsistent snapshot split accounting");
        }
        if (this.alreadyProcessedTables.stream().anyMatch(Objects::isNull)
                || this.remainingTables.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(this.remainingTables).size() != this.remainingTables.size()) {
            throw new IllegalArgumentException("Invalid snapshot table discovery state");
        }
        this.snapshotPlanned = snapshotPlanned;
        this.totalSnapshotSplits = totalSnapshotSplits;
        this.nextTableIndex = nextTableIndex;
        this.chunkSplitterState = nextChunkRange == null
                ? ChunkSplitterState.NO_SPLITTING_TABLE_STATE
                : new ChunkSplitterState(
                        nextChunkRange.tableId(),
                        ChunkSplitterState.ChunkBound.middleOf(nextChunkRange.start()),
                        nextChunkIndex);
        this.nextChunkRange = nextChunkRange;
        this.tableIdCaseSensitive = tableIdCaseSensitive;
        this.remainingTablesCheckpointed = remainingTablesCheckpointed;
        this.snapshotMetaReleased = snapshotMetaReleased;
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

    /** Snapshot completion is owned by one split-to-offset map, as in Flink CDC. */
    public Map<String, DebeziumOffset> getSplitFinishedOffsets() {
        return splitFinishedOffsets;
    }

    public Set<String> getFinishedSplits() {
        return splitFinishedOffsets.keySet();
    }

    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
        return finishedSplitInfos;
    }

    public int nextTableIndex() {
        return nextTableIndex;
    }

    public int nextChunkIndex() {
        Integer nextChunkId = chunkSplitterState.getNextChunkId();
        return nextChunkId == null ? 0 : nextChunkId;
    }

    public ChunkSplitterState getChunkSplitterState() {
        return chunkSplitterState;
    }

    public ChunkRange nextChunkRange() {
        return nextChunkRange;
    }

    public List<TableId> getAlreadyProcessedTables() {
        return alreadyProcessedTables;
    }

    public List<TableId> getRemainingTables() {
        return remainingTables;
    }

    public AssignerStatus getAssignerStatus() {
        return assignerStatus;
    }

    public boolean isTableIdCaseSensitive() {
        return tableIdCaseSensitive;
    }

    public boolean isRemainingTablesCheckpointed() {
        return remainingTablesCheckpointed;
    }

    public boolean isSnapshotMetaReleased() {
        return snapshotMetaReleased;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SnapshotPendingSplitsState state)) return false;
        return snapshotPlanned == state.snapshotPlanned
                && totalSnapshotSplits == state.totalSnapshotSplits
                && nextTableIndex == state.nextTableIndex
                && chunkSplitterState.equals(state.chunkSplitterState)
                && tableIdCaseSensitive == state.tableIdCaseSensitive
                && remainingTablesCheckpointed == state.remainingTablesCheckpointed
                && snapshotMetaReleased == state.snapshotMetaReleased
                && alreadyProcessedTables.equals(state.alreadyProcessedTables)
                && remainingTables.equals(state.remainingTables)
                && assignerStatus == state.assignerStatus
                && remainingSplits.equals(state.remainingSplits)
                && assignedSplits.equals(state.assignedSplits)
                && splitFinishedOffsets.equals(state.splitFinishedOffsets)
                && finishedSplitInfos.equals(state.finishedSplitInfos)
                && Objects.equals(nextChunkRange, state.nextChunkRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                snapshotPlanned,
                totalSnapshotSplits,
                remainingSplits,
                assignedSplits,
                splitFinishedOffsets,
                finishedSplitInfos,
                nextTableIndex,
                chunkSplitterState,
                nextChunkRange,
                alreadyProcessedTables,
                remainingTables,
                assignerStatus,
                tableIdCaseSensitive,
                remainingTablesCheckpointed,
                snapshotMetaReleased);
    }
}
