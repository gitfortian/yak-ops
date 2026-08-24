package io.yak.ops.business.dataset.lineage;

/** Requests a derived-lineage refresh after the surrounding Dataset transaction commits. */
public record DatasetLineageRefreshRequested(long datasetId) {
  public DatasetLineageRefreshRequested {
    if (datasetId <= 0L) {
      throw new IllegalArgumentException("datasetId 必须大于 0");
    }
  }
}
