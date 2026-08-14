package io.yak.ops.business.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.AnalysisRepository.AnalysisRow;
import io.yak.ops.business.dataset.DatasetService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

  @Test
  void createValidatesDatasetFieldsAndPersistsReusableSpec() {
    AnalysisRepository repository = mock(AnalysisRepository.class);
    DatasetService datasetService = mock(DatasetService.class);
    AnalysisService service = new AnalysisService(repository, datasetService, new ObjectMapper());

    when(repository.insert(eq("区域销售"), eq(null), eq(9L), eq(AnalysisChartType.BAR), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(11L);
    when(repository.findById(11L)).thenReturn(Optional.of(new AnalysisRow(
        11L,
        "区域销售",
        null,
        9L,
        AnalysisChartType.BAR,
        "{\"dimensions\":[\"region\"],\"metrics\":[{\"fieldId\":\"amount\",\"aggregation\":\"SUM\"}],\"filters\":[],\"sorts\":[],\"limit\":500,\"timeoutSeconds\":30}",
        "{\"showLegend\":false,\"showDataLabels\":false,\"smooth\":false,\"showGrid\":true}",
        Instant.EPOCH,
        Instant.EPOCH)));

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
  }

  @Test
  void metricCardRejectsDimensions() {
    AnalysisRepository repository = mock(AnalysisRepository.class);
    DatasetService datasetService = mock(DatasetService.class);
    AnalysisService service = new AnalysisService(repository, datasetService, new ObjectMapper());

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
}
