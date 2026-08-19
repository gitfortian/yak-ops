package io.yak.ops.business.dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

/**
 * Keeps a small in-memory diagnostic window so performance tracing does not add database I/O
 * to the Dataset query that is being measured.
 */
@Service
public class DatasetQueryPerformanceService {

  static final int MAX_TRACES = 500;
  static final int MAX_QUERY_LIMIT = 200;

  private final ConcurrentLinkedDeque<DatasetQueryPerformance> traces = new ConcurrentLinkedDeque<>();

  void record(DatasetQueryPerformance trace) {
    if (trace == null) return;
    traces.addFirst(trace);
    while (traces.size() > MAX_TRACES) {
      traces.pollLast();
    }
  }

  public List<DatasetQueryPerformance> recent(Set<Long> datasetIds, int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_LIMIT));
    boolean filterDatasets = datasetIds != null && !datasetIds.isEmpty();
    List<DatasetQueryPerformance> result = new ArrayList<>(Math.min(limit, traces.size()));
    for (DatasetQueryPerformance trace : traces) {
      if (filterDatasets && !datasetIds.contains(trace.datasetId())) continue;
      result.add(trace);
      if (result.size() >= limit) break;
    }
    return List.copyOf(result);
  }
}
