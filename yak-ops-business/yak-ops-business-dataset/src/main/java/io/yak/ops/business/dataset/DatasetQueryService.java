package io.yak.ops.business.dataset;

import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceReader;
import io.yak.ops.business.dataset.query.DatasetQueryCoordinator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Stable application entry point used by Dashboard/Chart consumers. */
@Service
public class DatasetQueryService {

  private final DatasetQueryCoordinator coordinator;
  private final DatasetQueryPerformanceReader performanceReader;

  public DatasetQueryService(
      DatasetQueryCoordinator coordinator,
      DatasetQueryPerformanceReader performanceReader) {
    this.coordinator = coordinator;
    this.performanceReader = performanceReader;
  }

  public DatasetQueryResult query(long datasetId, DatasetQueryRequest request) {
    return coordinator.query(datasetId, request);
  }

  public List<DatasetQueryPerformance> recentPerformance(Set<Long> datasetIds, int limit) {
    return performanceReader.recent(datasetIds, limit);
  }

  public List<DatasetQueryPerformance> recentPerformance(
      Set<Long> datasetIds, Set<String> queryIds, int limit) {
    return performanceReader.recent(datasetIds, queryIds, limit);
  }
}
