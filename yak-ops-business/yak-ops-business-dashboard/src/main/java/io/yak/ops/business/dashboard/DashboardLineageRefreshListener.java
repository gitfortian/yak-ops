package io.yak.ops.business.dashboard;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Refreshes effective Dashboard lineage only after the dashboard transaction has committed. */
@Component
class DashboardLineageRefreshListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DashboardLineageRefreshListener.class);

  private final DashboardService dashboardService;
  private final LineageOperations lineageOperations;

  @Autowired
  DashboardLineageRefreshListener(
      DashboardService dashboardService,
      DashboardLineageTransactionRunner transactionRunner) {
    this(dashboardService, new LineageOperations() {
      @Override
      public void syncVersion(
          DashboardAsset dashboard,
          DashboardVersion version,
          List<DashboardWidgetSnapshot> widgets,
          boolean published) {
        transactionRunner.syncVersion(dashboard, version, widgets, published);
      }

      @Override
      public void clear(long dashboardId) {
        transactionRunner.clear(dashboardId);
      }
    });
  }

  /** Keeps focused tests source-compatible while production uses the REQUIRES_NEW runner. */
  DashboardLineageRefreshListener(
      DashboardService dashboardService,
      DashboardLineageService lineageService) {
    this(dashboardService, new LineageOperations() {
      @Override
      public void syncVersion(
          DashboardAsset dashboard,
          DashboardVersion version,
          List<DashboardWidgetSnapshot> widgets,
          boolean published) {
        lineageService.syncVersion(dashboard, version, widgets, published);
      }

      @Override
      public void clear(long dashboardId) {
        lineageService.clear(dashboardId);
      }
    });
  }

  private DashboardLineageRefreshListener(
      DashboardService dashboardService,
      LineageOperations lineageOperations) {
    this.dashboardService = dashboardService;
    this.lineageOperations = lineageOperations;
  }

  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  public void refresh(DashboardLineageRefreshRequested event) {
    if (event == null || event.dashboardId() <= 0L) return;
    try {
      if (event.deleted()) {
        lineageOperations.clear(event.dashboardId());
        return;
      }

      DashboardDetail current = dashboardService.get(event.dashboardId());
      DashboardAsset dashboard = current.dashboard();
      if (dashboard.publishedVersionId() != null) {
        DashboardVersionDetail published = dashboardService.published(event.dashboardId());
        lineageOperations.syncVersion(
            dashboard,
            published.version(),
            published.widgets(),
            true);
      } else {
        lineageOperations.syncVersion(
            dashboard,
            current.currentVersion(),
            current.widgets(),
            false);
      }
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Dashboard lineage refresh failed after commit for dashboard {}: {}",
          event.dashboardId(),
          exception.getMessage(),
          exception);
    }
  }

  private interface LineageOperations {
    void syncVersion(
        DashboardAsset dashboard,
        DashboardVersion version,
        List<DashboardWidgetSnapshot> widgets,
        boolean published);

    void clear(long dashboardId);
  }
}

record DashboardLineageRefreshRequested(long dashboardId, boolean deleted) {
  DashboardLineageRefreshRequested {
    if (dashboardId <= 0L) throw new IllegalArgumentException("dashboardId 必须大于 0");
  }

  static DashboardLineageRefreshRequested refresh(long dashboardId) {
    return new DashboardLineageRefreshRequested(dashboardId, false);
  }

  static DashboardLineageRefreshRequested deleted(long dashboardId) {
    return new DashboardLineageRefreshRequested(dashboardId, true);
  }
}
