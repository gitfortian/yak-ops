package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动后以 yak_workflow_schedule 为事实来源，对 Yak Schedule/Quartz 与 Trigger Ledger 做对账恢复。
 *
 * <p>即便 Quartz 使用 memory store，ONLINE 计划、错过触发和串行等待队列都可从业务数据库恢复。</p>
 */
@Component
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowScheduleReconciler {
  private static final Logger log = LoggerFactory.getLogger(WorkflowScheduleReconciler.class);

  private final WorkflowScheduleDao dao;
  private final WorkflowDefinitionService definitions;
  private final WorkflowScheduleEngineBridge engine;
  private final WorkflowScheduleRuntimeState runtimeState;
  private final WorkflowScheduleMisfireRecovery misfireRecovery;
  private final WorkflowScheduleTriggerCoordinator coordinator;

  public WorkflowScheduleReconciler(
      WorkflowScheduleDao dao,
      WorkflowDefinitionService definitions,
      WorkflowScheduleEngineBridge engine,
      WorkflowScheduleRuntimeState runtimeState,
      WorkflowScheduleMisfireRecovery misfireRecovery,
      WorkflowScheduleTriggerCoordinator coordinator) {
    this.dao = dao;
    this.definitions = definitions;
    this.engine = engine;
    this.runtimeState = runtimeState;
    this.misfireRecovery = misfireRecovery;
    this.coordinator = coordinator;
  }

  @Order(20)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    if (!engine.available()) {
      log.warn("[workflow-schedule] Yak ScheduleManager unavailable, skip startup reconcile");
      return;
    }

    List<WorkflowSchedulePO> schedules = dao.selectSchedules(null, null);
    Map<String, WorkflowSchedulePO> byId = new HashMap<>();
    schedules.forEach(item -> byId.put(item.getId(), item));

    engine.list().forEach(snapshot -> {
      String scheduleId = snapshot.definition().key().name();
      WorkflowSchedulePO local = byId.get(scheduleId);
      if (local == null || !"ONLINE".equals(local.getStatus())) {
        try {
          engine.deleteIfPresent(scheduleId);
        } catch (RuntimeException exception) {
          log.warn(
              "[workflow-schedule] stale engine schedule cleanup failed schedule={}, message={}",
              scheduleId,
              exception.getMessage());
        }
      }
    });

    Instant now = Instant.now();
    for (WorkflowSchedulePO schedule : schedules) {
      if (!"ONLINE".equals(schedule.getStatus())) continue;
      try {
        var workflow = definitions.get(schedule.getWorkflowId());
        boolean workflowReady = "ONLINE".equals(workflow.status()) && workflow.activeVersionId() != null;
        boolean expired = schedule.getEndTime() != null && !schedule.getEndTime().isAfter(now);
        if (!workflowReady || expired) {
          markOffline(schedule, now);
          engine.deleteIfPresent(schedule.getId());
          continue;
        }

        Instant persistedNextFireTime = schedule.getNextFireTime();
        Instant missedFireTime = persistedNextFireTime != null && !persistedNextFireTime.isAfter(now)
            ? persistedNextFireTime
            : null;

        var snapshot = engine.save(schedule);
        runtimeState.syncSnapshot(schedule, snapshot);

        if (missedFireTime != null) {
          misfireRecovery.recover(schedule, missedFireTime, now);
          log.info(
              "[workflow-schedule] recovered misfire schedule={}, planned={}, policy={}",
              schedule.getId(), missedFireTime, schedule.getMisfireStrategy());
        }

        log.info(
            "[workflow-schedule] reconciled schedule={}, workflow={}, nextFireTime={}",
            schedule.getId(),
            schedule.getWorkflowId(),
            snapshot.nextFireTime());
      } catch (RuntimeException exception) {
        runtimeState.clearNext(schedule.getId());
        log.error(
            "[workflow-schedule] reconcile failed schedule={}, workflow={}, message={}",
            schedule.getId(),
            schedule.getWorkflowId(),
            exception.getMessage(),
            exception);
      }
    }

    try {
      coordinator.recoverPending();
    } catch (RuntimeException exception) {
      log.error(
          "[workflow-schedule] trigger ledger recovery failed message={}",
          exception.getMessage(),
          exception);
    }
  }

  private void markOffline(WorkflowSchedulePO schedule, Instant now) {
    schedule.setStatus("OFFLINE");
    schedule.setNextFireTime(null);
    schedule.setUpdateTime(now);
    if (dao.updateSchedule(schedule) != 1) {
      throw new IllegalStateException("启动对账时停用工作流调度失败：" + schedule.getId());
    }
  }
}
