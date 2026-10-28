package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.core.api.connector.source.SourceEvent;

/**
 * Reader ACK that all metadata groups for one Binlog assignment generation are assembled.
 * A stale generation must never arm the release checkpoint of a replacement reader.
 */
public final class BinlogSplitMetaAssembledEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;

    private final long binlogAssignmentGeneration;

    public BinlogSplitMetaAssembledEvent(long binlogAssignmentGeneration) {
        if (binlogAssignmentGeneration < 0) {
            throw new IllegalArgumentException("Invalid Binlog assignment generation");
        }
        this.binlogAssignmentGeneration = binlogAssignmentGeneration;
    }

    public long getBinlogAssignmentGeneration() {
        return binlogAssignmentGeneration;
    }
}
