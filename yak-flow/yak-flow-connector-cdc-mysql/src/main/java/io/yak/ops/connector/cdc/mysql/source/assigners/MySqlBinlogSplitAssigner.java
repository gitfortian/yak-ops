package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.BinlogPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Assigns the selected stream-only startup to the only replication reader. */
public final class MySqlBinlogSplitAssigner implements MySqlSplitAssigner {

    private MySqlBinlogSplit pending;
    private boolean assigned;

    public MySqlBinlogSplitAssigner(StartupOptions startupOptions, BinlogPendingSplitsState restored) {
        Objects.requireNonNull(startupOptions, "startupOptions");
        if (!startupOptions.isStreamOnly()) {
            throw new IllegalArgumentException("MySQL Binlog Source requires stream-only startup");
        }
        if (restored == null) {
            pending = new MySqlBinlogSplit(null, new byte[0]);
        } else {
            pending = restored.pendingSplit();
            assigned = restored.splitAssigned();
        }
    }

    @Override
    public void open() {}

    @Override
    public Optional<MySqlSplit> getNext() {
        if (pending == null) return Optional.empty();
        MySqlBinlogSplit next = pending;
        pending = null;
        assigned = true;
        return Optional.of(next);
    }

    @Override
    public boolean waitingForFinishedSplits() {
        return false;
    }

    @Override
    public List<FinishedSnapshotSplitInfo> getFinishedSplitInfos() {
        return Collections.emptyList();
    }

    @Override
    public void onFinishedSplits(Map<String, DebeziumOffset> splitFinishedOffsets) {
        if (!splitFinishedOffsets.isEmpty()) {
            throw new IllegalArgumentException("Binlog-only assigner cannot finish snapshot splits");
        }
    }

    @Override
    public void addSplits(Collection<MySqlSplit> splits) {
        if (splits.size() != 1 || pending != null || !assigned) {
            throw new IllegalStateException("Unexpected returned MySQL Binlog split");
        }
        MySqlSplit split = splits.iterator().next();
        if (!(split instanceof MySqlBinlogSplit returned)) {
            throw new IllegalArgumentException("Binlog-only assigner requires a Binlog split");
        }
        pending = returned;
        assigned = false;
    }

    @Override
    public BinlogPendingSplitsState snapshotState(long checkpointId) {
        if (checkpointId < 0) {
            throw new IllegalArgumentException("Checkpoint ID must not be negative");
        }
        return new BinlogPendingSplitsState(assigned, pending);
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {}

    @Override
    public AssignerStatus getAssignerStatus() {
        return AssignerStatus.INITIAL_ASSIGNING_FINISHED;
    }

    @Override
    public void startAssignNewlyAddedTables() {
        throw new UnsupportedOperationException("Binlog-only source does not assign snapshot tables");
    }

    @Override
    public boolean noMoreSplits() {
        return assigned;
    }

    @Override
    public void onBinlogSplitUpdated() {
        throw new UnsupportedOperationException("Binlog-only source does not update snapshot metadata");
    }

    @Override
    public void close() {
        pending = null;
    }
}
