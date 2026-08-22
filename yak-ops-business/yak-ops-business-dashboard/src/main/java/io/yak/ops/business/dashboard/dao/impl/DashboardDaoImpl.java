package io.yak.ops.business.dashboard.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dashboard.dao.DashboardDao;
import io.yak.ops.business.dashboard.dao.mapper.DashboardFilterBindingMapper;
import io.yak.ops.business.dashboard.dao.mapper.DashboardFilterMapper;
import io.yak.ops.business.dashboard.dao.mapper.DashboardInteractionMapper;
import io.yak.ops.business.dashboard.dao.mapper.DashboardMapper;
import io.yak.ops.business.dashboard.dao.mapper.DashboardVersionMapper;
import io.yak.ops.business.dashboard.dao.mapper.DashboardWidgetMapper;
import io.yak.ops.business.dashboard.dao.model.DashboardFilterBindingPO;
import io.yak.ops.business.dashboard.dao.model.DashboardFilterPO;
import io.yak.ops.business.dashboard.dao.model.DashboardInteractionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import io.yak.ops.business.dashboard.dao.model.DashboardVersionPO;
import io.yak.ops.business.dashboard.dao.model.DashboardWidgetPO;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的 Dashboard DAO 实现。 */
@Repository
@DependsOn("yakDashboardFlyway")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DashboardDaoImpl implements DashboardDao {

    private final DashboardMapper dashboardMapper;
    private final DashboardVersionMapper versionMapper;
    private final DashboardWidgetMapper widgetMapper;
    private final DashboardFilterMapper filterMapper;
    private final DashboardFilterBindingMapper filterBindingMapper;
    private final DashboardInteractionMapper interactionMapper;

    @Override
    public int insertDashboard(DashboardPO dashboard) {
        dashboard.setCreateTime(Timestamp.from(Instant.now()));
        dashboard.setUpdateTime(dashboard.getCreateTime());
        dashboard.setCurrentVersionNo(0);
        dashboard.setPublishedVersionNo(0);
        return dashboardMapper.insert(dashboard);
    }

    @Override
    public int insertVersion(DashboardVersionPO version) {
        version.setCreateTime(Timestamp.from(Instant.now()));
        return versionMapper.insert(version);
    }

    @Override
    public int insertWidget(DashboardWidgetPO widget) {
        return widgetMapper.insert(widget);
    }

    @Override
    public int insertFilter(DashboardFilterPO filter) {
        return filterMapper.insert(filter);
    }

    @Override
    public int insertFilterBinding(DashboardFilterBindingPO binding) {
        return filterBindingMapper.insert(binding);
    }

    @Override
    public int insertInteraction(DashboardInteractionPO interaction) {
        return interactionMapper.insert(interaction);
    }

    @Override
    public int updateCurrentVersion(
            long dashboardId,
            long versionId,
            int versionNo,
            String name,
            String description) {
        return dashboardMapper.update(
                null,
                Wrappers.<DashboardPO>lambdaUpdate()
                        .eq(DashboardPO::getId, dashboardId)
                        .set(DashboardPO::getCurrentVersionId, versionId)
                        .set(DashboardPO::getCurrentVersionNo, versionNo)
                        .set(DashboardPO::getName, name)
                        .set(DashboardPO::getDescription, description)
                        .set(DashboardPO::getUpdateTime, Timestamp.from(Instant.now())));
    }

    @Override
    public int updatePublishedVersion(long dashboardId, long versionId, int versionNo) {
        Timestamp now = Timestamp.from(Instant.now());
        return dashboardMapper.update(
                null,
                Wrappers.<DashboardPO>lambdaUpdate()
                        .eq(DashboardPO::getId, dashboardId)
                        .set(DashboardPO::getPublishedVersionId, versionId)
                        .set(DashboardPO::getPublishedVersionNo, versionNo)
                        .set(DashboardPO::getPublishedTime, now)
                        .set(DashboardPO::getUpdateTime, now));
    }

    @Override
    public DashboardPO selectDashboard(long dashboardId) {
        return dashboardMapper.selectById(dashboardId);
    }

    @Override
    public List<DashboardPO> selectDashboards() {
        return dashboardMapper.selectList(
                Wrappers.<DashboardPO>lambdaQuery()
                        .orderByDesc(DashboardPO::getUpdateTime)
                        .orderByDesc(DashboardPO::getId));
    }

    @Override
    public DashboardVersionPO selectVersion(long versionId) {
        return versionMapper.selectById(versionId);
    }

    @Override
    public DashboardVersionPO selectVersionByNo(long dashboardId, int versionNo) {
        return versionMapper.selectOne(
                Wrappers.<DashboardVersionPO>lambdaQuery()
                        .eq(DashboardVersionPO::getDashboardId, dashboardId)
                        .eq(DashboardVersionPO::getVersionNo, versionNo));
    }

    @Override
    public List<DashboardVersionPO> selectVersions(long dashboardId) {
        return versionMapper.selectList(
                Wrappers.<DashboardVersionPO>lambdaQuery()
                        .eq(DashboardVersionPO::getDashboardId, dashboardId)
                        .orderByDesc(DashboardVersionPO::getVersionNo));
    }

    @Override
    public List<DashboardWidgetPO> selectWidgets(long versionId) {
        return widgetMapper.selectList(
                Wrappers.<DashboardWidgetPO>lambdaQuery()
                        .eq(DashboardWidgetPO::getDashboardVersionId, versionId)
                        .orderByAsc(DashboardWidgetPO::getSortOrder)
                        .orderByAsc(DashboardWidgetPO::getId));
    }

    @Override
    public List<DashboardFilterPO> selectFilters(long versionId) {
        return filterMapper.selectList(
                Wrappers.<DashboardFilterPO>lambdaQuery()
                        .eq(DashboardFilterPO::getDashboardVersionId, versionId)
                        .orderByAsc(DashboardFilterPO::getSortOrder)
                        .orderByAsc(DashboardFilterPO::getId));
    }

    @Override
    public List<DashboardFilterBindingPO> selectFilterBindings(long versionId) {
        return filterBindingMapper.selectList(
                Wrappers.<DashboardFilterBindingPO>lambdaQuery()
                        .eq(DashboardFilterBindingPO::getDashboardVersionId, versionId)
                        .orderByAsc(DashboardFilterBindingPO::getFilterKey)
                        .orderByAsc(DashboardFilterBindingPO::getSortOrder)
                        .orderByAsc(DashboardFilterBindingPO::getWidgetKey));
    }

    @Override
    public List<DashboardInteractionPO> selectInteractions(long versionId) {
        return interactionMapper.selectList(
                Wrappers.<DashboardInteractionPO>lambdaQuery()
                        .eq(DashboardInteractionPO::getDashboardVersionId, versionId)
                        .orderByAsc(DashboardInteractionPO::getSortOrder)
                        .orderByAsc(DashboardInteractionPO::getId));
    }

    @Override
    public int selectNextVersionNo(long dashboardId) {
        List<Object> values = versionMapper.selectObjs(
                Wrappers.<DashboardVersionPO>query()
                        .select("COALESCE(MAX(version_no), 0) + 1")
                        .eq("dashboard_id", dashboardId));
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 1;
        }
        return ((Number) values.get(0)).intValue();
    }

    @Override
    public int deleteDashboard(long dashboardId) {
        return dashboardMapper.deleteById(dashboardId);
    }
}
