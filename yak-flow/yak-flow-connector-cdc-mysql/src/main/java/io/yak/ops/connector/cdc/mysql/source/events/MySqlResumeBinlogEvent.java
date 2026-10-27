package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.List;
import java.util.Objects;

/** Releases the checkpoint-protected Binlog handoff with its complete per-chunk filter metadata. */
public class MySqlResumeBinlogEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final DebeziumOffset highWatermark;
    private final List<FinishedSnapshotSplitInfo> finishedSplits;

    public MySqlResumeBinlogEvent(DebeziumOffset highWatermark, List<FinishedSnapshotSplitInfo> finishedSplits) {
        this.highWatermark = Objects.requireNonNull(highWatermark, "highWatermark");
        this.finishedSplits = List.copyOf(Objects.requireNonNull(finishedSplits, "finishedSplits"));
        if (this.finishedSplits.isEmpty()) {
            throw new IllegalArgumentException("Binlog handoff requires finished chunk metadata");
        }
    }

    public DebeziumOffset highWatermark() {
        return highWatermark;
    }

    public List<FinishedSnapshotSplitInfo> finishedSplits() {
        return finishedSplits;
    }

    @Override
    public String toString() {
        return "MySqlResumeBinlogEvent{checkpoint=redacted}";
    }
}
