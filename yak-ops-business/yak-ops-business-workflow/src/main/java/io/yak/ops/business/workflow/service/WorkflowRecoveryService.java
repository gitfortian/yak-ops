package io.yak.ops.business.workflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

  public WorkflowRecoveryService(WorkflowRuntimeService runtimeService) {
    this.runtimeService = runtimeService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    int recovered = runtimeService.recoverPersistedExecutions();
    if (recovered > 0) {
      log.info("[workflow] startup recovery completed executions={}", recovered);
    }
  }
}
