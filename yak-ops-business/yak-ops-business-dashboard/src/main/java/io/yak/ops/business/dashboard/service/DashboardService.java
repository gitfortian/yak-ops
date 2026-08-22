package io.yak.ops.business.dashboard.service;

import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardInteractionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionDetail;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import io.yak.ops.business.dashboard.domain.FilterBindingSpec;
import io.yak.ops.business.dashboard.domain.GlobalFilterSpec;
import io.yak.ops.business.dashboard.domain.InteractionSpec;
import io.yak.ops.business.dashboard.domain.WidgetSpec;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.service.event.DashboardLineageRefreshRequested;
import io.yak.ops.business.dashboard.service.support.DashboardDraftValidator;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard 业务用例编排。 */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final DashboardDraftValidator draftValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(
            transactionManager = "yakBusinessTransactionManager",
            readOnly = true)
    public List<DashboardAsset> list() {
        return dashboardRepository.listDashboards();
    }

    @Transactional(
            transactionManager = "yakBusinessTransactionManager",
            readOnly = true)
    public DashboardDetail get(long dashboardId) {
        DashboardAsset dashboard = requireDashboard(dashboardId);
        DashboardVersion currentVersion = null;
        Object theme = null;
        List<DashboardWidgetSnapshot> widgets = List.of();
        List<DashboardGlobalFilterSnapshot> filters = List.of();
        List<DashboardInteractionSnapshot> interactions = List.of();

        if (dashboard.currentVersionId() != null) {
            DashboardVersionSnapshot snapshot = dashboardRepository
                    .findVersionSnapshot(dashboard.currentVersionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Dashboard 当前草稿版本不存在：" + dashboard.currentVersionId()));
            currentVersion = snapshot.version();
            theme = snapshot.theme();
            widgets = snapshot.widgets();
            filters = snapshot.globalFilters();
            interactions = snapshot.interactions();
        }

        return new DashboardDetail(
                dashboard,
                currentVersion,
                theme,
                dashboardRepository.listVersions(dashboardId),
                widgets,
                filters,
                interactions);
    }

    @Transactional(
            transactionManager = "yakBusinessTransactionManager",
            readOnly = true)
    public List<DashboardVersion> versions(long dashboardId) {
        requireDashboard(dashboardId);
        return dashboardRepository.listVersions(dashboardId);
    }

    @Transactional(
            transactionManager = "yakBusinessTransactionManager",
            readOnly = true)
    public DashboardVersionDetail version(long dashboardId, int versionNo) {
        DashboardAsset dashboard = requireDashboard(dashboardId);
        if (versionNo <= 0) {
            throw new IllegalArgumentException("versionNo 必须大于 0");
        }

        DashboardVersionSnapshot snapshot = dashboardRepository
                .findVersionSnapshotByNo(dashboardId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "DashboardVersion 不存在：V" + versionNo));
        return versionDetail(dashboard, snapshot);
    }

    @Transactional(
            transactionManager = "yakBusinessTransactionManager",
            readOnly = true)
    public DashboardVersionDetail published(long dashboardId) {
        DashboardAsset dashboard = requireDashboard(dashboardId);
        if (dashboard.publishedVersionId() == null) {
            throw new IllegalStateException("Dashboard 尚未发布：" + dashboardId);
        }

        DashboardVersionSnapshot snapshot = dashboardRepository
                .findVersionSnapshot(dashboard.publishedVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Dashboard 已发布版本不存在：" + dashboard.publishedVersionId()));
        return versionDetail(dashboard, snapshot);
    }

    @Transactional("yakBusinessTransactionManager")
    public DashboardDetail create(DashboardDraft draft) {
        DashboardDraft normalized = draftValidator.normalize(draft);
        long dashboardId = dashboardRepository.insertDashboard(
                normalized.name(),
                normalized.description());
        appendVersion(dashboardId, 1, normalized);
        publishRefresh(dashboardId);
        return get(dashboardId);
    }

    @Transactional("yakBusinessTransactionManager")
    public DashboardDetail saveVersion(long dashboardId, DashboardDraft draft) {
        requireDashboard(dashboardId);
        DashboardDraft normalized = draftValidator.normalize(draft);
        int versionNo = dashboardRepository.nextVersionNo(dashboardId);
        appendVersion(dashboardId, versionNo, normalized);
        publishRefresh(dashboardId);
        return get(dashboardId);
    }

    @Transactional("yakBusinessTransactionManager")
    public DashboardDetail publish(long dashboardId) {
        DashboardAsset dashboard = requireDashboard(dashboardId);
        if (dashboard.currentVersionId() == null || dashboard.currentVersionNo() <= 0) {
            throw new IllegalStateException("Dashboard 没有可发布的草稿：" + dashboardId);
        }
        if (Objects.equals(dashboard.currentVersionId(), dashboard.publishedVersionId())) {
            return get(dashboardId);
        }

        DashboardVersionSnapshot draft = dashboardRepository
                .findVersionSnapshot(dashboard.currentVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Dashboard 当前草稿版本不存在：" + dashboard.currentVersionId()));
        dashboardRepository.updatePublishedVersion(
                dashboardId,
                draft.version().id(),
                draft.version().versionNo());
        publishRefresh(dashboardId);
        return get(dashboardId);
    }

    @Transactional("yakBusinessTransactionManager")
    public DashboardDetail restoreVersion(long dashboardId, int versionNo) {
        DashboardVersionDetail source = version(dashboardId, versionNo);
        DashboardDraft normalized = draftValidator.normalize(draftFromVersion(source));
        appendVersion(
                dashboardId,
                dashboardRepository.nextVersionNo(dashboardId),
                normalized);
        publishRefresh(dashboardId);
        return get(dashboardId);
    }

    /**
     * @deprecated 历史激活语义已调整为恢复快照并创建新的草稿版本。
     */
    @Deprecated
    @Transactional("yakBusinessTransactionManager")
    public DashboardDetail activateVersion(long dashboardId, int versionNo) {
        return restoreVersion(dashboardId, versionNo);
    }

    @Transactional("yakBusinessTransactionManager")
    public void delete(long dashboardId) {
        requireDashboard(dashboardId);
        dashboardRepository.deleteDashboard(dashboardId);
        eventPublisher.publishEvent(DashboardLineageRefreshRequested.deleted(dashboardId));
    }

    private DashboardAsset requireDashboard(long dashboardId) {
        if (dashboardId <= 0L) {
            throw new IllegalArgumentException("dashboardId 必须大于 0");
        }
        return dashboardRepository.findDashboard(dashboardId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dashboard 不存在：" + dashboardId));
    }

    private DashboardVersionDetail versionDetail(
            DashboardAsset dashboard,
            DashboardVersionSnapshot snapshot) {
        return new DashboardVersionDetail(
                dashboard,
                snapshot.version(),
                snapshot.theme(),
                snapshot.widgets(),
                snapshot.globalFilters(),
                snapshot.interactions());
    }

    private void appendVersion(
            long dashboardId,
            int versionNo,
            DashboardDraft draft) {
        long versionId = dashboardRepository.appendVersion(
                dashboardId,
                versionNo,
                draft);
        dashboardRepository.updateCurrentVersion(
                dashboardId,
                versionId,
                versionNo,
                draft.name(),
                draft.description());
    }

    private void publishRefresh(long dashboardId) {
        eventPublisher.publishEvent(DashboardLineageRefreshRequested.refresh(dashboardId));
    }

    private DashboardDraft draftFromVersion(DashboardVersionDetail detail) {
        List<WidgetSpec> widgets = detail.widgets().stream()
                .map(widget -> new WidgetSpec(
                        widget.widgetKey(),
                        widget.analysisId(),
                        widget.title(),
                        widget.inlineAnalysis(),
                        widget.x(),
                        widget.y(),
                        widget.w(),
                        widget.h(),
                        widget.minW(),
                        widget.minH()))
                .toList();

        List<GlobalFilterSpec> filters = detail.globalFilters().stream()
                .map(filter -> new GlobalFilterSpec(
                        filter.filterKey(),
                        filter.name(),
                        filter.operator(),
                        filter.defaultValue(),
                        filter.bindings().stream()
                                .map(binding -> new FilterBindingSpec(
                                        binding.widgetKey(),
                                        binding.fieldId()))
                                .toList()))
                .toList();

        List<InteractionSpec> interactions = detail.interactions().stream()
                .map(interaction -> new InteractionSpec(
                        interaction.interactionKey(),
                        interaction.event(),
                        interaction.sourceWidgetKey(),
                        interaction.sourceFieldId(),
                        interaction.targetFilterKey()))
                .toList();

        return new DashboardDraft(
                detail.version().name(),
                detail.version().description(),
                detail.version().activeDatasetId(),
                detail.theme(),
                widgets,
                filters,
                interactions);
    }
}
