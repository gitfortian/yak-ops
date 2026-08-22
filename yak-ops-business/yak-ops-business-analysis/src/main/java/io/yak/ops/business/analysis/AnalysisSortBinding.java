package io.yak.ops.business.analysis;

public record AnalysisSortBinding(
    String fieldId,
    AnalysisAggregation aggregation,
    AnalysisSortDirection direction) {
}
