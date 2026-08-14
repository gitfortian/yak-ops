package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 调度停用时清理尚未创建 WorkflowExecution 的 Trigger。 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowSchedulePendingTriggerCancellation {
  private static final Set<String> QUEUED = Set.of("RECEIVED", "WAITING");

  private final WorkflowScheduleTriggerDao ledger;

  public WorkflowSchedulePendingTriggerCancellation(WorkflowScheduleTriggerDao ledger) {
    this.ledger = ledger;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void cancel(String scheduleId, String workflowId, String reason) {
    ledger.lockWorkflow(workflowId);
    Instant now = Instant.now();
    for (WorkflowScheduleTriggerPO trigger : ledger.selectTriggers(scheduleId, workflowId, null, 500)) {
      if (!QUEUED.contains(trigger.getStatus())) continue;
      trigger.setStatus("SKIPPED");
      trigger.setMessage(reason == null || reason.isBlank() ? "调度已停用，取消等待 Trigger" : reason);
      trigger.setCompletedAt(now);
      trigger.setUpdateTime(now);
      if (ledger.update(trigger) != 1) {
        throw new IllegalStateException("取消等待 Trigger 失败：" + trigger.getId());
      }
    }
  }
}
