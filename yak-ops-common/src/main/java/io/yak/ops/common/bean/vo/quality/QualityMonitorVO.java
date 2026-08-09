package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 数据质量监控响应契约。 */
public final class QualityMonitorVO {
  private QualityMonitorVO() {}

  public record Settings(
      RunMode runMode,
      ScheduleFrequency scheduleFrequency,
      String scheduleTime,
      ScheduleWeekday scheduleWeekday,
      String cronExpression,
      @QualityDateTimeFormat LocalDateTime nextRunTime,
      RuleFailureAction ruleFailureAction,
      boolean notifyEnabled,
      NotifyChannel notifyChannel,
      String notifyTarget,
      AlertLevel alertLevel) {}

  public record Rule(
      Long id,
      Long monitorId,
      Long templateId,
      String templateCode,
      String name,
      RuleType ruleType,
      RuleScope scope,
      String dimension,
      String columnName,
      String operator,
      BigDecimal threshold,
      BigDecimal thresholdEnd,
      List<String> enumValues,
      String customSql,
      boolean enabled,
      int sortOrder) {}

  public record ListItem(
      Long id,
      String name,
      String description,
      Long dataSourceId,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String owner,
      boolean enabled,
      int ruleCount,
      CheckResult lastResult,
      String lastExecutionNo,
      @QualityDateTimeFormat LocalDateTime lastRunTime,
      @QualityDateTimeFormat LocalDateTime createTime,
      @QualityDateTimeFormat LocalDateTime updateTime) {}

  public record Detail(
      Long id,
      String name,
      String description,
      Long dataSourceId,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String whereClause,
      String owner,
      boolean enabled,
      CheckResult lastResult,
      String lastExecutionNo,
      @QualityDateTimeFormat LocalDateTime lastRunTime,
      @QualityDateTimeFormat LocalDateTime createTime,
      @QualityDateTimeFormat LocalDateTime updateTime,
      List<Rule> rules) {}

  public record Page(List<ListItem> records, long total, int current, int pageSize) {}

  public record TableSummary(
      String tableName,
      Long monitorId,
      String monitorName,
      int monitorCount,
      int ruleCount,
      CheckResult lastResult,
      @QualityDateTimeFormat LocalDateTime lastRunTime) {}

  public record Run(String executionNo, ExecutionStatus executionStatus, CheckResult checkResult) {}
}
