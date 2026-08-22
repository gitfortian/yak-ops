package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.dao.DashboardDao;
import io.yak.ops.business.dashboard.dao.model.DashboardFilterBindingPO;
import io.yak.ops.business.dashboard.dao.model.DashboardFilterPO;
import io.yak.ops.business.dashboard.dao.model.DashboardInteractionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import io.yak.ops.business.dashboard.dao.model.DashboardVersionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardWidgetPO;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterBindingSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterOperator;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardInteractionEvent;
import io.yak.ops.business.dashboard.domain.DashboardInteractionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import io.yak.ops.business.dashboard.repository.support.DashboardJsonCodec;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Dashboard 领域仓储的 MyBatis 持久化适配器。 */
@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DashboardRepositoryAdapter implements DashboardRepository {

    private final DashboardDao dashboardDao;
    private final DashboardJsonCodec jsonCodec;

    @Override
    public long insertDashboard(String name, String description) {
        DashboardPO dashboard = new DashboardPO();
        dashboard.setName(name);
        dashboard.setDescription(description);
        requireInserted(dashboardDao.insertDashboard(dashboard), "创建 Dashboard 失败");
        if (dashboard.getId() == null) {
            throw new IllegalStateException("创建 Dashboard 后未返回主键");
        }
        return dashboard.getId();
    }

    @Override
    public long appendVersion(long dashboardId, int versionNo, DashboardDraft draft) {
        DashboardVersionPO version = new DashboardVersionPO();
        version.setDashboardId(dashboardId);
        version.setVersionNo(versionNo);
        version.setNameSnapshot(draft.name());
        version.setDescriptionSnapshot(draft.description());
        version.setActiveDatasetId(draft.activeDatasetId());
        version.setThemeJson(jsonCodec.write(draft.theme()));
        requireInserted(dashboardDao.insertVersion(version), "创建 DashboardVersion 失败");
        if (version.getId() == null) {
            throw new IllegalStateException("创建 DashboardVersion 后未返回主键");
        }

        long versionId = version.getId();
        for (int index = 0; index < draft.widgets().size(); index++) {
            var widget = draft.widgets().get(index);
            DashboardWidgetPO row = new DashboardWidgetPO();
            row.setDashboardVersionId(versionId);
            row.setWidgetKey(widget.widgetKey());
            row.setAnalysisId(widget.analysisId());
            row.setTitle(widget.title());
            row.setInlineAnalysisJson(jsonCodec.write(widget.inlineAnalysis()));
            row.setGridX(widget.x());
            row.setGridY(widget.y());
            row.setGridW(widget.w());
            row.setGridH(widget.h());
            row.setMinW(widget.minW());
            row.setMinH(widget.minH());
            row.setSortOrder(index + 1);
            requireInserted(dashboardDao.insertWidget(row), "创建 DashboardWidget 失败");
        }

        for (int index = 0; index < draft.globalFilters().size(); index++) {
            var filter = draft.globalFilters().get(index);
            DashboardFilterPO row = new DashboardFilterPO();
            row.setDashboardVersionId(versionId);
            row.setFilterKey(filter.filterKey());
            row.setName(filter.name());
            row.setOperator(filter.operator().name());
            row.setDefaultValueJson(jsonCodec.write(filter.defaultValue()));
            row.setSortOrder(index + 1);
            requireInserted(dashboardDao.insertFilter(row), "创建 DashboardFilter 失败");

            for (int bindingIndex = 0; bindingIndex < filter.bindings().size(); bindingIndex++) {
                var binding = filter.bindings().get(bindingIndex);
                DashboardFilterBindingPO bindingRow = new DashboardFilterBindingPO();
                bindingRow.setDashboardVersionId(versionId);
                bindingRow.setFilterKey(filter.filterKey());
                bindingRow.setWidgetKey(binding.widgetKey());
                bindingRow.setFieldId(binding.fieldId());
                bindingRow.setSortOrder(bindingIndex + 1);
                requireInserted(
                        dashboardDao.insertFilterBinding(bindingRow),
                        "创建 DashboardFilterBinding 失败");
            }
        }

        for (int index = 0; index < draft.interactions().size(); index++) {
            var interaction = draft.interactions().get(index);
            DashboardInteractionPO row = new DashboardInteractionPO();
            row.setDashboardVersionId(versionId);
            row.setInteractionKey(interaction.interactionKey());
            row.setEventType(interaction.event().name());
            row.setSourceWidgetKey(interaction.sourceWidgetKey());
            row.setSourceFieldId(interaction.sourceFieldId());
            row.setTargetFilterKey(interaction.targetFilterKey());
            row.setSortOrder(index + 1);
            requireInserted(dashboardDao.insertInteraction(row), "创建 DashboardInteraction 失败");
        }
        return versionId;
    }

    @Override
    public void updateCurrentVersion(
            long dashboardId,
            long versionId,
            int versionNo,
            String name,
            String description) {
        if (dashboardDao.updateCurrentVersion(
                dashboardId,
                versionId,
                versionNo,
                name,
                description) != 1) {
            throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
        }
    }

    @Override
    public void updatePublishedVersion(long dashboardId, long versionId, int versionNo) {
        if (dashboardDao.updatePublishedVersion(dashboardId, versionId, versionNo) != 1) {
            throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
        }
    }

    @Override
    public Optional<DashboardAsset> findDashboard(long dashboardId) {
        return Optional.ofNullable(dashboardDao.selectDashboard(dashboardId))
                .map(this::toDomain);
    }

    @Override
    public List<DashboardAsset> listDashboards() {
        return dashboardDao.selectDashboards().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<DashboardVersionSnapshot> findVersionSnapshot(long versionId) {
        return Optional.ofNullable(dashboardDao.selectVersion(versionId))
                .map(this::snapshot);
    }

    @Override
    public Optional<DashboardVersionSnapshot> findVersionSnapshotByNo(
            long dashboardId,
            int versionNo) {
        return Optional.ofNullable(dashboardDao.selectVersionByNo(dashboardId, versionNo))
                .map(this::snapshot);
    }

    @Override
    public List<DashboardVersion> listVersions(long dashboardId) {
        return dashboardDao.selectVersions(dashboardId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int nextVersionNo(long dashboardId) {
        return dashboardDao.selectNextVersionNo(dashboardId);
    }

    @Override
    public void deleteDashboard(long dashboardId) {
        if (dashboardDao.deleteDashboard(dashboardId) != 1) {
            throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
        }
    }

    private DashboardVersionSnapshot snapshot(DashboardVersionPO version) {
        long versionId = version.getId();
        List<DashboardWidgetSnapshot> widgets = dashboardDao.selectWidgets(versionId).stream()
                .map(this::toDomain)
                .toList();

        Map<String, List<DashboardFilterBindingPO>> bindingsByFilter =
                dashboardDao.selectFilterBindings(versionId).stream()
                        .collect(Collectors.groupingBy(
                                DashboardFilterBindingPO::getFilterKey,
                                java.util.LinkedHashMap::new,
                                Collectors.toList()));
        List<DashboardGlobalFilterSnapshot> filters = dashboardDao.selectFilters(versionId).stream()
                .map(filter -> toDomain(
                        filter,
                        bindingsByFilter.getOrDefault(filter.getFilterKey(), List.of())))
                .toList();
        List<DashboardInteractionSnapshot> interactions = dashboardDao
                .selectInteractions(versionId).stream()
                .map(this::toDomain)
                .toList();

        return new DashboardVersionSnapshot(
                toDomain(version),
                jsonCodec.read(version.getThemeJson()),
                widgets,
                filters,
                interactions);
    }

    private DashboardAsset toDomain(DashboardPO row) {
        return new DashboardAsset(
                row.getId(),
                row.getName(),
                row.getDescription(),
                row.getCurrentVersionId(),
                value(row.getCurrentVersionNo()),
                row.getPublishedVersionId(),
                value(row.getPublishedVersionNo()),
                instant(row.getPublishedTime()),
                instant(row.getCreateTime()),
                instant(row.getUpdateTime()));
    }

    private DashboardVersion toDomain(DashboardVersionPO row) {
        return new DashboardVersion(
                row.getId(),
                row.getDashboardId(),
                value(row.getVersionNo()),
                row.getNameSnapshot(),
                row.getDescriptionSnapshot(),
                row.getActiveDatasetId(),
                instant(row.getCreateTime()));
    }

    private DashboardWidgetSnapshot toDomain(DashboardWidgetPO row) {
        return new DashboardWidgetSnapshot(
                row.getId(),
                row.getDashboardVersionId(),
                row.getWidgetKey(),
                row.getAnalysisId(),
                row.getTitle(),
                jsonCodec.read(row.getInlineAnalysisJson()),
                value(row.getGridX()),
                value(row.getGridY()),
                value(row.getGridW()),
                value(row.getGridH()),
                row.getMinW(),
                row.getMinH(),
                value(row.getSortOrder()));
    }

    private DashboardGlobalFilterSnapshot toDomain(
            DashboardFilterPO row,
            List<DashboardFilterBindingPO> bindings) {
        List<DashboardGlobalFilterBindingSnapshot> domainBindings = new ArrayList<>(bindings.size());
        for (DashboardFilterBindingPO binding : bindings) {
            domainBindings.add(new DashboardGlobalFilterBindingSnapshot(
                    binding.getWidgetKey(),
                    binding.getFieldId(),
                    value(binding.getSortOrder())));
        }
        return new DashboardGlobalFilterSnapshot(
                row.getFilterKey(),
                row.getName(),
                DashboardGlobalFilterOperator.valueOf(row.getOperator()),
                jsonCodec.read(row.getDefaultValueJson()),
                List.copyOf(domainBindings),
                value(row.getSortOrder()));
    }

    private DashboardInteractionSnapshot toDomain(DashboardInteractionPO row) {
        return new DashboardInteractionSnapshot(
                row.getInteractionKey(),
                DashboardInteractionEvent.valueOf(row.getEventType()),
                row.getSourceWidgetKey(),
                row.getSourceFieldId(),
                row.getTargetFilterKey(),
                value(row.getSortOrder()));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private void requireInserted(int count, String message) {
        if (count != 1) {
            throw new IllegalStateException(message);
        }
    }
}
