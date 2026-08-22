package io.yak.ops.business.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.analysis.repository.AnalysisRepository;
import io.yak.ops.business.analysis.service.event.AnalysisLineageRefreshRequested;
import io.yak.ops.business.analysis.service.support.AnalysisDefinitionNormalizer;
import io.yak.ops.business.dataset.DatasetService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AnalysisServiceTest {

  @Test
  void createValidatesDatasetFieldsAndPersistsReusableSpec() {
    AnalysisRepository repository = mock(AnalysisRepository.class);
    DatasetService datasetService = mock(DatasetService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AnalysisDefinitionNormalizer normalizer = new AnalysisDefinitionNormalizer(datasetService);
    AnalysisService service = new AnalysisService(repository, normalizer, events);

    AnalysisAsset stored = asset(11L, 9L);
    when(repository.insert(argThat(draft ->
        "区域销售".equals(draft.name())
            && draft.datasetId() == 9L
            && draft.querySpec().limit() == 500)))
        .thenReturn(11L);
    when(repository.findById(11L)).thenReturn(Optional.of(stored));

    AnalysisAsset created = service.create(new AnalysisService.SaveCommand(
        "区域销售",
        null,
        9L,
        AnalysisChartType.BAR,
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            500,
            30),
        new AnalysisVisualConfig(false, false, false, true)));

    assertEquals(11L, created.id());
    assertEquals(9L, created.datasetId());
    verify(datasetService).validateAnalysisBinding(eq(9L), anySet());
    verify(events).publishEvent(AnalysisLineageRefreshRequested.refresh(11L));
  }

  @Test
  void metricCardRejectsDimensions() {
    AnalysisRepository repository = mock(AnalysisRepository.class);
    DatasetService datasetService = mock(DatasetService.class);
    AnalysisService service = new AnalysisService(
        repository,
        new AnalysisDefinitionNormalizer(datasetService),
        mock(ApplicationEventPublisher.class));

    assertThrows(IllegalArgumentException.class, () -> service.create(new AnalysisService.SaveCommand(
        "错误指标卡",
        null,
        9L,
        AnalysisChartType.METRIC,
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            200,
            30),
        null)));
  }

  private static AnalysisAsset asset(long analysisId, long datasetId) {
    return new AnalysisAsset(
        analysisId,
        "区域销售",
        null,
        datasetId,
        AnalysisChartType.BAR,
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(), List.of(), 500, 30),
        new AnalysisVisualConfig(false, false, false, true),
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
