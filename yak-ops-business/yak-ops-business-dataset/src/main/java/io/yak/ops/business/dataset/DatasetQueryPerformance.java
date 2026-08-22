package io.yak.ops.business.dataset;

import java.time.Instant;

/** Diagnostic trace for one Dataset Query Runtime execution. */
public record DatasetQueryPerformance(
    String queryId,
    long datasetId,
    String datasetName,
    long datasetVersionId,
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
