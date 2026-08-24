package io.yak.ops.business.dashboard.definition;

/** Committed Dashboard mutation fact consumed by derived projections. */
public record DashboardChangedEvent(long dashboardId, boolean deleted) {

  public DashboardChangedEvent {
    if (dashboardId <= 0L) {
      throw new IllegalArgumentException("dashboardId 必须大于 0");
    }
  }

  public static DashboardChangedEvent refreshed(long dashboardId) {
    return new DashboardChangedEvent(dashboardId, false);
  }

  public static DashboardChangedEvent deleted(long dashboardId) {
    return new DashboardChangedEvent(dashboardId, true);
  }
}
