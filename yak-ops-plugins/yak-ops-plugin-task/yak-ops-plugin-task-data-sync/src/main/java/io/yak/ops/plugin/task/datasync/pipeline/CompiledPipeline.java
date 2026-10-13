package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import java.util.Objects;

/** Validated YakFlow topology and the effective configuration for one physical submission. */
public record CompiledPipeline(StreamGraph graph, Configuration configuration) {

    public CompiledPipeline {
        Objects.requireNonNull(graph, "graph");
        configuration = new Configuration(Objects.requireNonNull(configuration, "configuration"));
    }
}
