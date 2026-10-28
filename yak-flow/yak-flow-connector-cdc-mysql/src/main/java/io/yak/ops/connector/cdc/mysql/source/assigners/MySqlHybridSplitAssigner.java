package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.ChunkSplitterState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

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
            phase = restored.phase();
            low = restored.lowWatermark();
            high = restored.highWatermark();
            pendingBinlog = restored.pendingBinlog();
            binlogAssigned = restored.isBinlogSplitAssigned();
            if (binlogAssigned == (pendingBinlog != null)) {
                throw new IllegalArgumentException("Binlog assignment and pending split state conflict");
            }
            snapshots = new MySqlSnapshotSplitAssigner(restored.getSnapshotPendingSplits());
        }
    }

    @Override
    public void open() {
        snapshots.open();
    }

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
        return (MySqlSnapshotSplit) snapshots.getNext().orElse(null);
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
        return snapshots.waitingForFinishedSplits();
    }

    @Override
    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
        return snapshots.getFinishedSplitInfos();
    }

    @Override
    public void onFinishedSplits(Map<String, DebeziumOffset> splitFinishedOffsets) {
        snapshots.onFinishedSplits(splitFinishedOffsets);
    }

    /** The Snapshot Assigner alone owns completion metadata and split attribution. */
    public FinishedSnapshotSplitInfo finishSnapshot(String splitId, DebeziumOffset highWatermark) {
        return snapshots.finishSnapshot(splitId, highWatermark);
    }

    public void finishSnapshot(FinishedSnapshotSplitInfo info) {
        snapshots.finishSnapshot(info);
    }

    public void appendPlan(MySqlSnapshotSplit split, int tableCount) {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT) {
            throw new IllegalStateException("Cannot discover snapshot chunks before Binlog anchor");
        }
        snapshots.appendPlan(split, tableCount);
    }

    /**
     * Delegates asynchronous Chunk discovery to the Snapshot Assigner.
     * Hybrid owns only bootstrap and Binlog handoff state.
     */
    public boolean discoverNextAsync(
            List<TableId> tables,
            MySqlChunkSplitter splitter,
            SplitEnumeratorContext<MySqlSplit> context,
            int maxPendingChunks,
            BiConsumer<MySqlSnapshotSplit, Integer> onDiscovered,
            BiConsumer<TableId, Throwable> onError) {
        if (phase != HybridPendingSplitsState.Phase.SNAPSHOT) return false;
        return snapshots.discoverNextAsync(
                tables, splitter, context, maxPendingChunks, onDiscovered, onError);
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return snapshots.finishedSplits();
    }

    public int nextTableIndex() {
        return snapshots.nextTableIndex();
    }

    public int nextChunkIndex() {
        return snapshots.nextChunkIndex();
    }

    public ChunkSplitterState getChunkSplitterState() {
        return snapshots.getChunkSplitterState();
    }

    public ChunkRange nextChunkRange() {
        return snapshots.nextChunkRange();
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
        snapshots.notifyCheckpointComplete(checkpointId);
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
                snapshots.snapshotState(checkpointId),
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
        if (phase == HybridPendingSplitsState.Phase.BOOTSTRAP || phase == HybridPendingSplitsState.Phase.SNAPSHOT) {
            return AssignerStatus.INITIAL_ASSIGNING;
        }
        return AssignerStatus.INITIAL_ASSIGNING_FINISHED;
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
    public void close() {
        snapshots.close();
    }
}
