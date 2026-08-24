package io.yak.ops.business.dashboard.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.composition.DashboardCompositionNormalizer;
import io.yak.ops.business.dashboard.definition.DashboardChangedEvent;
import io.yak.ops.business.dashboard.definition.DashboardReader;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DashboardVersionManagerTest {

  @Test
  void saveCreatesANewImmutableVersionAndMovesCurrentPointerThroughAppender() {
    DashboardReader dashboards = mock(DashboardReader.class);
    DashboardVersionReader versionReader = mock(DashboardVersionReader.class);
    DashboardCompositionNormalizer composition = mock(DashboardCompositionNormalizer.class);
    DashboardVersionAppender appender = mock(DashboardVersionAppender.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    DashboardVersionManager manager = new DashboardVersionManager(
        dashboards, versionReader, composition, appender, events);
    DashboardDraft draft = new DashboardDraft("V", null, null, null, List.of(), List.of(), List.of());
    DashboardDetail detail = new DashboardDetail(
        mock(DashboardAsset.class), null, null, List.of(), List.of(), List.of(), List.of());

    when(dashboards.require(3L)).thenReturn(mock(DashboardAsset.class));
    when(composition.normalize(draft)).thenReturn(draft);
    when(dashboards.get(3L)).thenReturn(detail);

    DashboardDetail saved = manager.saveVersion(3L, draft);

    assertThat(saved).isSameAs(detail);
    verify(appender).appendNext(3L, draft);
    verify(events).publishEvent(DashboardChangedEvent.refreshed(3L));
  }
}
