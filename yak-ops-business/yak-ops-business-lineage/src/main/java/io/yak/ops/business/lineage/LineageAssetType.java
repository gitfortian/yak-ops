package io.yak.ops.business.lineage;

/** First-stage asset types shared by data development, catalog and BI consumption. */
public enum LineageAssetType {
  TABLE,
  COLUMN,
  SQL_TASK,
  DATASET,
  DATASET_FIELD,
  CHART,
  DASHBOARD
}
