package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/**
 * Requests current Snapshot completion metadata after a paused Binlog split is restored.
 *
 * <p>Follows Flink CDC's recovery handshake: the reader cannot infer the latest set of
 * finished Snapshot splits from a checkpointed, possibly older Binlog split.
 */
public final class LatestFinishedSplitsNumberRequestEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;

    private final int restoredFinishedSplitsNumber;

    public LatestFinishedSplitsNumberRequestEvent(int restoredFinishedSplitsNumber) {
        if (restoredFinishedSplitsNumber < 0) {
            throw new IllegalArgumentException("Restored finished Snapshot count cannot be negative");
        }
        this.restoredFinishedSplitsNumber = restoredFinishedSplitsNumber;
    }

    public int getRestoredFinishedSplitsNumber() {
        return restoredFinishedSplitsNumber;
    }
}
