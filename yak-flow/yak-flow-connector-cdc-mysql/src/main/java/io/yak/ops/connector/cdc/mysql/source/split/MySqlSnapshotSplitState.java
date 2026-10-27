package io.yak.ops.connector.cdc.mysql.source.split;

import java.util.Objects;

/**
 * State of an incremental snapshot split.
 *
 * <p>Source row images are never stored in split state. A split is completed only
 * after the entire Low/High/Backfill normalized chunk was emitted in one mailbox
 * operation; incomplete splits are restored as their original scan boundaries.
 */
public final class MySqlSnapshotSplitState extends MySqlSplitState {

    private MySqlSnapshotSplit snapshot;

    public MySqlSnapshotSplitState(MySqlSnapshotSplit split) {
        super(split);
        if (split.output() != null || split.outputPosition() != 0) {
            throw new IllegalArgumentException("Prepared row images are not recoverable split state");
        }
        snapshot = split;
    }

    @Override
    public String snapshotSplitId() {
        return snapshot.splitId();
    }

    @Override
    public void prepareSnapshot(MySqlSnapshotSplit prepared) {
        if (snapshot.prepared()
                || !snapshot.splitId().equals(prepared.splitId())
                || !snapshot.tableId().equals(prepared.tableId())
                || !Objects.equals(snapshot.lowerInclusive(), prepared.lowerInclusive())
                || !Objects.equals(snapshot.upperExclusive(), prepared.upperExclusive())
                || !Objects.equals(snapshot.chunkRange(), prepared.chunkRange())
                || prepared.output() == null) {
            throw new IllegalStateException("Normalized chunk differs from assigned snapshot range");
        }
        // Mark finished only after the emitter has published the complete normalized chunk.
        snapshot = prepared.withOutputPosition(prepared.output().rows().size());
    }

    @Override
    public MySqlSnapshotSplit toMySqlSplit() {
        return snapshot;
    }

    @Override
    public MySqlSplit checkpoint(byte[] history) {
        if (snapshot.output() != null) {
            throw new IllegalStateException("Checkpoint must never persist snapshot row images");
        }
        return snapshot;
    }
}
