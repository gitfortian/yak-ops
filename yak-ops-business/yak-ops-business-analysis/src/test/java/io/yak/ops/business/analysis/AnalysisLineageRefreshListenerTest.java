package io.yak.ops.business.analysis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisLineageRefreshListenerTest {

  @Test
  void refreshReadsCommittedAnalysisSnapshot() {
    AnalysisService analysisService = mock(AnalysisService.class);
    AnalysisLineageService lineageService = mock(AnalysisLineageService.class);
    AnalysisLineageRefreshListener listener =
        new AnalysisLineageRefreshListener(analysisService, lineageService);
    AnalysisAsset asset = new AnalysisAsset(
        5L,
        "A",
        null,
        9L,
        AnalysisChartType.METRIC,
        new AnalysisQuerySpec(
            List.of(),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            100,
            30),
        new AnalysisVisualConfig(false, false, false, false),
        Instant.EPOCH,
        Instant.EPOCH);
    when(analysisService.get(5L)).thenReturn(asset);

    listener.refresh(AnalysisLineageRefreshRequested.refresh(5L));

    verify(lineageService).syncCurrent(asset);
  }

  @Test
  void refreshFailureDoesNotEscapeCommittedBusinessCall() {
    AnalysisService analysisService = mock(AnalysisService.class);
    AnalysisLineageService lineageService = mock(AnalysisLineageService.class);
    AnalysisLineageRefreshListener listener =
        new AnalysisLineageRefreshListener(analysisService, lineageService);
    when(analysisService.get(5L)).thenThrow(new IllegalStateException("lineage unavailable"));

    assertDoesNotThrow(() -> listener.refresh(AnalysisLineageRefreshRequested.refresh(5L)));
  }

  @Test
  void deleteClearsEvidenceWithoutReadingDeletedAnalysis() {
    AnalysisService analysisService = mock(AnalysisService.class);
    AnalysisLineageService lineageService = mock(AnalysisLineageService.class);
    AnalysisLineageRefreshListener listener =
        new AnalysisLineageRefreshListener(analysisService, lineageService);

    listener.refresh(AnalysisLineageRefreshRequested.deleted(5L));

    verify(lineageService).clear(5L);
  }
}
