package io.yak.ops.business.quality.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEventSpec;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettingsSpec;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSpec;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionSpec;
import io.yak.ops.business.quality.domain.QualityDomain.RuleSpec;
import io.yak.ops.business.quality.domain.QualityDomain.ScheduledMonitor;
import io.yak.ops.business.quality.domain.QualityDomain.TableAsset;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetSpec;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetTarget;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.execution.QualityRuntime.ExecutionJob;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 数据质量核心 Repository。领域层不感知 MyBatis、PO 或 HTTP DTO/VO。 */
public interface QualityRepository {
  List<Template> listTemplates(QualityQuery.Template query);
  Optional<Template> findTemplate(long id);

  PageData<TableAsset> pageTableAssets(QualityQuery.TableAsset query);
  List<TableAssetTarget> listTableAssetTargets(long dataSourceId, String databaseName);
  boolean existsTableAssetTarget(long dataSourceId, String databaseName, String schemaName, String tableName);
  int registerTableAssets(List<TableAssetSpec> assets);
  int countMonitorsForTableAsset(long assetId);
  boolean deleteTableAsset(long assetId);

  PageData<Monitor> pageMonitors(QualityQuery.Monitor query);
  Optional<Monitor> findMonitor(long id);
  List<TableMonitorSummary> tableSummaries(long dataSourceId, String databaseName, String schemaName);
  boolean existsMonitorForTarget(Long excludeId, long dataSourceId, String databaseName, String schemaName, String tableName);
  long insertMonitor(MonitorSpec monitor);
  boolean updateMonitor(long id, MonitorSpec monitor);
  boolean deleteMonitor(long id);
  MonitorSettings findMonitorSettings(long monitorId);
  void upsertMonitorSettings(long monitorId, MonitorSettingsSpec settings);
  List<ScheduledMonitor> listDueMonitors(LocalDateTime now, int limit);
  boolean claimMonitorSchedule(long monitorId, LocalDateTime expectedRunTime, LocalDateTime nextRunTime);
  void insertAlertEvent(AlertEventSpec alert);
  void replaceRules(long monitorId, List<RuleSpec> rules);
  List<Rule> listRules(long monitorId);

  ExecutionJob executionJob(long monitorId, long executionId, String executionNo);
  void lockMonitor(long monitorId);
  boolean hasActiveExecution(long monitorId);
  long insertExecution(String executionNo, Monitor monitor, int totalRules, String operator,
      TriggerType triggerType, LocalDateTime queuedAt);
  boolean markExecutionRunning(long id, LocalDateTime startedAt);
  void insertRuleExecution(RuleExecutionSpec ruleExecution);
  boolean completeExecution(long id, CheckResult result, int passed, int failed, int errors,
      LocalDateTime finishedAt, long durationMs);
  boolean failExecution(long id, String errorMessage, LocalDateTime finishedAt, long durationMs);
  boolean updateMonitorResult(long monitorId, String executionNo, CheckResult result, LocalDateTime runTime);
  PageData<Execution> pageExecutions(QualityQuery.Execution query);
  Optional<Execution> findExecution(String executionNo);
}
