package io.yak.ops.business.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.controller.v1.mapper.AnalysisViewMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisViewMapperTest {

  @Test
  void httpIdsRemainStrings() throws Exception {
    AnalysisAsset asset = new AnalysisAsset(
        9007199254740993L,
        "A",
        null,
        9007199254740995L,
        AnalysisChartType.METRIC,
        new AnalysisQuerySpec(
            List.of(),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(), List.of(), 100, 30),
        new AnalysisVisualConfig(false, false, false, false),
        Instant.EPOCH,
        Instant.EPOCH);

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    JsonNode json = mapper.readTree(mapper.writeValueAsString(new AnalysisViewMapper().toView(asset)));

    assertEquals("9007199254740993", json.path("id").asText());
    assertEquals("9007199254740995", json.path("datasetId").asText());
  }
}
