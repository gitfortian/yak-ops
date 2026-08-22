package io.yak.ops.business.dataset.service.event;

/** Application event requesting a derived-lineage refresh after the Dataset transaction commits. */
public record DatasetLineageRefreshRequested(long datasetId) {

  public DatasetLineageRefreshRequested {
    if (datasetId <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
  }
}
