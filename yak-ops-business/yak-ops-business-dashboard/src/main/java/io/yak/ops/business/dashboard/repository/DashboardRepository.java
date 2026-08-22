package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import java.util.List;
import java.util.Optional;

/** Dashboard 领域仓储。 */
public interface DashboardRepository {

    long insertDashboard(String name, String description);

    long appendVersion(long dashboardId, int versionNo, DashboardDraft draft);

    void updateCurrentVersion(
            long dashboardId,
            long versionId,
            int versionNo,
            String name,
            String description);

    void updatePublishedVersion(long dashboardId, long versionId, int versionNo);

    Optional<DashboardAsset> findDashboard(long dashboardId);

    List<DashboardAsset> listDashboards();

    Optional<DashboardVersionSnapshot> findVersionSnapshot(long versionId);

    Optional<DashboardVersionSnapshot> findVersionSnapshotByNo(long dashboardId, int versionNo);

    List<DashboardVersion> listVersions(long dashboardId);

    int nextVersionNo(long dashboardId);

    void deleteDashboard(long dashboardId);
}
