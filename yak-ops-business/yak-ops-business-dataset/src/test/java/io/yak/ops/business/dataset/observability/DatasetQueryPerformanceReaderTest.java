package io.yak.ops.business.dataset.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatasetQueryPerformanceReaderTest {

  @Test
  void filtersRecentTracesByDatasetAndKeepsNewestFirst() {
    Fixture fixture = fixture();
    fixture.recorder.record(trace("q1", 1L, 10L));
    fixture.recorder.record(trace("q2", 2L, 20L));
    fixture.recorder.record(trace("q3", 1L, 30L));

    var traces = fixture.reader.recent(Set.of(1L), 10);

    assertEquals(2, traces.size());
    assertEquals("q3", traces.get(0).queryId());
    assertEquals("q1", traces.get(1).queryId());
  }

  @Test
  void filtersRecentTracesByExactQueryIds() {
    Fixture fixture = fixture();
    fixture.recorder.record(trace("q1", 1L, 10L));
    fixture.recorder.record(trace("q2", 1L, 20L));
    fixture.recorder.record(trace("q3", 2L, 30L));

    var traces = fixture.reader.recent(Set.of(), Set.of("q1", "q3"), 10);

    assertEquals(2, traces.size());
    assertEquals("q3", traces.get(0).queryId());
    assertEquals("q1", traces.get(1).queryId());
  }

  @Test
  void capsTheDiagnosticWindowAndQueryLimit() {
    Fixture fixture = fixture();
    for (int index = 0; index < DatasetQueryPerformanceBuffer.MAX_TRACES + 5; index++) {
      fixture.recorder.record(trace("q" + index, 1L, index));
    }

    var traces = fixture.reader.recent(Set.of(), DatasetQueryPerformanceReader.MAX_QUERY_LIMIT);

    assertEquals(DatasetQueryPerformanceReader.MAX_QUERY_LIMIT, traces.size());
    assertEquals("q504", traces.get(0).queryId());
  }

  private Fixture fixture() {
    DatasetQueryPerformanceBuffer buffer = new DatasetQueryPerformanceBuffer();
    return new Fixture(
        new DatasetQueryPerformanceRecorder(buffer), new DatasetQueryPerformanceReader(buffer));
  }

  private static DatasetQueryPerformance trace(
      String queryId, long datasetId, long totalMillis) {
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

  private record Fixture(
      DatasetQueryPerformanceRecorder recorder, DatasetQueryPerformanceReader reader) {}
}
