package io.yak.ops.business.dashboard;

interface DashboardThemeRepository {
  void save(long versionId, String themeJson);
  Object find(long versionId);
}
