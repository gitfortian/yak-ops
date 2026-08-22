package io.yak.ops.business.analysis;

public record AnalysisMetricBinding(
    String fieldId,
    AnalysisAggregation aggregation) {
}
