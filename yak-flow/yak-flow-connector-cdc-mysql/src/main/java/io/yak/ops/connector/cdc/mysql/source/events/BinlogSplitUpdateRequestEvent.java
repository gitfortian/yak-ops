package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/** Requests the Binlog owner to pause its current split before new Snapshot chunks are read. */
public final class BinlogSplitUpdateRequestEvent implements SourceEvent {
    private final long generation;

    public BinlogSplitUpdateRequestEvent(long generation) {
        if (generation < 0) throw new IllegalArgumentException("Binlog update generation must not be negative");
        this.generation = generation;
    }

    public long getGeneration() {
        return generation;
    }
}
