package io.yak.ops.connector.cdc.mysql.source.split;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import java.util.List;
import java.util.Objects;

/** State of the reader, essentially a mutable version of the {@link MySqlSplit}. */
public abstract class MySqlSplitState {

    protected final MySqlSplit split;

    protected MySqlSplitState(MySqlSplit split) {
        this.split = Objects.requireNonNull(split, "split");
    }

    /** Checks whether this split state is a snapshot split state. */
    public final boolean isSnapshotSplitState() {
        return getClass() == MySqlSnapshotSplitState.class;
    }

    /** Checks whether this split state is a binlog split state. */
    public final boolean isBinlogSplitState() {
        return getClass() == MySqlBinlogSplitState.class;
    }

    /** Casts this split state into a {@link MySqlSnapshotSplitState}. */
    public final MySqlSnapshotSplitState asSnapshotSplitState() {
        return (MySqlSnapshotSplitState) this;
    }

    /** Casts this split state into a {@link MySqlBinlogSplitState}. */
    public final MySqlBinlogSplitState asBinlogSplitState() {
        return (MySqlBinlogSplitState) this;
    }

    /** Creates the mutable state from the immutable split. */
    public static MySqlSplitState fromSplit(MySqlSplit split) {
        return split.isSnapshotSplit()
                ? new MySqlSnapshotSplitState(split.asSnapshotSplit())
                : new MySqlBinlogSplitState(split);
    }

    /** Use the current split state to create a new MySql split. */
    public abstract MySqlSplit toMySqlSplit();

    /** Stable source identity, independent of the physical reader attempt. */
    public abstract String sourceId();

    public String snapshotSplitId() {
        throw new IllegalStateException("Not a snapshot split");
    }

    public void captureLow(DebeziumOffset low, byte[] schemaHistory) {
        throw new IllegalStateException("Low watermark is not a Binlog bootstrap event");
    }

    public void prepareSnapshot(MySqlSnapshotSplit prepared) {
        throw new IllegalStateException("Not a snapshot split");
    }

    public void snapshotCheckpoint(long checkpointId) {}

    public String completeCheckpoint(long checkpointId) {
        return null;
    }

    public void emitSnapshotRows(int position) {
        throw new IllegalStateException("Not a snapshot split");
    }

    public void resume(DebeziumOffset high, List<FinishedSnapshotSplitInfo> finished) {}

    public void emitBinlog(DebeziumOffset offset) {
        throw new IllegalStateException("Not a Binlog split");
    }

    public final boolean binlog() {
        return isBinlogSplitState();
    }

    /** A live Binlog checkpoint requires the matching Debezium schema history. */
    public abstract MySqlSplit checkpoint(byte[] history);
}
