package io.yak.ops.business.analysis;

import java.time.Instant;

/** Reusable BI analysis definition. Layout remains owned by Dashboard, not by Analysis. */
public record AnalysisAsset(
    long id,
    String name,
    String description,
    long datasetId,
    AnalysisChartType chartType,
    AnalysisQuerySpec querySpec,
    AnalysisVisualConfig visualConfig,
    Instant createTime,
    Instant updateTime) {
}
