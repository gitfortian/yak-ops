package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Rebuilds non-terminal workflow runtime state after Yak Ops has fully started. */
@Component
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowRuntimeRecovery {
  private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeRecovery.class);

  private final WorkflowRuntime runtimeService;
  private final WorkflowRuntimeRepository runtimePersistence;

  public WorkflowRuntimeRecovery(
      WorkflowRuntime runtimeService,
      WorkflowRuntimeRepository runtimePersistence) {
    this.runtimeService = runtimeService;
    this.runtimePersistence = runtimePersistence;
  }

  @Order(10)
  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    // Register executions as active before reconciliation. This does not execute a node by itself;
    // it only guarantees that a recovered SUBMITTED dispatch drains immediately when reconstructed.
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
