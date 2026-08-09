package io.yak.ops.business.quality.service;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityExecutionWorkspaceRepository;
import io.yak.ops.business.quality.service.support.QualityViewMapper;
import io.yak.ops.common.bean.dto.quality.QualityExecutionWorkspaceDTO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionWorkspaceVO;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.LogLevel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ConditionalOnQualityEnabled
@Service
public class QualityExecutionWorkspaceService {

  private final QualityExecutionWorkspaceRepository repository;

  public QualityExecutionWorkspaceService(QualityExecutionWorkspaceRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionWorkspaceVO.ExecutionPage page(QualityExecutionWorkspaceDTO.PageRequest request) {
    QualityQuery.ExecutionWorkspace query = query(request);
    var result = repository.page(query);
    return new QualityExecutionWorkspaceVO.ExecutionPage(
        result.records().stream().map(QualityViewMapper::executionWorkspaceList).toList(),
        result.total(), query.current(), query.pageSize());
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionWorkspaceVO.RuleExecutionPage pageRules(QualityExecutionWorkspaceDTO.PageRequest request) {
    QualityQuery.ExecutionWorkspace query = query(request);
    var result = repository.pageRules(query);
    return new QualityExecutionWorkspaceVO.RuleExecutionPage(
        result.records().stream().map(QualityViewMapper::ruleWorkspace).toList(),
        result.total(), query.current(), query.pageSize());
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionWorkspaceVO.ExecutionDetail get(String executionNo) {
    return repository.find(executionNo)
        .map(QualityViewMapper::executionWorkspace)
        .orElseThrow(() -> new IllegalArgumentException("质量执行记录不存在：" + executionNo));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionWorkspaceVO.LogView logs(String executionNo) {
    Execution execution = repository.find(executionNo)
        .orElseThrow(() -> new IllegalArgumentException("质量执行记录不存在：" + executionNo));
    List<QualityExecutionWorkspaceVO.LogLine> lines = new ArrayList<>();
    lines.add(new QualityExecutionWorkspaceVO.LogLine(
        execution.queuedAt(), LogLevel.INFO, "DISPATCH",
        "执行任务已创建，触发方式：" + (execution.triggerType().name().equals("SCHEDULE") ? "调度触发" : "手动触发")
            + "，操作人：" + safe(execution.operator())));

    if (execution.startedAt() != null) {
      lines.add(new QualityExecutionWorkspaceVO.LogLine(
          execution.startedAt(), LogLevel.INFO, "EXECUTION",
          "开始执行质量检查，共 " + execution.totalRules() + " 条规则"));
    }

    for (var rule : execution.rules()) {
      lines.add(new QualityExecutionWorkspaceVO.LogLine(
          fallback(rule.createdAt(), execution.startedAt(), execution.queuedAt()),
          logLevel(rule.checkResult()), "RULE", ruleMessage(rule)));
    }

    if (execution.errorMessage() != null && !execution.errorMessage().isBlank()) {
      lines.add(new QualityExecutionWorkspaceVO.LogLine(
          fallback(execution.finishedAt(), execution.startedAt(), execution.queuedAt()),
          LogLevel.ERROR, "EXECUTION", execution.errorMessage()));
    }

    if (execution.finishedAt() != null) {
      lines.add(new QualityExecutionWorkspaceVO.LogLine(
          execution.finishedAt(), execution.checkResult() == CheckResult.PASSED ? LogLevel.INFO : LogLevel.WARN,
          "FINISH", "执行结束：通过 " + execution.passedRules() + "，未通过 " + execution.failedRules()
              + "，异常 " + execution.errorRules() + "，耗时 "
              + (execution.durationMs() == null ? 0 : execution.durationMs()) + " ms"));
    }
    return new QualityExecutionWorkspaceVO.LogView(execution.executionNo(), List.copyOf(lines));
  }

  private QualityQuery.ExecutionWorkspace query(QualityExecutionWorkspaceDTO.PageRequest request) {
    QualityExecutionWorkspaceDTO.PageRequest v = request == null
        ? new QualityExecutionWorkspaceDTO.PageRequest(
            1, 20, null, null, null, null, null, null, null, null, null, null, null, null)
        : request;
    return new QualityQuery.ExecutionWorkspace(
        v.normalizedCurrent(), v.normalizedPageSize(), v.keyword(), v.objectKeyword(),
        v.dataSourceId(), v.monitorId(), v.executionStatus(), v.checkResult(), v.triggerType(),
        v.hasIssues(), v.dimension(), v.scope(), v.queuedAfter(), v.queuedBefore());
  }

  private static String ruleMessage(io.yak.ops.business.quality.domain.QualityDomain.RuleExecution rule) {
    StringBuilder message = new StringBuilder().append("规则「").append(rule.ruleName()).append("」")
        .append(resultLabel(rule.checkResult()));
    if (hasText(rule.metricValue())) message.append("，实际值：").append(rule.metricValue());
    if (hasText(rule.expectedValue())) message.append("，期望值：").append(rule.expectedValue());
    if (rule.durationMs() != null) message.append("，耗时：").append(rule.durationMs()).append(" ms");
    if (hasText(rule.errorMessage())) message.append("，错误：").append(rule.errorMessage());
    return message.toString();
  }

  private static String resultLabel(CheckResult result) {
    return switch (result) {
      case PASSED -> "通过";
      case NOT_PASSED -> "未通过";
      case ERROR -> "执行异常";
      case RUNNING -> "运行中";
      case NOT_RUN -> "未运行";
    };
  }

  private static LogLevel logLevel(CheckResult result) {
    return switch (result) {
      case PASSED, RUNNING, NOT_RUN -> LogLevel.INFO;
      case NOT_PASSED -> LogLevel.WARN;
      case ERROR -> LogLevel.ERROR;
    };
  }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }
  private static String safe(String value) { return hasText(value) ? value : "system"; }
  private static LocalDateTime fallback(LocalDateTime... values) {
    for (LocalDateTime value : values) if (value != null) return value;
    return LocalDateTime.now();
  }
}
