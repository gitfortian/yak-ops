package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityAnalyticsDao;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.DimensionRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.IssueRow;
import io.yak.ops.business.quality.dao.model.QualityOverviewPO.StatsRow;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 首页质量总览持久化适配器。 */
@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityOverviewRepositoryAdapter implements QualityOverviewRepository {

  private final QualityAnalyticsDao analyticsDao;

  @Override
  public OverviewStats stats(
      LocalDateTime todayStart,
      LocalDateTime todayEnd,
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd) {
    StatsRow row = analyticsDao.selectHomeOverviewStats(
        params(todayStart, todayEnd, rangeStart, rangeEnd));
    if (row == null) {
      return new OverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }
    return new OverviewStats(
        nvl(row.getMonitoredTableCount()),
        nvl(row.getEnabledRuleCount()),
        nvl(row.getTodayExecutionCount()),
        nvl(row.getTodayIssueTableCount()),
        nvl(row.getRecentExecutedRuleCount()),
        nvl(row.getRecentPassedRuleCount()),
        nvl(row.getRecentIssueRuleCount()));
  }

  @Override
  public List<DimensionSummary> dimensions(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
    return analyticsDao.selectHomeOverviewDimensions(params(null, null, rangeStart, rangeEnd)).stream()
        .map(this::dimension)
        .toList();
  }

  @Override
  public List<IssueSummary> recentIssues(
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd,
      int limit) {
    Map<String, Object> params = params(null, null, rangeStart, rangeEnd);
    params.put("limit", Math.max(1, limit));
    return analyticsDao.selectHomeOverviewIssues(params).stream()
        .map(this::issue)
        .toList();
  }

  private DimensionSummary dimension(DimensionRow row) {
    return new DimensionSummary(
        text(row.getDimension(), "其他"),
        nvl(row.getTotalCount()),
        nvl(row.getPassedCount()),
        nvl(row.getIssueCount()));
  }

  private IssueSummary issue(IssueRow row) {
    return new IssueSummary(
        nvl(row.getRuleExecutionId()),
        text(row.getExecutionNo(), ""),
        nvl(row.getMonitorId()),
        text(row.getMonitorName(), "未命名质量监控"),
        text(row.getObjectName(), row.getTableName()),
        text(row.getTableName(), ""),
        text(row.getRuleName(), "未命名规则"),
        text(row.getDimension(), "其他"),
        row.getColumnName(),
        text(row.getCheckResult(), "NOT_PASSED"),
        row.getQueuedAt());
  }

  private Map<String, Object> params(
      LocalDateTime todayStart,
      LocalDateTime todayEnd,
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd) {
    Map<String, Object> params = new LinkedHashMap<>();
    if (todayStart != null) params.put("todayStart", todayStart);
    if (todayEnd != null) params.put("todayEnd", todayEnd);
    params.put("rangeStart", rangeStart);
    params.put("rangeEnd", rangeEnd);
    return params;
  }

  private static long nvl(Long value) {
    return value == null ? 0L : value;
  }

  private static String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
