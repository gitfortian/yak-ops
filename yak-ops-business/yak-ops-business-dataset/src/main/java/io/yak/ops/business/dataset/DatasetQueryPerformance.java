package io.yak.ops.business.dataset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** Diagnostic trace for one Dataset Query Runtime execution. */
public record DatasetQueryPerformance(
    String queryId,
    @JsonSerialize(using = ToStringSerializer.class) long datasetId,
    String datasetName,
    @JsonSerialize(using = ToStringSerializer.class) long datasetVersionId,
    int datasetVersionNo,
    String sourceType,
    String dataSourceId,
    String sql,
    long waitMillis,
    long prepareMillis,
    long executeMillis,
    long transferMillis,
    long totalMillis,
    int returnedRows,
    boolean truncated,
    Instant startedAt) {
}
