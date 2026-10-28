package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/** The Binlog owner requests the next bounded group of finished snapshot metadata. */
public final class BinlogSplitMetaRequestEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;

    private final int groupId;
    private final long generation;

    public BinlogSplitMetaRequestEvent(int groupId, long generation) {
        if (groupId < 0 || generation < 0) throw new IllegalArgumentException("Invalid Binlog group request");
        this.groupId = groupId;
        this.generation = generation;
    }

    public int getGroupId() {
        return groupId;
    }

    public long getGeneration() {
        return generation;
    }
}
