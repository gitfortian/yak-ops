package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.SourceEvent;
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
    private final List<FinishedSnapshotSplitInfo> metaGroup;

    public BinlogSplitMetaEvent(
            int metaGroupId,
            int totalGroups,
            int totalFinishedSplitSize,
            long binlogAssignmentGeneration,
            List<FinishedSnapshotSplitInfo> metaGroup) {
        if (metaGroupId < 0
                || totalGroups <= 0
                || metaGroupId >= totalGroups
                || totalFinishedSplitSize <= 0
                || binlogAssignmentGeneration < 0) {
            throw new IllegalArgumentException("Invalid Binlog metadata group header");
        }
        this.metaGroup = List.copyOf(Objects.requireNonNull(metaGroup, "metaGroup"));
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

    public List<FinishedSnapshotSplitInfo> getMetaGroup() {
        return metaGroup;
    }
}
