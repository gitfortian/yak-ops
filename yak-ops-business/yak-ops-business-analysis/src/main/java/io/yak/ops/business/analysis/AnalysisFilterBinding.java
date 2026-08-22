package io.yak.ops.business.analysis;

public record AnalysisFilterBinding(
    String fieldId,
    AnalysisFilterOperator operator,
    Object value) {
}
