package io.yak.ops.business.dashboard.lineage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.definition.DashboardChangedEvent;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.publication.DashboardEffectiveSnapshotReader;
import io.yak.ops.business.dashboard.publication.DashboardEffectiveSnapshotReader.EffectiveSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardLineageRefreshListenerTest {

  @Test
  void refreshUsesEffectiveSnapshotChosenByPublicationReadSide() {
    DashboardEffectiveSnapshotReader snapshots = mock(DashboardEffectiveSnapshotReader.class);
    DashboardLineageSynchronizer lineage = mock(DashboardLineageSynchronizer.class);
    DashboardLineageRefreshListener listener = new DashboardLineageRefreshListener(snapshots, lineage);
    DashboardAsset dashboard = dashboard();
    DashboardVersion version = new DashboardVersion(12L, 7L, 3, "D", null, null, Instant.EPOCH);
    DashboardVersionSnapshot snapshot =
        new DashboardVersionSnapshot(version, null, List.of(), List.of(), List.of());
    when(snapshots.read(7L)).thenReturn(new EffectiveSnapshot(dashboard, snapshot, true));

    listener.refresh(DashboardChangedEvent.refreshed(7L));

    verify(lineage).syncVersion(dashboard, version, List.of(), true);
  }

  @Test
  void projectionFailureDoesNotEscapeCommittedBusinessMutation() {
    DashboardEffectiveSnapshotReader snapshots = mock(DashboardEffectiveSnapshotReader.class);
    DashboardLineageSynchronizer lineage = mock(DashboardLineageSynchronizer.class);
    DashboardLineageRefreshListener listener = new DashboardLineageRefreshListener(snapshots, lineage);
    doThrow(new IllegalStateException("lineage unavailable")).when(snapshots).read(7L);

    assertThatCode(() -> listener.refresh(DashboardChangedEvent.refreshed(7L)))
        .doesNotThrowAnyException();
  }

  @Test
  void deleteOnlyClearsDashboardScopedEvidence() {
    DashboardEffectiveSnapshotReader snapshots = mock(DashboardEffectiveSnapshotReader.class);
    DashboardLineageSynchronizer lineage = mock(DashboardLineageSynchronizer.class);
    DashboardLineageRefreshListener listener = new DashboardLineageRefreshListener(snapshots, lineage);

    listener.refresh(DashboardChangedEvent.deleted(7L));

    verify(lineage).clear(7L);
  }

  private DashboardAsset dashboard() {
    return new DashboardAsset(
        7L, "D", null, 12L, 3, 12L, 3, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
  }
}
