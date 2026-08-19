package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatasetQueryPerformanceServiceTest {

  @Test
  void filtersRecentTracesByDatasetAndKeepsNewestFirst() {
    DatasetQueryPerformanceService service = new DatasetQueryPerformanceService();
    service.record(trace("q1", 1L, 10L));
    service.record(trace("q2", 2L, 20L));
    service.record(trace("q3", 1L, 30L));

    var traces = service.recent(Set.of(1L), 10);

    assertEquals(2, traces.size());
    assertEquals("q3", traces.get(0).queryId());
    assertEquals("q1", traces.get(1).queryId());
  }

  @Test
  void capsTheDiagnosticWindow() {
    DatasetQueryPerformanceService service = new DatasetQueryPerformanceService();
    for (int index = 0; index < DatasetQueryPerformanceService.MAX_TRACES + 5; index++) {
      service.record(trace("q" + index, 1L, index));
    }

    var traces = service.recent(Set.of(), DatasetQueryPerformanceService.MAX_QUERY_LIMIT);

    assertEquals(DatasetQueryPerformanceService.MAX_QUERY_LIMIT, traces.size());
    assertEquals("q504", traces.get(0).queryId());
  }

  private static DatasetQueryPerformance trace(String queryId, long datasetId, long totalMillis) {
    return new DatasetQueryPerformance(
        queryId,
        datasetId,
        "dataset-" + datasetId,
        datasetId * 10,
        1,
        "SQL_QUERY",
        "ds-1",
        "select 1",
        1,
        2,
        3,
        4,
        totalMillis,
        1,
        false,
        Instant.parse("2026-08-18T10:00:00Z"));
  }
}
