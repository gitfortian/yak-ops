package io.yak.ops.connector.cdc.mysql.source.split;

import java.util.Objects;

/** State of the snapshot split, including checkpoint-gated normalized output. */
public final class MySqlSnapshotSplitState extends MySqlSplitState {

    private MySqlSnapshotSplit snapshot;
    private Long outputCheckpoint;
    private boolean outputDurable;

    public MySqlSnapshotSplitState(MySqlSnapshotSplit split) {
        super(split);
        snapshot = split;
        outputDurable = split.prepared();
    }

    @Override
    public String sourceId() {
        return snapshot.fingerprint();
    }

    @Override
    public String snapshotSplitId() {
        if (!snapshot.prepared()) {
            throw new IllegalStateException("Snapshot event identity requires frozen output");
        }
        return snapshot.splitId();
    }

    @Override
    public void prepareSnapshot(MySqlSnapshotSplit prepared) {
        if (snapshot.prepared()
                || !snapshot.splitId().equals(prepared.splitId())
                || !snapshot.tableId().equals(prepared.tableId())
                || !snapshot.fingerprint().equals(prepared.fingerprint())
                || !Objects.equals(snapshot.lowerInclusive(), prepared.lowerInclusive())
                || !Objects.equals(snapshot.upperExclusive(), prepared.upperExclusive())
                || !Objects.equals(snapshot.chunkRange(), prepared.chunkRange())) {
            throw new IllegalStateException("Prepared snapshot differs from its assigned range");
        }
        snapshot = prepared;
        outputDurable = false;
    }

    @Override
    public void snapshotCheckpoint(long checkpointId) {
        if (snapshot.output() != null && !outputDurable && outputCheckpoint == null) {
            outputCheckpoint = checkpointId;
        }
    }

    @Override
    public String completeCheckpoint(long checkpointId) {
        if (outputCheckpoint != null && checkpointId >= outputCheckpoint) {
            outputDurable = true;
            outputCheckpoint = null;
            return snapshot.splitId();
        }
        return null;
    }

    @Override
    public void emitSnapshotRows(int position) {
        if (!outputDurable) {
            throw new IllegalStateException("Snapshot output has no completed preparation checkpoint");
        }
        snapshot = snapshot.withOutputPosition(position);
    }

    @Override
    public MySqlSnapshotSplit toMySqlSplit() {
        return snapshot;
    }

    @Override
    public MySqlSplit checkpoint(byte[] history) {
        return toMySqlSplit();
    }
}
