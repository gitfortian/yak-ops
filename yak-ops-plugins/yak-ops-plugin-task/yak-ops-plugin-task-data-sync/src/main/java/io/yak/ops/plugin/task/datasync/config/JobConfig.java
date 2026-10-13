package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Parsed single-source, single-sink definition retaining the persisted JSON v2 structure. */
public final class JobConfig {

    private final ReadonlyConfig parameters;
    private final ReadonlyConfig source;
    private final ReadonlyConfig sink;

    private JobConfig(ReadonlyConfig parameters, ReadonlyConfig source, ReadonlyConfig sink) {
        this.parameters = parameters;
        this.source = source;
        this.sink = sink;
    }

    /** Resolves structured endpoint configurations and adds the empty transform default. */
    public static JobConfig from(ReadonlyConfig parameters) {
        Objects.requireNonNull(parameters, "parameters");
        Map<String, Object> source = parameters.get(JobOptions.SOURCE);
        Map<String, Object> sink = parameters.get(JobOptions.SINK);
        List<Object> transforms = parameters.get(JobOptions.TRANSFORM);
        if (source == null || sink == null || transforms == null || !transforms.isEmpty()) {
            throw new IllegalArgumentException("DATA_SYNC requires a Source, Sink and empty transforms");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("source", source);
        values.put("transform", transforms);
        values.put("sink", sink);
        return new JobConfig(
                ReadonlyConfig.fromMap(values), ReadonlyConfig.fromMap(source), ReadonlyConfig.fromMap(sink));
    }

    public ReadonlyConfig parameters() {
        return parameters;
    }

    public ReadonlyConfig source() {
        return source;
    }

    public ReadonlyConfig sink() {
        return sink;
    }
}
