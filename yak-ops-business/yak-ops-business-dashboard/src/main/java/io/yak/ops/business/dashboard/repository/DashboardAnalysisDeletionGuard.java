package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.analysis.AnalysisDeletionGuard;
import io.yak.ops.business.dashboard.dao.DashboardDao;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Preserves the former dashboard-widget Analysis RESTRICT rule at the application boundary. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DashboardAnalysisDeletionGuard implements AnalysisDeletionGuard {

    private final DashboardDao dashboardDao;

    @Override
    public void requireDeletable(long analysisId) {
        if (dashboardDao.existsWidgetByAnalysisId(analysisId)) {
            throw new IllegalStateException(
                    "Analysis 仍被 Dashboard 历史版本引用，不能删除：" + analysisId);
        }
    }
}
