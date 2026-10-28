package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.SnapshotPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates the hybrid source's four stages without performing JDBC or Binlog I/O.
 *
 * <p>The transition from HANDOFF to STREAMING is gated by a completed checkpoint
 * containing all completed snapshot split watermarks, not by a final split delivery acknowledgment.
 */
public final class MySqlHybridSplitAssigner implements MySqlSplitAssigner {

    private final MySqlSnapshotSplitAssigner snapshots;
    private MySqlBinlogSplit pendingBinlog;
    private boolean binlogAssigned;
    private HybridPendingSplitsState.Phase phase;
    private DebeziumOffset low;
    private DebeziumOffset high;
    private Long handoffCheckpointId;
    private final Map<String, FinishedSnapshotSplitInfo> completed = new LinkedHashMap<>();
    private int nextTableIndex;
    private int nextChunkIndex;
    private ChunkRange nextChunkRange;

    public MySqlHybridSplitAssigner(StartupOptions startupOptions, HybridPendingSplitsState restored) {
        Objects.requireNonNull(startupOptions, "startupOptions");
        if (startupOptions.startupMode() != StartupMode.INITIAL) {
            throw new UnsupportedOperationException("MySQL Hybrid Source requires initial startup");
        }
        if (restored == null) {
            snapshots = new MySqlSnapshotSplitAssigner();
            pendingBinlog = MySqlBinlogSplit.forInitialSnapshot();
            binlogAssigned = false;
            phase = HybridPendingSplitsState.Phase.BOOTSTRAP;
        } else {
            restored.getSnapshotPendingSplits()
                    .getFinishedSplitInfos()
                    .forEach(info -> completed.put(info.splitId(), info));
            nextTableIndex = restored.getSnapshotPendingSplits().nextTableIndex();
            nextChunkIndex = restored.getSnapshotPendingSplits().nextChunkIndex();
            nextChunkRange = restored.getSnapshotPendingSplits().nextChunkRange();
            phase = restored.phase();
            low = restored.lowWatermark();
            high = restored.highWatermark();
            pendingBinlog = restored.pendingBinlog();
            binlogAssigned = restored.isBinlogSplitAssigned();
            if (binlogAssigned == (pendingBinlog != null)) {
                throw new IllegalArgumentException("Binlog assignment and pending split state conflict");
            }
            snapshots = new MySqlSnapshotSplitAssigner(
                    restored.getSnapshotPendingSplits().getRemainingSplits(),
                    restored.getSnapshotPendingSplits().getAssignedSplits(),
                    restored.getSnapshotPendingSplits().getFinishedSplits(),
                    restored.getSnapshotPendingSplits().totalSnapshotSplits(),
                    restored.getSnapshotPendingSplits().snapshotPlanned());
        }
    }

    @Override
    public void open() {}

    @Override
    public Optional<MySqlSplit> getNext() {
        MySqlBinlogSplit next = nextBinlog();
        return next == null ? Optional.ofNullable(nextSnapshot()) : Optional.of(next);
    }

    public MySqlBinlogSplit nextBinlog() {
        if (pendingBinlog == null) return null;
        MySqlBinlogSplit next = pendingBinlog;
        pendingBinlog = null;
        binlogAssigned = true;
        return next;
    }

    public MySqlSnapshotSplit nextSnapshot() {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT) {
            return null;
        }
        return snapshots.next();
    }

    public void captureLow(DebeziumOffset offset) {
        if (phase != HybridPendingSplitsState.Phase.BOOTSTRAP) {
            if (!Objects.equals(low, offset)) {
                throw new IllegalStateException("Conflicting MySQL hybrid low watermark");
            }
            return;
        }
        low = Objects.requireNonNull(offset, "offset");
        phase = HybridPendingSplitsState.Phase.SNAPSHOT;
    }

    public void plan(List<MySqlSnapshotSplit> splits) {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT) {
            throw new IllegalStateException("Cannot plan snapshot before Binlog anchor");
        }
        snapshots.plan(splits);
    }

    @Override
    public boolean waitingForFinishedSplits() {
        return !snapshots.allFinished();
    }

    @Override
    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
        return finishedSplits();
    }

    @Override
    public void onFinishedSplits(Map<String, DebeziumOffset> splitFinishedOffsets) {
        splitFinishedOffsets.forEach(this::finishSnapshot);
    }

    /** Resolves reported watermarks against coordinator-owned assigned split metadata. */
    public FinishedSnapshotSplitInfo finishSnapshot(String splitId, DebeziumOffset highWatermark) {
        Objects.requireNonNull(splitId, "splitId");
        Objects.requireNonNull(highWatermark, "highWatermark");
        MySqlSnapshotSplit original = snapshots.assigned().get(splitId);
        if (original == null) {
            FinishedSnapshotSplitInfo previous = completed.get(splitId);
            if (previous != null && previous.highWatermark().equals(highWatermark)) return previous;
            throw new IllegalArgumentException("Unknown or conflicting completed snapshot split");
        }
        FinishedSnapshotSplitInfo info = new FinishedSnapshotSplitInfo(
                original.tableId(),
                splitId,
                original.lowerInclusive(),
                original.upperExclusive(),
                highWatermark,
                original.chunkRange());
        finishSnapshot(info);
        return info;
    }

    public void finishSnapshot(FinishedSnapshotSplitInfo info) {
        FinishedSnapshotSplitInfo previous = completed.get(info.splitId());
        if (previous != null) {
            if (!previous.equals(info)) throw new IllegalStateException("Conflicting completed chunk High watermark");
            return;
        }
        MySqlSnapshotSplit original = snapshots.assigned().get(info.splitId());
        if (original == null
                || !original.tableId().equals(info.tableId())
                || !Objects.equals(original.chunkRange(), info.chunkRange())
                || !Objects.equals(original.lowerInclusive(), info.lowerInclusive())
                || !Objects.equals(original.upperExclusive(), info.upperExclusive())) {
            throw new IllegalArgumentException("Completed chunk differs from its assigned range");
        }
        snapshots.complete(info.splitId());
        completed.put(info.splitId(), info);
    }

    /** Atomically persist one discovered range and advance the durable discovery cursor. */
    public void appendPlan(MySqlSnapshotSplit split, int tableCount) {
        ChunkRange range = split.chunkRange();
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT
                || tableCount < 1
                || nextTableIndex < 0
                || nextTableIndex >= tableCount
                || range == null) {
            throw new IllegalStateException("Invalid snapshot discovery transition");
        }
        if ((nextChunkIndex == 0 && (nextChunkRange != null || range.start() != null))
                || (nextChunkIndex > 0
                        && (nextChunkRange == null
                                || nextChunkRange.end() != null
                                || !nextChunkRange.tableId().equals(range.tableId())
                                || !nextChunkRange.column().equals(range.column())
                                || nextChunkRange.type() != range.type()
                                || !Objects.equals(nextChunkRange.start(), range.start())))) {
            throw new IllegalStateException("Snapshot range differs from the durable discovery cursor");
        }

        boolean tableComplete = range.last();
        int nextTable = tableComplete ? nextTableIndex + 1 : nextTableIndex;
        int nextChunk = tableComplete ? 0 : nextChunkIndex + 1;
        ChunkRange nextRange =
                tableComplete ? null : new ChunkRange(range.tableId(), range.column(), range.type(), range.end(), null);
        snapshots.append(List.of(split), nextTable == tableCount);
        nextTableIndex = nextTable;
        nextChunkIndex = nextChunk;
        nextChunkRange = nextRange;
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return List.copyOf(completed.values());
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

    public int pendingSnapshotCount() {
        return snapshots.pending().size();
    }

    public boolean snapshotComplete() {
        return phase == HybridPendingSplitsState.Phase.SNAPSHOT && snapshots.allFinished();
    }

    public void captureHigh(DebeziumOffset offset) {
        if (!snapshotComplete()) {
            throw new IllegalStateException("High watermark requires all snapshot splits to finish");
        }
        high = Objects.requireNonNull(offset, "offset");
        phase = HybridPendingSplitsState.Phase.HANDOFF;
    }

    public boolean restoredHandoffReady() {
        return phase == HybridPendingSplitsState.Phase.HANDOFF && handoffCheckpointId == null;
    }

    /**
     * Resumes a HANDOFF state loaded from an already completed durable checkpoint.
     *
     * <p>Fresh in-memory attempts must instead wait for notifyCheckpointComplete.
     */
    public void resumeRestoredHandoff() {
        if (phase != HybridPendingSplitsState.Phase.HANDOFF || handoffCheckpointId != null) {
            throw new IllegalStateException("Expected a restored completed handoff checkpoint");
        }
        phase = HybridPendingSplitsState.Phase.STREAMING;
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        if (phase == HybridPendingSplitsState.Phase.HANDOFF
                && handoffCheckpointId != null
                && checkpointId >= handoffCheckpointId) {
            phase = HybridPendingSplitsState.Phase.STREAMING;
            handoffCheckpointId = null;
        }
    }

    @Override
    public HybridPendingSplitsState snapshotState(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("Checkpoint ID must not be negative");
        }
        if (phase == HybridPendingSplitsState.Phase.HANDOFF && handoffCheckpointId == null) {
            handoffCheckpointId = checkpointId;
        }
        return new HybridPendingSplitsState(
                new SnapshotPendingSplitsState(
                        snapshots.planned(),
                        snapshots.total(),
                        snapshots.pending(),
                        snapshots.assigned(),
                        snapshots.finished(),
                        finishedSplits(),
                        nextTableIndex,
                        nextChunkIndex,
                        nextChunkRange),
                binlogAssigned,
                pendingBinlog,
                phase,
                low,
                high);
    }

    @Override
    public void addSplits(Collection<MySqlSplit> splits) {
        for (MySqlSplit split : splits) {
            if (split instanceof MySqlSnapshotSplit snapshot) {
                addBack(snapshot);
            } else if (split instanceof MySqlBinlogSplit binlogSplit) {
                addBack(binlogSplit);
            } else {
                throw new IllegalArgumentException("Unsupported returned MySQL split");
            }
        }
    }

    public void addBack(MySqlBinlogSplit split) {
        if (pendingBinlog != null || !binlogAssigned) {
            throw new IllegalStateException("Binlog is not assigned");
        }
        pendingBinlog = Objects.requireNonNull(split, "split");
        binlogAssigned = false;
    }

    public void addBack(MySqlSnapshotSplit split) {
        snapshots.addBack(split);
    }

    public HybridPendingSplitsState.Phase phase() {
        return phase;
    }

    public DebeziumOffset low() {
        return low;
    }

    public DebeziumOffset high() {
        return high;
    }

    public boolean snapshotPlanned() {
        return snapshots.planned();
    }

    @Override
    public AssignerStatus getAssignerStatus() {
        return phase == HybridPendingSplitsState.Phase.BOOTSTRAP
                        || phase == HybridPendingSplitsState.Phase.SNAPSHOT
                ? AssignerStatus.INITIAL_ASSIGNING
                : AssignerStatus.INITIAL_ASSIGNING_FINISHED;
    }

    @Override
    public void startAssignNewlyAddedTables() {
        throw new UnsupportedOperationException("Dynamic MySQL table discovery is not enabled");
    }

    @Override
    public boolean noMoreSplits() {
        return binlogAssigned && snapshots.planned() && snapshots.pending().isEmpty();
    }

    @Override
    public void onBinlogSplitUpdated() {
        throw new UnsupportedOperationException("Dynamic MySQL table discovery is not enabled");
    }

    @Override
    public void close() {}
}
