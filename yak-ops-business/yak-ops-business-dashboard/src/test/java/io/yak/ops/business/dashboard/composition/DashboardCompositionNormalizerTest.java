package io.yak.ops.business.dashboard.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.WidgetSpec;
import io.yak.ops.business.dashboard.gateway.analysis.DashboardAnalysisGateway;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardCompositionNormalizerTest {

  @Test
  void linkedWidgetUsesAnalysisGatewayAndPreservesLayout() {
    DashboardAnalysisGateway analyses = mock(DashboardAnalysisGateway.class);
    DashboardCompositionNormalizer normalizer = normalizer(analyses);
    DashboardDraft draft = new DashboardDraft(
        "  销售驾驶舱  ",
        null,
        12L,
        Map.of("mode", "dark"),
        List.of(new WidgetSpec("w1", 99L, "区域销售", null, 0, 0, 10, 7, 6, 5)),
        List.of(),
        List.of());

    DashboardDraft normalized = normalizer.normalize(draft);

    assertThat(normalized.name()).isEqualTo("销售驾驶舱");
    assertThat(normalized.widgets()).hasSize(1);
    assertThat(normalized.widgets().get(0).w()).isEqualTo(10);
    verify(analyses).requireExists(99L);
  }

  @Test
  void widgetCannotLinkReusableAndInlineAnalysisAtTheSameTime() {
    DashboardCompositionNormalizer normalizer = normalizer(mock(DashboardAnalysisGateway.class));
    DashboardDraft draft = new DashboardDraft(
        "D",
        null,
        null,
        null,
        List.of(new WidgetSpec(
            "w1", 99L, null, Map.of("datasetId", 1L), 0, 0, 6, 4, null, null)),
        List.of(),
        List.of());

    assertThatThrownBy(() -> normalizer.normalize(draft))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须且只能选择");
  }

  private DashboardCompositionNormalizer normalizer(DashboardAnalysisGateway analyses) {
    DashboardJsonPolicy json = new DashboardJsonPolicy(new ObjectMapper());
    DashboardWidgetPolicy widgets =
        new DashboardWidgetPolicy(analyses, new DashboardLayoutPolicy(), json);
    return new DashboardCompositionNormalizer(
        json,
        widgets,
        new DashboardFilterPolicy(json),
        new DashboardInteractionPolicy());
  }
}
