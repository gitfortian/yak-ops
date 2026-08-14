package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleTriggerMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的工作流调度 Trigger Ledger DAO。 */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowScheduleTriggerDaoImpl implements WorkflowScheduleTriggerDao {
  private static final List<String> PENDING = List.of("RECEIVED", "WAITING", "LAUNCHING", "RUNNING");

  private final WorkflowScheduleTriggerMapper mapper;

  @Override
  public WorkflowScheduleTriggerPO claim(WorkflowScheduleTriggerPO trigger) {
    mapper.insertIgnore(trigger);
    WorkflowScheduleTriggerPO stored = selectBySchedulePlan(
        trigger.getScheduleId(), trigger.getPlannedFireTime());
    if (stored == null) {
      throw new IllegalStateException(
          "Trigger Ledger 幂等记录保存失败：" + trigger.getScheduleId() + "@" + trigger.getPlannedFireTime());
    }
    return stored;
  }

  @Override
  public WorkflowScheduleTriggerPO selectBySchedulePlan(String scheduleId, Instant plannedFireTime) {
    return mapper.selectOne(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(WorkflowScheduleTriggerPO::getScheduleId, scheduleId)
            .eq(WorkflowScheduleTriggerPO::getPlannedFireTime, plannedFireTime));
  }

  @Override
  public WorkflowScheduleTriggerPO selectByExecutionId(String executionId) {
    if (executionId == null || executionId.isBlank()) return null;
    return mapper.selectOne(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(WorkflowScheduleTriggerPO::getWorkflowExecutionId, executionId)
            .last("LIMIT 1"));
  }

  @Override
  public WorkflowScheduleTriggerPO selectNextWaiting(String workflowId) {
    return mapper.selectOne(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(WorkflowScheduleTriggerPO::getWorkflowId, workflowId)
            .eq(WorkflowScheduleTriggerPO::getStatus, "WAITING")
            .orderByAsc(WorkflowScheduleTriggerPO::getPlannedFireTime)
            .orderByAsc(WorkflowScheduleTriggerPO::getCreateTime)
            .last("LIMIT 1"));
  }

  @Override
  public List<WorkflowScheduleTriggerPO> selectPending() {
    return mapper.selectList(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .in(WorkflowScheduleTriggerPO::getStatus, PENDING)
            .orderByAsc(WorkflowScheduleTriggerPO::getPlannedFireTime));
  }

  @Override
  public List<WorkflowScheduleTriggerPO> selectTriggers(
      String scheduleId, String workflowId, String status, int limit) {
    var query = Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery();
    if (scheduleId != null && !scheduleId.isBlank()) {
      query.eq(WorkflowScheduleTriggerPO::getScheduleId, scheduleId.trim());
    }
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowScheduleTriggerPO::getWorkflowId, workflowId.trim());
    }
    if (status != null && !status.isBlank()) {
      query.eq(WorkflowScheduleTriggerPO::getStatus, status.trim().toUpperCase());
    }
    int safeLimit = Math.max(1, Math.min(limit, 500));
    return mapper.selectList(
        query.orderByDesc(WorkflowScheduleTriggerPO::getPlannedFireTime)
            .last("LIMIT " + safeLimit));
  }

  @Override
  public int update(WorkflowScheduleTriggerPO trigger) {
    return mapper.updateById(trigger);
  }

  @Override
  public void lockWorkflow(String workflowId) {
    String locked = mapper.lockWorkflow(workflowId);
    if (locked == null) {
      throw new IllegalArgumentException("工作流不存在：" + workflowId);
    }
  }

  @Override
  public long countActiveExecutions(String workflowId) {
    return mapper.countActiveExecutions(workflowId);
  }

  @Override
  public long countLaunchingTriggers(String workflowId) {
    return mapper.countLaunchingTriggers(workflowId);
  }

  @Override
  public String selectWorkflowIdByExecution(String executionId) {
    return mapper.selectWorkflowIdByExecution(executionId);
  }

  @Override
  public String selectExecutionIdByTrigger(String triggerId) {
    return mapper.selectExecutionIdByTrigger(triggerId);
  }
}
