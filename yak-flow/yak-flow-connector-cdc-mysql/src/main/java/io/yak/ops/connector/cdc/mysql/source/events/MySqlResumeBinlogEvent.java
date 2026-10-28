package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.Objects;

/**
 * Releases a checkpoint-protected Binlog split after the reader assembles all metadata groups.
 *
 * <p>Flink CDC transmits completed Snapshot metadata separately in bounded groups. Do not
 * duplicate that unbounded list in the Resume event.
 */
public final class MySqlResumeBinlogEvent implements SourceEvent {
    private static final long serialVersionUID = 1L;

    private final DebeziumOffset highWatermark;

    public MySqlResumeBinlogEvent(DebeziumOffset highWatermark) {
        this.highWatermark = Objects.requireNonNull(highWatermark, "highWatermark");
    }

    public DebeziumOffset highWatermark() {
        return highWatermark;
    }

    @Override
    public String toString() {
        return "MySqlResumeBinlogEvent{checkpoint=redacted}";
    }
}
