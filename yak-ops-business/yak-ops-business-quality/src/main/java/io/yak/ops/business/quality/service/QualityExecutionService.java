package io.yak.ops.business.quality.service;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.execution.QualityExecutionWorker;
import io.yak.ops.business.quality.execution.QualityRuntime.ExecutionJob;
import io.yak.ops.business.quality.repository.QualityRepository;
import io.yak.ops.business.quality.service.support.QualityViewMapper;
import io.yak.ops.common.bean.dto.quality.QualityExecutionDTO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionVO;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ConditionalOnQualityEnabled
@Service
public class QualityExecutionService {

  private static final DateTimeFormatter EXECUTION_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

  private final QualityRepository repository;
  private final QualityExecutionWorker worker;
  private final ThreadPoolTaskExecutor taskExecutor;

  public QualityExecutionService(
      QualityRepository repository,
      QualityExecutionWorker worker,
      @Qualifier("qualityExecutionTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
    this.repository = repository;
    this.worker = worker;
    this.taskExecutor = taskExecutor;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public QualityMonitorVO.Run run(long monitorId, String operator) {
    return enqueue(monitorId, operator, TriggerType.MANUAL);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public QualityMonitorVO.Run runScheduled(long monitorId) {
    return enqueue(monitorId, "quality-scheduler", TriggerType.SCHEDULE);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionVO.Page page(QualityExecutionDTO.PageRequest request) {
    QualityExecutionDTO.PageRequest normalized = request == null
        ? new QualityExecutionDTO.PageRequest(1, 20, null, null, null, null)
        : request;
    QualityQuery.Execution query = new QualityQuery.Execution(
        normalized.normalizedCurrent(), normalized.normalizedPageSize(), normalized.keyword(),
        normalized.monitorId(), normalized.executionStatus(), normalized.checkResult());
    var result = repository.pageExecutions(query);
    return new QualityExecutionVO.Page(
        result.records().stream().map(QualityViewMapper::executionList).toList(),
        result.total(), query.current(), query.pageSize());
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityExecutionVO.Detail get(String executionNo) {
    return repository.findExecution(executionNo)
        .map(QualityViewMapper::execution)
        .orElseThrow(() -> new IllegalArgumentException("质量执行记录不存在：" + executionNo));
  }

  private QualityMonitorVO.Run enqueue(long monitorId, String operator, TriggerType triggerType) {
    repository.lockMonitor(monitorId);
    Monitor monitor = repository.findMonitor(monitorId)
        .orElseThrow(() -> new IllegalArgumentException("质量监控不存在：" + monitorId));
    if (!monitor.enabled()) {
      throw new IllegalStateException("质量监控已停用，无法执行");
    }
    int enabledRules = (int) monitor.rules().stream().filter(rule -> rule.enabled()).count();
    if (enabledRules == 0) {
      throw new IllegalStateException("质量监控没有可执行规则");
    }
    if (repository.hasActiveExecution(monitorId)) {
      throw new IllegalStateException("该质量监控已有运行中的检查任务");
    }

    LocalDateTime queuedAt = LocalDateTime.now();
    String executionNo = executionNo(queuedAt);
    long executionId = repository.insertExecution(
        executionNo, monitor, enabledRules, normalizeOperator(operator), triggerType, queuedAt);
    ExecutionJob job = repository.executionJob(monitorId, executionId, executionNo);
    dispatchAfterCommit(job);
    return new QualityMonitorVO.Run(executionNo, ExecutionStatus.WAITING, CheckResult.RUNNING);
  }

  private void dispatchAfterCommit(ExecutionJob job) {
    Runnable dispatch = () -> {
      try {
        taskExecutor.execute(() -> worker.execute(job));
      } catch (TaskRejectedException exception) {
        LocalDateTime now = LocalDateTime.now();
        repository.failExecution(job.executionId(), "质量执行队列已满", now, 0L);
        repository.updateMonitorResult(job.monitor().id(), job.executionNo(), CheckResult.ERROR, now);
      }
    };
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      dispatch.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        dispatch.run();
      }
    });
  }

  private static String executionNo(LocalDateTime queuedAt) {
    return "QM-" + EXECUTION_TIME.format(queuedAt) + "-"
        + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
  }

  private static String normalizeOperator(String operator) {
    return operator == null || operator.isBlank() ? "system" : operator.trim();
  }
}
