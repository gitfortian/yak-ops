package io.yak.ops.business.dataset.observability;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Read side for process-local Dataset query-performance evidence. */
@Component
public class DatasetQueryPerformanceReader {

  static final int MAX_QUERY_LIMIT = 200;

  private final DatasetQueryPerformanceBuffer buffer;

  public DatasetQueryPerformanceReader(DatasetQueryPerformanceBuffer buffer) {
    this.buffer = buffer;
  }

  public List<DatasetQueryPerformance> recent(Set<Long> datasetIds, int requestedLimit) {
    return recent(datasetIds, Set.of(), requestedLimit);
  }

  public List<DatasetQueryPerformance> recent(
      Set<Long> datasetIds, Set<String> queryIds, int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_LIMIT));
    boolean filterDatasets = datasetIds != null && !datasetIds.isEmpty();
    boolean filterQueries = queryIds != null && !queryIds.isEmpty();
    List<DatasetQueryPerformance> result =
        new ArrayList<>(Math.min(limit, buffer.size()));
    for (DatasetQueryPerformance trace : buffer.traces()) {
      if (filterDatasets && !datasetIds.contains(trace.datasetId())) {
        continue;
      }
      if (filterQueries && !queryIds.contains(trace.queryId())) {
        continue;
      }
      result.add(trace);
      if (result.size() >= limit) {
        break;
      }
    }
    return List.copyOf(result);
  }
}
