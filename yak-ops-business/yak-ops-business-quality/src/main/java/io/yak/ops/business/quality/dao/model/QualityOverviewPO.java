package io.yak.ops.business.quality.dao.model;

import java.time.LocalDateTime;
import lombok.Data;

/** 首页质量总览使用的只读持久化投影。 */
public final class QualityOverviewPO {

  private QualityOverviewPO() {
  }

  @Data
  public static class StatsRow {
    private Long monitoredTableCount;
    private Long enabledRuleCount;
    private Long todayExecutionCount;
    private Long todayIssueTableCount;
    private Long recentExecutedRuleCount;
    private Long recentPassedRuleCount;
    private Long recentIssueRuleCount;
  }

  @Data
  public static class DimensionRow {
    private String dimension;
    private Long totalCount;
    private Long passedCount;
    private Long issueCount;
  }

  @Data
  public static class IssueRow {
    private Long ruleExecutionId;
    private String executionNo;
    private Long monitorId;
    private String monitorName;
    private String objectName;
    private String tableName;
    private String ruleName;
    private String dimension;
    private String columnName;
    private String checkResult;
    private LocalDateTime queuedAt;
  }
}
