package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Objects;

/**
 * Describes whether the single unbounded Binlog split is pending or reader-owned.
 *
 * <p>An assigned split is checkpointed by its Reader and Runtime assignment tracker,
 * not duplicated into the enumerator state. A returned split remains pending with its offset.
 */
public final class BinlogPendingSplitsState extends PendingSplitsState {
    private final boolean splitAssigned;
    private final MySqlBinlogSplit pendingSplit;

    public BinlogPendingSplitsState(boolean splitAssigned, MySqlBinlogSplit pendingSplit) {
        if (splitAssigned == (pendingSplit != null)) {
            throw new IllegalArgumentException("Invalid MySQL Binlog assignment state");
        }
        this.splitAssigned = splitAssigned;
        this.pendingSplit = pendingSplit;
    }

    public boolean splitAssigned() {
        return splitAssigned;
    }

    public MySqlBinlogSplit pendingSplit() {
        return pendingSplit;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof BinlogPendingSplitsState other)) return false;
        return splitAssigned == other.splitAssigned
                && Objects.equals(pendingSplit, other.pendingSplit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitAssigned, pendingSplit);
    }

    @Override
    public String toString() {
        return "BinlogPendingSplitsState{assigned=" + splitAssigned + ", pending=" + (pendingSplit != null) + "}";
    }
}
