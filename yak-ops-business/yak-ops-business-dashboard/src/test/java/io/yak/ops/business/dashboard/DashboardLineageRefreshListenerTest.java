package io.yak.ops.business.dashboard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionDetail;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import io.yak.ops.business.dashboard.service.DashboardService;
import io.yak.ops.business.dashboard.service.event.DashboardLineageRefreshRequested;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardLineageRefreshListenerTest {

  @Test
  void publishedDashboardUsesPublishedSnapshotInsteadOfNewerDraft() {
    DashboardService dashboardService = mock(DashboardService.class);
    DashboardLineageService lineageService = mock(DashboardLineageService.class);
    DashboardLineageRefreshListener listener =
        new DashboardLineageRefreshListener(dashboardService, lineageService);

    DashboardAsset dashboard = new DashboardAsset(
        7L, "Draft V2", null, 102L, 2, 101L, 1, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
    DashboardVersion draft = new DashboardVersion(
        102L, 7L, 2, "Draft V2", null, null, Instant.EPOCH);
    DashboardVersion published = new DashboardVersion(
        101L, 7L, 1, "Published V1", null, null, Instant.EPOCH);
    DashboardWidgetSnapshot draftWidget = widget(2L, 102L, "draft", 22L);
    DashboardWidgetSnapshot publishedWidget = widget(1L, 101L, "live", 11L);

    when(dashboardService.get(7L)).thenReturn(new DashboardDetail(
        dashboard, draft, List.of(draft, published), List.of(draftWidget), List.of(), List.of()));
    when(dashboardService.published(7L)).thenReturn(new DashboardVersionDetail(
        dashboard, published, List.of(publishedWidget), List.of(), List.of()));

    listener.refresh(DashboardLineageRefreshRequested.refresh(7L));

    verify(lineageService).syncVersion(
        dashboard, published, List.of(publishedWidget), true);
  }

  @Test
  void unpublishedDashboardUsesCurrentDraft() {
    DashboardService dashboardService = mock(DashboardService.class);
    DashboardLineageService lineageService = mock(DashboardLineageService.class);
    DashboardLineageRefreshListener listener =
        new DashboardLineageRefreshListener(dashboardService, lineageService);

    DashboardAsset dashboard = new DashboardAsset(
        7L, "Draft", null, 102L, 2, null, 0, null, Instant.EPOCH, Instant.EPOCH);
    DashboardVersion draft = new DashboardVersion(
        102L, 7L, 2, "Draft", null, null, Instant.EPOCH);
    DashboardWidgetSnapshot widget = widget(2L, 102L, "draft", 22L);
    when(dashboardService.get(7L)).thenReturn(new DashboardDetail(
        dashboard, draft, List.of(draft), List.of(widget), List.of(), List.of()));

    listener.refresh(DashboardLineageRefreshRequested.refresh(7L));

    verify(lineageService).syncVersion(dashboard, draft, List.of(widget), false);
  }

  @Test
  void lineageFailureNeverEscapesCommittedDashboardMutation() {
    DashboardService dashboardService = mock(DashboardService.class);
    DashboardLineageService lineageService = mock(DashboardLineageService.class);
    DashboardLineageRefreshListener listener =
        new DashboardLineageRefreshListener(dashboardService, lineageService);
    when(dashboardService.get(7L)).thenThrow(new IllegalStateException("lineage unavailable"));

    assertDoesNotThrow(() -> listener.refresh(DashboardLineageRefreshRequested.refresh(7L)));
  }

  @Test
  void deleteClearsDashboardScopedEvidence() {
    DashboardService dashboardService = mock(DashboardService.class);
    DashboardLineageService lineageService = mock(DashboardLineageService.class);
    DashboardLineageRefreshListener listener =
        new DashboardLineageRefreshListener(dashboardService, lineageService);

    listener.refresh(DashboardLineageRefreshRequested.deleted(7L));

    verify(lineageService).clear(7L);
  }

  private static DashboardWidgetSnapshot widget(
      long id,
      long versionId,
      String key,
      long analysisId) {
    return new DashboardWidgetSnapshot(
        id, versionId, key, analysisId, key, null,
        0, 0, 8, 6, null, null, 1);
  }
}
