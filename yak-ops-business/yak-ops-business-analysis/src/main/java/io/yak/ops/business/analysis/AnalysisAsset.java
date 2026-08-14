package io.yak.ops.business.analysis;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/** Reusable BI analysis definition. Layout remains owned by Dashboard, not by Analysis. */
public record AnalysisAsset(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    String name,
    String description,
    @JsonSerialize(using = ToStringSerializer.class) long datasetId,
    AnalysisChartType chartType,
    AnalysisQuerySpec querySpec,
    AnalysisVisualConfig visualConfig,
    Instant createTime,
    Instant updateTime) {
}

enum AnalysisChartType {
  METRIC,
  BAR,
  LINE,
  PIE,
  TABLE
}

enum AnalysisAggregation {
  SUM,
  AVG,
  COUNT,
  COUNT_DISTINCT,
  MAX,
  MIN
}

enum AnalysisFilterOperator {
  EQ,
  NE,
  GT,
  GTE,
  LT,
  LTE,
  LIKE
}

enum AnalysisSortDirection {
  ASC,
  DESC
}

record AnalysisQuerySpec(
    List<String> dimensions,
    List<AnalysisMetricBinding> metrics,
    List<AnalysisFilterBinding> filters,
    List<AnalysisSortBinding> sorts,
    int limit,
    int timeoutSeconds) {
}

record AnalysisMetricBinding(
    String fieldId,
    AnalysisAggregation aggregation) {
}

record AnalysisFilterBinding(
    String fieldId,
    AnalysisFilterOperator operator,
    Object value) {
}

record AnalysisSortBinding(
    String fieldId,
    AnalysisAggregation aggregation,
    AnalysisSortDirection direction) {
}

record AnalysisVisualConfig(
    boolean showLegend,
    boolean showDataLabels,
    boolean smooth,
    boolean showGrid) {
}
