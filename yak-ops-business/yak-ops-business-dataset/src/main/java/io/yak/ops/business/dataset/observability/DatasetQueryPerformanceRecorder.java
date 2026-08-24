package io.yak.ops.business.dataset.observability;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import org.springframework.stereotype.Component;

/** Records query-performance evidence without affecting the query result. */
@Component
public class DatasetQueryPerformanceRecorder {

  private final DatasetQueryPerformanceBuffer buffer;

  public DatasetQueryPerformanceRecorder(DatasetQueryPerformanceBuffer buffer) {
    this.buffer = buffer;
  }

  public void record(DatasetQueryPerformance trace) {
    if (trace != null) {
      buffer.add(trace);
    }
  }
}
