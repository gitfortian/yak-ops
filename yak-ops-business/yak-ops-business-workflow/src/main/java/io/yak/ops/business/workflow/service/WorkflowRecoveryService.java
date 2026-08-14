package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.persistence.WorkflowRuntimePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/** Rebuilds non-terminal workflow runtime state after Yak Ops has fully started. */
@Service
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowRecoveryService {
  private static final Logger log = LoggerFactory.getLogger(WorkflowRecoveryService.class);

  private final WorkflowRuntimeService runtimeService;
  private final WorkflowRuntimePersistence runtimePersistence;

  public WorkflowRecoveryService(
      WorkflowRuntimeService runtimeService,
      WorkflowRuntimePersistence runtimePersistence) {
    this.runtimeService = runtimeService;
    this.runtimePersistence = runtimePersistence;
  }

  @Order(10)
  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    // 先恢复全部非终态 WorkflowExecution，再由 Schedule Reconciler 恢复 Trigger Ledger。
    for (String executionId : runtimePersistence.findRecoverableExecutionIds()) {
      try {
        runtimeService.activate(executionId);
      } catch (RuntimeException exception) {
        log.error(
            "[workflow] pre-recovery activation failed execution={}, message={}",
            executionId,
            exception.getMessage(),
            exception);
      }
    }

    int recovered = runtimeService.recoverPersistedExecutions();
    if (recovered > 0) {
      log.info("[workflow] startup recovery completed executions={}", recovered);
    }
  }
}
