package io.yak.ops.business.dashboard.service.event;

/** Dashboard 提交后派生血缘刷新事件。 */
public record DashboardLineageRefreshRequested(long dashboardId, boolean deleted) {

    public DashboardLineageRefreshRequested {
        if (dashboardId <= 0L) {
            throw new IllegalArgumentException("dashboardId 必须大于 0");
        }
    }

    public static DashboardLineageRefreshRequested refresh(long dashboardId) {
        return new DashboardLineageRefreshRequested(dashboardId, false);
    }

    public static DashboardLineageRefreshRequested deleted(long dashboardId) {
        return new DashboardLineageRefreshRequested(dashboardId, true);
    }
}
