package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 工作流定义上下线与调度生命周期之间的联动。 */
@Component
public class WorkflowDefinitionScheduleGuard {
  private final ObjectProvider<WorkflowScheduleDao> scheduleDaoProvider;
  private final ObjectProvider<WorkflowScheduleLifecycle> scheduleLifecycleProvider;

  public WorkflowDefinitionScheduleGuard(
      ObjectProvider<WorkflowScheduleDao> scheduleDaoProvider,
      ObjectProvider<WorkflowScheduleLifecycle> scheduleLifecycleProvider) {
    this.scheduleDaoProvider = scheduleDaoProvider;
    this.scheduleLifecycleProvider = scheduleLifecycleProvider;
  }

  /**
   * 工作流上线后自动启用已经保存的有效调度。
   * database-disabled 的 focused development/test 环境没有调度组件，此时保持轻量行为。
   */
  public void activateConfiguredSchedules(String workflowId) {
    String id = requireWorkflowId(workflowId);
    WorkflowScheduleDao scheduleDao = scheduleDaoProvider.getIfAvailable();
    WorkflowScheduleLifecycle lifecycle = scheduleLifecycleProvider.getIfAvailable();
    if (scheduleDao == null || lifecycle == null) return;

    Instant now = Instant.now();
    scheduleDao.selectSchedules(id, "OFFLINE").stream()
        .filter(schedule -> schedule.getEndTime() == null || schedule.getEndTime().isAfter(now))
        .forEach(schedule -> lifecycle.online(schedule.getId()));
  }

  /** 工作流下线前自动停用所有在线调度，避免继续产生新的计划实例。 */
  public void deactivateConfiguredSchedules(String workflowId) {
    String id = requireWorkflowId(workflowId);
    WorkflowScheduleDao scheduleDao = scheduleDaoProvider.getIfAvailable();
    WorkflowScheduleLifecycle lifecycle = scheduleLifecycleProvider.getIfAvailable();
    if (scheduleDao == null || lifecycle == null) return;

    scheduleDao.selectSchedules(id, "ONLINE")
        .forEach(schedule -> lifecycle.offline(schedule.getId()));
  }

  private String requireWorkflowId(String workflowId) {
    if (workflowId == null || workflowId.isBlank()) {
      throw new IllegalArgumentException("工作流 ID 不能为空");
    }
    return workflowId.trim();
  }
}
