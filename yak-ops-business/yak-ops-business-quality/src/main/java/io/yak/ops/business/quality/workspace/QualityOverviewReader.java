package io.yak.ops.business.quality.workspace;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.repository.QualityOverviewRepository;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.DimensionSummary;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.IssueSummary;
import io.yak.ops.business.quality.repository.QualityOverviewRepository.OverviewStats;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首页数据质量健康度、关键指标和问题列表读模型。 */
@Component
@ConditionalOnQualityEnabled
public class QualityOverviewReader {

  private static final int RANGE_DAYS = 7;
  private static final int RECENT_ISSUE_LIMIT = 3;

  private final QualityOverviewRepository repository;

  public QualityOverviewReader(QualityOverviewRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Overview overview() {
    LocalDate today = LocalDate.now();
    LocalDate rangeStartDate = today.minusDays(RANGE_DAYS - 1L);
    LocalDateTime todayStart = today.atStartOfDay();
    LocalDateTime rangeStart = rangeStartDate.atStartOfDay();
    LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();

    OverviewStats stats = repository.stats(todayStart, rangeEnd, rangeStart, rangeEnd);
    List<DimensionHealth> dimensions = repository.dimensions(rangeStart, rangeEnd).stream()
        .filter(item -> item.total() > 0L)
        .map(this::dimension)
        .toList();
    List<RecentIssue> recentIssues = repository.recentIssues(
            rangeStart, rangeEnd, RECENT_ISSUE_LIMIT).stream()
        .map(this::issue)
        .toList();

    return new Overview(
        rangeStartDate,
        today,
        rate(stats.recentPassedRuleCount(), stats.recentExecutedRuleCount()),
        stats.monitoredTableCount(),
        stats.enabledRuleCount(),
        stats.todayExecutionCount(),
        stats.todayIssueTableCount(),
        stats.recentIssueRuleCount(),
        dimensions,
        recentIssues);
  }

  private DimensionHealth dimension(DimensionSummary item) {
    return new DimensionHealth(
        item.dimension(),
        item.total(),
        item.issues(),
        rate(item.passed(), item.total()));
  }

  private RecentIssue issue(IssueSummary item) {
    return new RecentIssue(
        String.valueOf(item.id()),
        item.executionNo(),
        String.valueOf(item.monitorId()),
        item.monitorName(),
        item.objectName(),
        item.tableName(),
        item.ruleName(),
        item.dimension(),
        item.columnName(),
        item.checkResult(),
        item.queuedAt());
  }

  private static Double rate(long passed, long total) {
    if (total <= 0L) return null;
    return Math.round((passed * 10000D) / total) / 100D;
  }

  public record Overview(
      LocalDate rangeStart,
      LocalDate rangeEnd,
      Double passRate,
      long monitoredTableCount,
      long enabledRuleCount,
      long todayExecutionCount,
      long todayIssueTableCount,
      long recentIssueCount,
      List<DimensionHealth> dimensions,
      List<RecentIssue> recentIssues) {

    public Overview {
      dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
      recentIssues = recentIssues == null ? List.of() : List.copyOf(recentIssues);
    }
  }

  public record DimensionHealth(
      String dimension,
      long total,
      long issues,
      Double passRate) {
  }

  public record RecentIssue(
      String id,
      String executionNo,
      String monitorId,
      String monitorName,
      String objectName,
      String tableName,
      String ruleName,
      String dimension,
      String columnName,
      String checkResult,
      LocalDateTime queuedAt) {
  }
}
