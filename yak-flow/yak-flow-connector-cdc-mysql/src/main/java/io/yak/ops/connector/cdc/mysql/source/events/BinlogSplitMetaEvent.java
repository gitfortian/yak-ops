package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One bounded group of completed Snapshot Chunk metadata sent to the Binlog owner.
 *
 * <p>Follows Flink CDC's BinlogSplitMetaEvent; the payload uses YakFlow's immutable
 * FinishedSnapshotSplitInfo and Debezium 2.7 offsets instead of Flink's byte arrays.
 */
public final class BinlogSplitMetaEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;

    private final int metaGroupId;
    private final int totalGroups;
    private final int totalFinishedSplitSize;
    private final long binlogAssignmentGeneration;
    private final List<byte[]> metaGroup;

    public BinlogSplitMetaEvent(
            int metaGroupId,
            int totalGroups,
            int totalFinishedSplitSize,
            long binlogAssignmentGeneration,
            List<byte[]> metaGroup) {
        if (metaGroupId < 0
                || totalGroups <= 0
                || metaGroupId >= totalGroups
                || totalFinishedSplitSize <= 0
                || binlogAssignmentGeneration < 0) {
            throw new IllegalArgumentException("Invalid Binlog metadata group header");
        }
        Objects.requireNonNull(metaGroup, "metaGroup");
        List<byte[]> copies = new ArrayList<>(metaGroup.size());
        for (byte[] entry : metaGroup) {
            Objects.requireNonNull(entry, "metadata frame");
            if (entry.length == 0 || entry.length > 8 * 1024 * 1024) {
                throw new IllegalArgumentException("Invalid Binlog metadata frame length");
            }
            copies.add(entry.clone());
        }
        this.metaGroup = List.copyOf(copies);
        if (this.metaGroup.isEmpty() || this.metaGroup.size() > totalFinishedSplitSize) {
            throw new IllegalArgumentException("Invalid Binlog metadata group contents");
        }
        this.metaGroupId = metaGroupId;
        this.totalGroups = totalGroups;
        this.totalFinishedSplitSize = totalFinishedSplitSize;
        this.binlogAssignmentGeneration = binlogAssignmentGeneration;
    }

    public String getSplitId() {
        return MySqlBinlogSplit.ID;
    }

    public int getMetaGroupId() {
        return metaGroupId;
    }

    public int getTotalGroups() {
        return totalGroups;
    }

    public int getTotalFinishedSplitSize() {
        return totalFinishedSplitSize;
    }

    public long getBinlogAssignmentGeneration() {
        return binlogAssignmentGeneration;
    }

    public List<byte[]> getMetaGroup() {
        List<byte[]> copies = new ArrayList<>(metaGroup.size());
        metaGroup.forEach(entry -> copies.add(entry.clone()));
        return List.copyOf(copies);
    }
}
