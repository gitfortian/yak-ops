package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import java.util.Objects;

/** Validated YakFlow topology and the effective configuration for one physical submission. */
public record SyncCompiledPipeline(StreamGraph graph, Configuration configuration) {

    public SyncCompiledPipeline {
        Objects.requireNonNull(graph, "graph");
        configuration = new Configuration(Objects.requireNonNull(configuration, "configuration"));
    }
}
