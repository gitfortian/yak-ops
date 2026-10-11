package io.yak.ops.plugin.task.datasync;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.util.List;

/**
 * Immutable single-source, single-sink DATA_SYNC definition.
 *
 * <p>Transforms are reserved but must remain empty until a real transform implementation exists.
 * Neither Task retry policies nor YakFlow physical connector configurations are persisted here.
 */
public record SyncParameters(SourceParameters source, List<JsonNode> transform, SinkParameters sink)
        implements TaskParameters {
    public SyncParameters {
        if (transform == null) {
            transform = List.of();
        } else {
            transform = List.copyOf(transform);
        }
    }
}
