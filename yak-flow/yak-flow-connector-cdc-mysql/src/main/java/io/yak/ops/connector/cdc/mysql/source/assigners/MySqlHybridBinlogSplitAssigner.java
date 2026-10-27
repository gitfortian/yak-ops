package io.yak.ops.connector.cdc.mysql.source.assigners;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Objects;

/** Holds the Hybrid bootstrap Binlog split before Snapshot/Handoff ownership is established. */
final class MySqlHybridBinlogSplitAssigner {

    private MySqlBinlogSplit pending;
    private boolean assigned;

    MySqlHybridBinlogSplitAssigner(String fingerprint) {
        pending = MySqlBinlogSplit.forInitialSnapshot(Objects.requireNonNull(fingerprint, "fingerprint"));
    }

    MySqlHybridBinlogSplitAssigner(MySqlBinlogSplit pending, boolean assigned) {
        if (assigned == (pending != null)) {
            throw new IllegalArgumentException("Binlog assignment and pending state conflict");
        }
        this.pending = pending;
        this.assigned = assigned;
    }

    public MySqlBinlogSplit next(int subtaskId) {
        if (subtaskId != 0) {
            return null;
        }
        MySqlBinlogSplit result = pending;
        if (result != null) {
            pending = null;
            assigned = true;
        }
        return result;
    }

    public void addBack(MySqlBinlogSplit split) {
        if (pending != null || !assigned) {
            throw new IllegalStateException("Binlog is not assigned");
        }
        pending = Objects.requireNonNull(split, "split");
        assigned = false;
    }

    public boolean assigned() {
        return assigned;
    }

    public MySqlBinlogSplit pending() {
        return pending;
    }
}
