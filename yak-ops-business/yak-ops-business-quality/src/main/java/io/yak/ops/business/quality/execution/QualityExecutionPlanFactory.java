package io.yak.ops.business.quality.execution;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.RuleSnapshot;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.springframework.stereotype.Component;

/** Freezes the Project identity, monitor definition and enabled rules into an immutable plan. */
@Component
@ConditionalOnQualityEnabled
public class QualityExecutionPlanFactory {
  private final QualityMonitorRepository monitorRepository;
  private final CurrentProject currentProject;

  public QualityExecutionPlanFactory(
      QualityMonitorRepository monitorRepository,
      CurrentProject currentProject) {
    this.monitorRepository = monitorRepository;
    this.currentProject = currentProject;
  }

  public QualityExecutionPlan create(
      Monitor monitor,
      long executionId,
      String executionNo) {
    long projectId = currentProject.requireProjectId();
    MonitorSettings settings = monitorRepository.findMonitorSettings(monitor.id());
    MonitorSnapshot monitorSnapshot =
        new MonitorSnapshot(
            monitor.id(),
            monitor.name(),
            monitor.dataSourceId(),
            monitor.dataSourceName(),
            monitor.databaseName(),
            monitor.schemaName(),
            monitor.tableName(),
            monitor.whereClause(),
            monitor.owner());
    List<RuleSnapshot> rules =
        monitor.rules().stream()
            .filter(Rule::enabled)
            .map(
                rule ->
                    new RuleSnapshot(
                        rule.id(),
                        rule.templateId(),
                        rule.templateCode(),
                        rule.name(),
                        rule.ruleType(),
                        rule.scope(),
                        rule.dimension(),
                        rule.columnName(),
                        ComparisonOperator.fromValue(rule.operator()),
                        rule.threshold(),
                        rule.thresholdEnd(),
                        rule.enumValues(),
                        rule.customSql()))
            .toList();
    return new QualityExecutionPlan(
        projectId,
        executionId,
        executionNo,
        monitorSnapshot,
        rules,
        settings.ruleFailureAction(),
        settings.notifyEnabled(),
        settings.notifyChannel(),
        settings.notifyTarget(),
        settings.alertLevel());
  }
}
