package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动期恢复 Backfill 批次物化。
 *
 * <p>批次定义先持久化，Trigger 再逐条通过 Ledger 提交；如果进程中途退出，
 * 这里重新按批次快照生成全部 occurrence。已存在 Trigger 由 dedupeKey 幂等拦截，缺失项自动补齐。</p>
 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowBackfillReconciler {
  private static final Logger log = LoggerFactory.getLogger(WorkflowBackfillReconciler.class);

  private final WorkflowBackfillDao dao;
  private final WorkflowBackfillPlanner planner;
  private final WorkflowScheduleTriggerCoordinator coordinator;

  public WorkflowBackfillReconciler(
      WorkflowBackfillDao dao,
      WorkflowBackfillPlanner planner,
      WorkflowScheduleTriggerCoordinator coordinator) {
    this.dao = dao;
    this.planner = planner;
    this.coordinator = coordinator;
  }

  @Order(15)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    List<WorkflowBackfillPO> batches = dao.selectList(null, null).stream()
        .filter(value -> "RUNNING".equals(value.getStatus()))
        .toList();
    for (WorkflowBackfillPO backfill : batches) {
      try {
        var plan = planner.plan(
            backfill.getCronExpression(),
            backfill.getTimezone(),
            backfill.getStartBusinessDate(),
            backfill.getEndBusinessDate());
        for (var occurrence : plan.occurrences()) {
          try {
            coordinator.submitBackfill(backfill, occurrence);
          } catch (RuntimeException exception) {
            log.warn(
                "[workflow-backfill] reconcile occurrence failed backfill={}, businessDate={}, message={}",
                backfill.getId(), occurrence.businessDate(), exception.getMessage());
          }
        }
        log.info(
            "[workflow-backfill] reconciled batch={}, occurrences={}",
            backfill.getId(), plan.occurrences().size());
      } catch (RuntimeException exception) {
        log.error(
            "[workflow-backfill] reconcile batch failed backfill={}, message={}",
            backfill.getId(), exception.getMessage(), exception);
      }
    }
  }
}
