package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.Objects;

/** Reports the first durable resume anchor captured before any snapshot split is assigned. */
public class MySqlLowWatermarkEvent implements SourceEvent {

    private static final long serialVersionUID = 1L;

    private final DebeziumOffset offset;

    public MySqlLowWatermarkEvent(DebeziumOffset offset) {
        this.offset = Objects.requireNonNull(offset, "offset");
    }

    public DebeziumOffset offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "MySqlLowWatermarkEvent{offset=redacted}";
    }
}
