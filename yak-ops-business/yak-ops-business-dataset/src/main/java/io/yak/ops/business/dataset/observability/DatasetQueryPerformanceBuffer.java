package io.yak.ops.business.dataset.observability;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/** Process-local diagnostic evidence; it is not Dataset or query-result business truth. */
@Component
class DatasetQueryPerformanceBuffer {

  static final int MAX_TRACES = 500;

  private final ConcurrentLinkedDeque<DatasetQueryPerformance> traces =
      new ConcurrentLinkedDeque<>();

  void add(DatasetQueryPerformance trace) {
    traces.addFirst(trace);
    while (traces.size() > MAX_TRACES) {
      traces.pollLast();
    }
  }

  Iterable<DatasetQueryPerformance> traces() {
    return traces;
  }

  int size() {
    return traces.size();
  }
}
