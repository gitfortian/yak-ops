package io.yak.ops.business.analysis;

/** Normalized definition persisted as one reusable Analysis aggregate. */
public record AnalysisDraft(
    String name,
    String description,
    long datasetId,
    AnalysisChartType chartType,
    AnalysisQuerySpec querySpec,
    AnalysisVisualConfig visualConfig) {
}
