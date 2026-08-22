package io.yak.ops.business.dashboard;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Starts an independent transaction for derived Dashboard lineage after the business commit. */
@Service
public class DashboardLineageTransactionRunner {

  private final DashboardLineageService lineageService;

  public DashboardLineageTransactionRunner(DashboardLineageService lineageService) {
    this.lineageService = lineageService;
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void syncVersion(
      DashboardAsset dashboard,
      DashboardVersion version,
      List<DashboardWidgetSnapshot> widgets,
      boolean published) {
    lineageService.syncVersion(dashboard, version, widgets, published);
  }

  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      propagation = Propagation.REQUIRES_NEW)
  public void clear(long dashboardId) {
    lineageService.clear(dashboardId);
  }
}
