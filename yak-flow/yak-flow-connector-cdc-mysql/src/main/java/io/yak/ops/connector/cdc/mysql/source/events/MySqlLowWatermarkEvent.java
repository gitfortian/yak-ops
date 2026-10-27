package io.yak.ops.connector.cdc.mysql.source.events;

import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.api.connector.source.SourceEvent;
import java.util.Objects;

/** Reports the first durable resume anchor captured before any snapshot split is assigned. */
public record MySqlLowWatermarkEvent(DebeziumOffset offset) implements SourceEvent {

    public MySqlLowWatermarkEvent {
        Objects.requireNonNull(offset, "offset");
    }
}
