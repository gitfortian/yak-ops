package io.yak.ops.business.dashboard;

import java.util.List;
import java.util.Optional;

interface DashboardRepository {
  long insertDashboard(String name, String description);
  long insertVersion(long dashboardId, int versionNo, String name, String description, Long activeDatasetId);
  void insertWidgets(long versionId, List<DashboardService.WidgetSpec> widgets, List<String> inlineJson);
  void updateCurrentVersion(long dashboardId, long versionId, int versionNo, String name, String description);
  Optional<DashboardAsset> findDashboard(long dashboardId);
  List<DashboardAsset> listDashboards();
  Optional<DashboardVersion> findVersion(long versionId);
  Optional<DashboardVersion> findVersionByNo(long dashboardId, int versionNo);
  List<DashboardVersion> listVersions(long dashboardId);
  List<DashboardWidgetSnapshot> listWidgets(long versionId);
  int nextVersionNo(long dashboardId);
  void deleteDashboard(long dashboardId);
}
