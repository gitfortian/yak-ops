package io.yak.ops.business.analysis;

/** Analysis keeps semantic operators; SQL-specific LIKE remains a Dataset Runtime detail. */
public enum AnalysisFilterOperator {
  EQ,
  NE,
  GT,
  GTE,
  LT,
  LTE,
  CONTAINS
}
