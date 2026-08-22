package io.yak.ops.business.analysis;

public record AnalysisVisualConfig(
    boolean showLegend,
    boolean showDataLabels,
    boolean smooth,
    boolean showGrid) {
}
