package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.core.api.connector.source.SourceSplit;
import java.util.Objects;

/** The split of MySQL source, identified by a stable split ID. */
public abstract class MySqlSplit implements SourceSplit {

    protected final String splitId;

    public MySqlSplit(String splitId) {
        this.splitId = Objects.requireNonNull(splitId, "splitId");
    }

    /** Checks whether this split is a snapshot split. */
    public final boolean isSnapshotSplit() {
        return getClass() == MySqlSnapshotSplit.class;
    }

    /** Checks whether this split is a binlog split. */
    public final boolean isBinlogSplit() {
        return getClass() == MySqlBinlogSplit.class;
    }

    /** Casts this split into a {@link MySqlSnapshotSplit}. */
    public final MySqlSnapshotSplit asSnapshotSplit() {
        return (MySqlSnapshotSplit) this;
    }

    /** Casts this split into a {@link MySqlBinlogSplit}. */
    public final MySqlBinlogSplit asBinlogSplit() {
        return (MySqlBinlogSplit) this;
    }

    @Override
    public String splitId() {
        return splitId;
    }
}
