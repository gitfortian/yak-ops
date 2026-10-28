package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/**
 * Coordinator's current Snapshot fence count and Binlog assignment generation.
 *
 * <p>A reader with a restored paused split uses the generation to reject stale replies
 * and request complete metadata. An unfinished Snapshot cycle must not resume Binlog.
 */
public final class LatestFinishedSplitsNumberEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;

    private final int latestFinishedSplitsNumber;
    private final long binlogAssignmentGeneration;
    private final boolean snapshotFinished;

    public LatestFinishedSplitsNumberEvent(
            int latestFinishedSplitsNumber, long binlogAssignmentGeneration, boolean snapshotFinished) {
        if (latestFinishedSplitsNumber < 0 || binlogAssignmentGeneration < 0) {
            throw new IllegalArgumentException("Invalid completed Snapshot count or Binlog generation");
        }
        this.latestFinishedSplitsNumber = latestFinishedSplitsNumber;
        this.binlogAssignmentGeneration = binlogAssignmentGeneration;
        this.snapshotFinished = snapshotFinished;
    }

    public int getLatestFinishedSplitsNumber() {
        return latestFinishedSplitsNumber;
    }

    public long getBinlogAssignmentGeneration() {
        return binlogAssignmentGeneration;
    }

    public boolean isSnapshotFinished() {
        return snapshotFinished;
    }
}
