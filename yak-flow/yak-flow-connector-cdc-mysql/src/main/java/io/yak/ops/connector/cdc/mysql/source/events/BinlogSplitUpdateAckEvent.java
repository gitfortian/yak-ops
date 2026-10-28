package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/** Confirms that the Binlog owner has installed the updated Snapshot fence metadata and resumed. */
public final class BinlogSplitUpdateAckEvent implements SourceEvent {
    private final long generation;

    public BinlogSplitUpdateAckEvent(long generation) {
        if (generation < 0) throw new IllegalArgumentException("Binlog update generation must not be negative");
        this.generation = generation;
    }

    public long getGeneration() {
        return generation;
    }
}
