package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.source.assigners.state.BinlogPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import java.util.List;
import java.util.Objects;

/** Assigns the selected stream-only startup to the only replication reader. */
public final class MySqlBinlogSplitAssigner implements MySqlSplitAssigner<MySqlBinlogSplit, BinlogPendingSplitsState> {

    private MySqlBinlogSplit pending;
    private boolean assigned;

    public MySqlBinlogSplitAssigner(StartupOptions startupOptions, BinlogPendingSplitsState restored) {
        Objects.requireNonNull(startupOptions, "startupOptions");
        if (startupOptions.startupMode() != StartupMode.LATEST_OFFSET) {
            throw new UnsupportedOperationException("MySQL Binlog Source requires latest-offset startup");
        }
        if (restored == null) {
            pending = new MySqlBinlogSplit(null, new byte[0]);
        } else {
            pending = restored.pendingSplit();
            assigned = restored.splitAssigned();
        }
    }

    @Override
    public MySqlBinlogSplit getNext(int subtaskId) {
        if (subtaskId != 0) {
            return null;
        }
        MySqlBinlogSplit next = pending;
        if (next != null) {
            pending = null;
            assigned = true;
        }
        return next;
    }

    @Override
    public void addSplitsBack(List<MySqlBinlogSplit> splits, int subtaskId) {
        if (subtaskId != 0 || splits.size() != 1 || pending != null || !assigned) {
            throw new IllegalStateException("Unexpected returned MySQL Binlog split");
        }
        MySqlBinlogSplit returned = Objects.requireNonNull(splits.getFirst(), "split");
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
    public boolean notifyCheckpointComplete(long checkpointId) {
        return false;
    }

    public void close() {
        pending = null;
    }
}
