package io.yak.ops.boot.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.DashboardService;
import io.yak.ops.business.dashboard.domain.DashboardOverview;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.development.node.DevelopmentNodeService;
import io.yak.ops.business.digitalscreen.application.DigitalScreenApplicationService;
import io.yak.ops.business.quality.workspace.QualityExecutionOverviewReader;
import io.yak.ops.business.quality.workspace.QualityOverviewReader;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionOverviewReader;
import io.yak.ops.business.workflow.execution.WorkflowExecutionOverviewReader;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HomeCockpitServiceTest {

  @Test
  void shouldBuildLifecycleAndAttentionFromDomainReadSides() {
    DataSourceReader dataSourceReader = mock(DataSourceReader.class);
    when(dataSourceReader.summary()).thenReturn(new DataSourceSummary(12, 10, 1, 1, 3));

    OfflineExecutionOverviewReader offline = mock(OfflineExecutionOverviewReader.class);
    when(offline.metrics(any(), any()))
        .thenReturn(new OfflineExecutionOverviewReader.Metrics(18, 2, 3, 8, 1000, 100, 2));

    WorkflowExecutionOverviewReader workflow = mock(WorkflowExecutionOverviewReader.class);
    when(workflow.metrics(any(), any()))
        .thenReturn(new WorkflowExecutionOverviewReader.Metrics(9, 1, 2, 0, 0, 0, 0));

    QualityExecutionOverviewReader qualityExecution = mock(QualityExecutionOverviewReader.class);
    when(qualityExecution.metrics(any(), any()))
        .thenReturn(new QualityExecutionOverviewReader.Metrics(4, 1, 1, 0, 0, 0, 0));

    DevelopmentNodeService development = mock(DevelopmentNodeService.class);
    when(development.count()).thenReturn(1L);

    QualityOverviewReader quality = mock(QualityOverviewReader.class);
    LocalDate today = LocalDate.now();
    when(quality.overview()).thenReturn(new QualityOverviewReader.Overview(
        today.minusDays(6),
        today,
        98.0,
        7,
        20,
        5,
        1,
        4,
        List.of(),
        List.of()));

    HomeAssetOverviewService assets = mock(HomeAssetOverviewService.class);
    when(assets.overview()).thenReturn(new HomeAssetOverviewService.OverviewResponse(
        new HomeAssetOverviewService.DatasetOverview(6L, 10L, 20L, 1L, List.of(), List.of()),
        new HomeAssetOverviewService.LineageOverview(
            30L, 8L, 2L, 6L, List.of(), List.of(), List.of())));

    DataServiceReader dataServices = mock(DataServiceReader.class);
    when(dataServices.count()).thenReturn(5L);
    DashboardService dashboards = mock(DashboardService.class);
    when(dashboards.overview(1)).thenReturn(new DashboardOverview(3, 2, List.of()));
    DigitalScreenApplicationService screens = mock(DigitalScreenApplicationService.class);
    when(screens.count()).thenReturn(2L);

    HomeCockpitService service = new HomeCockpitService(
        provider(dataSourceReader),
        provider(offline),
        provider(development),
        provider(workflow),
        provider(quality),
        provider(qualityExecution),
        assets,
        provider(dataServices),
        provider(dashboards),
        provider(screens));

    HomeCockpitService.CockpitResponse response = service.cockpit();

    assertThat(response.header().dataSourceCount()).isEqualTo(12);
    assertThat(response.header().runningCount()).isEqualTo(4);
    assertThat(response.header().attentionCount()).isEqualTo(12);
    assertThat(response.lifecycle()).hasSize(8);
    assertThat(response.lifecycle())
        .filteredOn(item -> item.key().equals("data-source"))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.status()).isEqualTo("ATTENTION");
          assertThat(item.value()).isEqualTo(12L);
          assertThat(item.issueCount()).isEqualTo(2L);
        });
    assertThat(response.lifecycle())
        .filteredOn(item -> item.key().equals("quality"))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.status()).isEqualTo("ATTENTION");
          assertThat(item.issueCount()).isEqualTo(5L);
        });
    assertThat(response.lifecycle())
        .filteredOn(item -> item.key().equals("asset"))
        .singleElement()
        .extracting(HomeCockpitService.LifecycleStage::value)
        .isEqualTo(6L);
    assertThat(response.lifecycle())
        .filteredOn(item -> item.key().equals("service"))
        .singleElement()
        .extracting(HomeCockpitService.LifecycleStage::value)
        .isEqualTo(5L);
    assertThat(response.lifecycle())
        .filteredOn(item -> item.key().equals("consumption"))
        .singleElement()
        .satisfies(item -> {
          assertThat(item.status()).isEqualTo("READY");
          assertThat(item.value()).isEqualTo(5L);
        });
    assertThat(response.attention().items())
        .extracting(HomeCockpitService.AttentionItem::key)
        .containsExactly(
            "offline-failures",
            "workflow-failures",
            "quality-execution-failures",
            "data-source-connection",
            "quality-issues");
  }

  @Test
  void shouldKeepCockpitAvailableWhenOptionalDomainsAreUnavailable() {
    HomeAssetOverviewService assets = mock(HomeAssetOverviewService.class);
    when(assets.overview()).thenThrow(new IllegalStateException("asset unavailable"));

    HomeCockpitService service = new HomeCockpitService(
        provider(null),
        provider(null),
        provider(null),
        provider(null),
        provider(null),
        provider(null),
        assets,
        provider(null),
        provider(null),
        provider(null));

    HomeCockpitService.CockpitResponse response = service.cockpit();

    assertThat(response.lifecycle()).hasSize(8);
    assertThat(response.lifecycle())
        .filteredOn(item -> item.key().equals("data-source"))
        .singleElement()
        .extracting(HomeCockpitService.LifecycleStage::status)
        .isEqualTo("UNAVAILABLE");
    assertThat(response.lifecycle())
        .filteredOn(item -> item.key().equals("consumption"))
        .singleElement()
        .extracting(HomeCockpitService.LifecycleStage::status)
        .isEqualTo("UNAVAILABLE");
    assertThat(response.attention().items()).isEmpty();
    assertThat(response.header().attentionCount()).isZero();
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }
}
