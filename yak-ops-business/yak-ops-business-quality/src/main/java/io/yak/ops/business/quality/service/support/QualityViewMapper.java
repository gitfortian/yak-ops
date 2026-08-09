package io.yak.ops.business.quality.service.support;

import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.OperationLog;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityDomain.TableAsset;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityDomain.TemplateFolder;
import io.yak.ops.common.bean.vo.quality.CustomQualityTemplateVO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionVO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionWorkspaceVO;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.common.bean.vo.quality.QualityTableAssetVO;
import io.yak.ops.common.bean.vo.quality.QualityTemplateVO;
import io.yak.ops.common.bean.vo.quality.QualityWorkspaceVO;

/** Domain -> API VO 的无状态转换。 */
public final class QualityViewMapper {
  private QualityViewMapper() {}

  public static QualityTemplateVO.Template template(Template v) {
    return new QualityTemplateVO.Template(v.id(), v.code(), v.name(), v.description(), v.ruleType(),
        v.scope(), v.dimension(), v.parameterSchema(), v.builtin(), v.enabled(), v.ruleCount(), v.sortOrder());
  }

  public static QualityMonitorVO.Rule rule(Rule v) {
    return new QualityMonitorVO.Rule(v.id(), v.monitorId(), v.templateId(), v.templateCode(), v.name(),
        v.ruleType(), v.scope(), v.dimension(), v.columnName(), v.operator(), v.threshold(), v.thresholdEnd(),
        v.enumValues(), v.customSql(), v.enabled(), v.sortOrder());
  }

  public static QualityMonitorVO.ListItem monitorList(Monitor v) {
    return new QualityMonitorVO.ListItem(v.id(), v.name(), v.description(), v.dataSourceId(), v.dataSourceName(),
        v.databaseName(), v.schemaName(), v.tableName(), v.owner(), v.enabled(), v.ruleCount(), v.lastResult(),
        v.lastExecutionNo(), v.lastRunTime(), v.createTime(), v.updateTime());
  }

  public static QualityMonitorVO.Detail monitor(Monitor v) {
    return new QualityMonitorVO.Detail(v.id(), v.name(), v.description(), v.dataSourceId(), v.dataSourceName(),
        v.databaseName(), v.schemaName(), v.tableName(), v.whereClause(), v.owner(), v.enabled(), v.lastResult(),
        v.lastExecutionNo(), v.lastRunTime(), v.createTime(), v.updateTime(), v.rules().stream().map(QualityViewMapper::rule).toList());
  }

  public static QualityMonitorVO.Settings settings(MonitorSettings v) {
    return new QualityMonitorVO.Settings(v.runMode(), v.scheduleFrequency(), v.scheduleTime(), v.scheduleWeekday(),
        v.cronExpression(), v.nextRunTime(), v.ruleFailureAction(), v.notifyEnabled(), v.notifyChannel(),
        v.notifyTarget(), v.alertLevel());
  }

  public static QualityMonitorVO.TableSummary tableSummary(TableMonitorSummary v) {
    return new QualityMonitorVO.TableSummary(v.tableName(), v.monitorId(), v.monitorName(), v.monitorCount(),
        v.ruleCount(), v.lastResult(), v.lastRunTime());
  }

  public static QualityTableAssetVO.Asset tableAsset(TableAsset v) {
    return new QualityTableAssetVO.Asset(v.id(), v.dataSourceId(), v.dataSourceName(), v.databaseName(), v.schemaName(),
        v.tableName(), v.tableType(), v.remarks(), v.monitorId(), v.monitorName(), v.monitorCount(), v.ruleCount(),
        v.lastResult(), v.lastRunTime(), v.registeredBy(), v.registeredAt());
  }

  public static QualityExecutionVO.RuleExecution ruleExecution(RuleExecution v) {
    return new QualityExecutionVO.RuleExecution(v.id(), v.ruleId(), v.ruleName(), v.templateCode(), v.ruleType(),
        v.columnName(), v.checkResult(), v.metricValue(), v.expectedValue(), v.executedSql(), v.errorMessage(), v.durationMs());
  }

  public static QualityExecutionVO.ListItem executionList(Execution v) {
    return new QualityExecutionVO.ListItem(v.executionNo(), v.monitorId(), v.monitorName(), v.dataSourceName(), v.objectName(),
        v.executionStatus(), v.checkResult(), v.totalRules(), v.passedRules(), v.failedRules(), v.errorRules(), v.operator(),
        v.queuedAt(), v.startedAt(), v.finishedAt(), v.durationMs(), v.errorMessage());
  }

  public static QualityExecutionVO.Detail execution(Execution v) {
    return new QualityExecutionVO.Detail(v.executionNo(), v.monitorId(), v.monitorName(), v.dataSourceName(), v.databaseName(),
        v.schemaName(), v.tableName(), v.objectName(), v.executionStatus(), v.checkResult(), v.totalRules(), v.passedRules(),
        v.failedRules(), v.errorRules(), v.operator(), v.queuedAt(), v.startedAt(), v.finishedAt(), v.durationMs(), v.errorMessage(),
        v.rules().stream().map(QualityViewMapper::ruleExecution).toList());
  }

  public static CustomQualityTemplateVO.Template customTemplate(CustomTemplate v) {
    return new CustomQualityTemplateVO.Template(v.id(), v.code(), v.name(), v.description(), v.ruleType(), v.scope(), v.dimension(),
        v.parameterSchema(), v.builtin(), v.enabled(), v.ruleCount(), v.sortOrder(), v.folderId(), v.folderName(), v.templateSql(),
        v.setFlag(), v.checkType(), v.checkMethod(), v.createdBy(), v.createdAt(), v.updatedAt());
  }

  public static CustomQualityTemplateVO.Folder folder(TemplateFolder v) {
    return new CustomQualityTemplateVO.Folder(v.id(), v.parentId(), v.name(), v.sortOrder(), v.templateCount(), v.childCount(),
        v.createdAt(), v.updatedAt());
  }

  public static QualityExecutionWorkspaceVO.ExecutionListItem executionWorkspaceList(Execution v) {
    return new QualityExecutionWorkspaceVO.ExecutionListItem(v.executionNo(), v.monitorId(), v.monitorName(), v.dataSourceId(),
        v.dataSourceName(), v.objectName(), v.triggerType(), v.executionStatus(), v.checkResult(), v.totalRules(), v.passedRules(),
        v.failedRules(), v.errorRules(), v.operator(), v.queuedAt(), v.startedAt(), v.finishedAt(), v.durationMs(), v.errorMessage());
  }

  public static QualityExecutionWorkspaceVO.RuleExecutionListItem ruleWorkspace(RuleExecutionWorkspaceItem v) {
    return new QualityExecutionWorkspaceVO.RuleExecutionListItem(v.id(), v.ruleId(), v.executionNo(), v.monitorId(), v.monitorName(),
        v.dataSourceId(), v.dataSourceName(), v.databaseName(), v.schemaName(), v.tableName(), v.objectName(), v.ruleName(),
        v.templateCode(), v.ruleType(), v.scope(), v.dimension(), v.columnName(), v.triggerType(), v.executionStatus(), v.checkResult(),
        v.metricValue(), v.expectedValue(), v.operator(), v.queuedAt(), v.startedAt(), v.finishedAt(), v.durationMs(), v.errorMessage());
  }

  public static QualityExecutionWorkspaceVO.RuleExecution workspaceRuleExecution(RuleExecution v) {
    return new QualityExecutionWorkspaceVO.RuleExecution(v.id(), v.ruleId(), v.ruleName(), v.templateCode(), v.ruleType(),
        v.ruleType().scope(), v.ruleType().dimension(), v.columnName(), v.checkResult(), v.metricValue(), v.expectedValue(),
        v.executedSql(), v.errorMessage(), v.durationMs(), v.createdAt());
  }

  public static QualityExecutionWorkspaceVO.ExecutionDetail executionWorkspace(Execution v) {
    return new QualityExecutionWorkspaceVO.ExecutionDetail(v.executionNo(), v.monitorId(), v.monitorName(), v.dataSourceId(),
        v.dataSourceName(), v.databaseName(), v.schemaName(), v.tableName(), v.objectName(), v.triggerType(), v.executionStatus(),
        v.checkResult(), v.totalRules(), v.passedRules(), v.failedRules(), v.errorRules(), v.operator(), v.queuedAt(), v.startedAt(),
        v.finishedAt(), v.durationMs(), v.errorMessage(), v.rules().stream().map(QualityViewMapper::workspaceRuleExecution).toList());
  }

  public static QualityWorkspaceVO.OperationLogItem operationLog(OperationLog v) {
    return new QualityWorkspaceVO.OperationLogItem(v.id(), v.operator(), v.operationTime(), v.actionType(), v.content());
  }
}
