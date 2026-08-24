package io.yak.ops.business.dashboard.lineage;

import io.yak.ops.business.dashboard.change.DashboardChangedEvent;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.publication.DashboardEffectiveSnapshotReader;
import io.yak.ops.business.dashboard.publication.DashboardEffectiveSnapshotReader.EffectiveSnapshot;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Refreshes derived Dashboard lineage only after the Dashboard transaction has committed. */
@Component
public class DashboardLineageRefreshListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DashboardLineageRefreshListener.class);

  private final DashboardEffectiveSnapshotReader snapshots;
  private final DashboardLineageSynchronizer lineage;

  public DashboardLineageRefreshListener(
      DashboardEffectiveSnapshotReader snapshots,
      DashboardLineageSynchronizer lineage) {
    this.snapshots = snapshots;
    this.lineage = lineage;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void refresh(DashboardChangedEvent event) {
    if (event == null || event.dashboardId() <= 0L) return;
    try {
      if (event.deleted()) {
        lineage.clear(event.dashboardId());
        return;
      }
      EffectiveSnapshot effective = snapshots.read(event.dashboardId());
      DashboardVersionSnapshot snapshot = effective.snapshot();
      lineage.syncVersion(
          effective.dashboard(),
          snapshot == null ? null : snapshot.version(),
          snapshot == null ? List.of() : snapshot.widgets(),
          effective.published());
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Dashboard lineage refresh failed after commit for dashboard {}: {}",
          event.dashboardId(),
          exception.getMessage(),
          exception);
    }
  }
}
