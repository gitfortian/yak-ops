package io.yak.ops.business.quality.dao.impl;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityAnalyticsDao;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.ColumnReportRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.DimensionReportRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.OperationLogRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.ReportOverviewRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TrendPointRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.WorkspaceStatsRow;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityAnalyticsDaoImpl implements QualityAnalyticsDao {
  private final QualityQueryMapper queryMapper;

  @Override public WorkspaceStatsRow selectStats(long monitorId) { return queryMapper.selectWorkspaceStats(monitorId); }
  @Override public ReportOverviewRow selectOverview(Map<String, Object> params) { return queryMapper.selectReportOverview(params); }
  @Override public List<DimensionReportRow> selectDimensions(Map<String, Object> params) { return queryMapper.selectDimensionReport(params); }
  @Override public List<TrendPointRow> selectTrend(Map<String, Object> params) { return queryMapper.selectTrend(params); }
  @Override public List<ColumnReportRow> selectColumns(Map<String, Object> params) { return queryMapper.selectColumnReport(params); }
  @Override public long countOperationLogs(long monitorId) { return queryMapper.countOperationLogs(monitorId); }
  @Override public List<OperationLogRow> selectOperationLogs(Map<String, Object> params) { return queryMapper.selectOperationLogs(params); }
}
