package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 工作流定义生命周期与调度生命周期之间的约束。 */
@Component
public class WorkflowDefinitionScheduleGuard {
  private final ObjectProvider<WorkflowScheduleDao> scheduleDaoProvider;

  public WorkflowDefinitionScheduleGuard(ObjectProvider<WorkflowScheduleDao> scheduleDaoProvider) {
    this.scheduleDaoProvider = scheduleDaoProvider;
  }

  /**
   * 工作流下线前必须先停用所有在线调度。
   * database-disabled 的 focused development/test 环境没有调度 DAO，此时保持原有轻量行为。
   */
  public void ensureCanOffline(String workflowId) {
    if (workflowId == null || workflowId.isBlank()) {
      throw new IllegalArgumentException("工作流 ID 不能为空");
    }

    WorkflowScheduleDao scheduleDao = scheduleDaoProvider.getIfAvailable();
    if (scheduleDao == null) return;

    if (!scheduleDao.selectSchedules(workflowId.trim(), "ONLINE").isEmpty()) {
      throw new IllegalStateException("工作流存在已启用调度，请先停用调度后再下线工作流");
    }
  }
}
