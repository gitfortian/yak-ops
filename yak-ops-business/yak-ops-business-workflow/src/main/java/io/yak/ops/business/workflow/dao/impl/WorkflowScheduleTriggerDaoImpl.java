package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowBackfillMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleTriggerMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的工作流调度 Trigger Ledger DAO。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowScheduleTriggerDaoImpl implements WorkflowScheduleTriggerDao {
  private static final List<String> PENDING =
      List.of("RECEIVED", "WAITING", "LAUNCHING", "REACTIVATING", "RUNNING");

  private final WorkflowScheduleTriggerMapper mapper;
  private final WorkflowScheduleMapper scheduleMapper;
  private final WorkflowDefinitionMapper definitionMapper;
  private final WorkflowExecutionMapper executionMapper;
  private final WorkflowBackfillMapper backfillMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowScheduleTriggerDaoImpl(
      WorkflowScheduleTriggerMapper mapper,
      WorkflowScheduleMapper scheduleMapper,
      WorkflowDefinitionMapper definitionMapper,
      WorkflowExecutionMapper executionMapper,
      WorkflowBackfillMapper backfillMapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.scheduleMapper = scheduleMapper;
    this.definitionMapper = definitionMapper;
    this.executionMapper = executionMapper;
    this.backfillMapper = backfillMapper;
    this.currentProject = currentProject;
  }

  public WorkflowScheduleTriggerDaoImpl(WorkflowScheduleTriggerMapper mapper) {
    this(
        mapper,
        null,
        null,
        null,
        null,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public WorkflowScheduleTriggerPO claim(WorkflowScheduleTriggerPO trigger) {
    bindProject(trigger);
    if (trigger.getBackfillId() == null || trigger.getBackfillId().isBlank()) {
      WorkflowScheduleTriggerPO legacy = selectBySchedulePlan(
          trigger.getScheduleId(), trigger.getPlannedFireTime());
      if (legacy != null) return legacy;
    }
    mapper.insertIgnore(trigger);
    WorkflowScheduleTriggerPO stored = selectByDedupeKey(trigger.getDedupeKey());
    if (stored == null) {
      throw new IllegalStateException("Trigger Ledger 幂等记录保存失败：" + trigger.getDedupeKey());
    }
    return stored;
  }

  @Override
  public WorkflowScheduleTriggerPO selectByDedupeKey(String dedupeKey) {
    if (dedupeKey == null || dedupeKey.isBlank()) return null;
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getDedupeKey, dedupeKey.trim()));
  }

  @Override
  public WorkflowScheduleTriggerPO selectBySchedulePlan(
      String scheduleId, Instant plannedFireTime) {
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getScheduleId, scheduleId)
            .eq(WorkflowScheduleTriggerPO::getPlannedFireTime, plannedFireTime)
            .isNull(WorkflowScheduleTriggerPO::getBackfillId)
            .last("LIMIT 1"));
  }

  @Override
  public WorkflowScheduleTriggerPO selectByExecutionId(String executionId) {
    if (executionId == null || executionId.isBlank()) return null;
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getWorkflowExecutionId, executionId)
            .last("LIMIT 1"));
  }

  @Override
  public WorkflowScheduleTriggerPO selectNextWaiting(String workflowId) {
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getWorkflowId, workflowId)
            .eq(WorkflowScheduleTriggerPO::getStatus, "WAITING")
            .orderByAsc(WorkflowScheduleTriggerPO::getPlannedFireTime)
            .orderByAsc(WorkflowScheduleTriggerPO::getCreateTime)
            .last("LIMIT 1"));
  }

  @Override
  public List<WorkflowScheduleTriggerPO> selectPending() {
    Long projectId = currentProjectId();
    return mapper.selectList(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .in(WorkflowScheduleTriggerPO::getStatus, PENDING)
            .orderByAsc(WorkflowScheduleTriggerPO::getPlannedFireTime));
  }

  @Override
  public List<WorkflowScheduleTriggerPO> selectQueuedBySchedule(String scheduleId) {
    Long projectId = currentProjectId();
    return mapper.selectList(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getScheduleId, scheduleId)
            .isNull(WorkflowScheduleTriggerPO::getBackfillId)
            .in(WorkflowScheduleTriggerPO::getStatus, List.of("RECEIVED", "WAITING"))
            .orderByAsc(WorkflowScheduleTriggerPO::getPlannedFireTime));
  }

  @Override
  public List<WorkflowScheduleTriggerPO> selectByBackfillId(String backfillId) {
    if (backfillId == null || backfillId.isBlank()) return List.of();
    Long projectId = currentProjectId();
    return mapper.selectList(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getBackfillId, backfillId.trim())
            .orderByAsc(WorkflowScheduleTriggerPO::getPlannedFireTime));
  }

  @Override
  public List<WorkflowScheduleTriggerPO> selectTriggers(
      String scheduleId,
      String workflowId,
      String backfillId,
      String status,
      int limit) {
    Long projectId = currentProjectId();
    var query = Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
        .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId);
    if (scheduleId != null && !scheduleId.isBlank()) {
      query.eq(WorkflowScheduleTriggerPO::getScheduleId, scheduleId.trim());
    }
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowScheduleTriggerPO::getWorkflowId, workflowId.trim());
    }
    if (backfillId != null && !backfillId.isBlank()) {
      query.eq(WorkflowScheduleTriggerPO::getBackfillId, backfillId.trim());
    }
    if (status != null && !status.isBlank()) {
      query.eq(WorkflowScheduleTriggerPO::getStatus, status.trim().toUpperCase());
    }
    int safeLimit = Math.max(1, Math.min(limit, 1000));
    return mapper.selectList(
        query.orderByDesc(WorkflowScheduleTriggerPO::getPlannedFireTime)
            .last("LIMIT " + safeLimit));
  }

  @Override
  public int update(WorkflowScheduleTriggerPO trigger) {
    Long projectId = currentProjectId();
    if (projectId == null) return mapper.updateById(trigger);
    bindProject(trigger);
    return mapper.update(
        trigger,
        Wrappers.<WorkflowScheduleTriggerPO>lambdaUpdate()
            .eq(WorkflowScheduleTriggerPO::getId, trigger.getId())
            .eq(WorkflowScheduleTriggerPO::getProjectId, projectId));
  }

  @Override
  public int bindPreparedExecution(
      String triggerId, String executionId, String executionStatus) {
    Long projectId = currentProjectId();
    return mapper.update(
        null,
        Wrappers.<WorkflowScheduleTriggerPO>lambdaUpdate()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getTriggerId, triggerId)
            .eq(WorkflowScheduleTriggerPO::getStatus, "LAUNCHING")
            .isNull(WorkflowScheduleTriggerPO::getWorkflowExecutionId)
            .set(WorkflowScheduleTriggerPO::getWorkflowExecutionId, executionId)
            .set(WorkflowScheduleTriggerPO::getExecutionStatus, executionStatus)
            .set(WorkflowScheduleTriggerPO::getMessage, "WorkflowExecution 已首次持久化")
            .set(WorkflowScheduleTriggerPO::getUpdateTime, Instant.now()));
  }

  @Override
  public void lockWorkflow(String workflowId) {
    requireWorkflow(workflowId);
    String locked = mapper.lockWorkflow(workflowId);
    if (locked == null) throw new IllegalArgumentException("工作流不存在：" + workflowId);
  }

  @Override
  public long countActiveExecutions(String workflowId) {
    requireWorkflow(workflowId);
    return mapper.countActiveExecutions(workflowId);
  }

  @Override
  public long countLaunchingTriggers(String workflowId) {
    Long projectId = currentProjectId();
    return mapper.selectCount(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getWorkflowId, workflowId)
            .in(WorkflowScheduleTriggerPO::getStatus, List.of("LAUNCHING", "REACTIVATING")));
  }

  @Override
  public long countWaitingTriggers(String workflowId) {
    Long projectId = currentProjectId();
    return mapper.selectCount(
        Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
            .eq(projectId != null, WorkflowScheduleTriggerPO::getProjectId, projectId)
            .eq(WorkflowScheduleTriggerPO::getWorkflowId, workflowId)
            .eq(WorkflowScheduleTriggerPO::getStatus, "WAITING"));
  }

  @Override
  public String selectWorkflowIdByExecution(String executionId) {
    Long projectId = currentProjectId();
    if (projectId != null && executionMapper != null) {
      WorkflowExecutionPO execution = executionMapper.selectOne(
          Wrappers.<WorkflowExecutionPO>lambdaQuery()
              .eq(WorkflowExecutionPO::getId, executionId)
              .eq(WorkflowExecutionPO::getProjectId, projectId));
      if (execution == null) return null;
    }
    return mapper.selectWorkflowIdByExecution(executionId);
  }

  @Override
  public String selectExecutionIdByTrigger(String triggerId) {
    Long projectId = currentProjectId();
    if (projectId != null) {
      WorkflowScheduleTriggerPO trigger = mapper.selectOne(
          Wrappers.<WorkflowScheduleTriggerPO>lambdaQuery()
              .eq(WorkflowScheduleTriggerPO::getProjectId, projectId)
              .eq(WorkflowScheduleTriggerPO::getTriggerId, triggerId)
              .last("LIMIT 1"));
      if (trigger == null) return null;
      if (trigger.getWorkflowExecutionId() != null) return trigger.getWorkflowExecutionId();
    }
    return mapper.selectExecutionIdByTrigger(triggerId);
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private void bindProject(WorkflowScheduleTriggerPO trigger) {
    Long projectId = currentProjectId();
    if (projectId != null) {
      if (trigger.getProjectId() != null
          && !Objects.equals(trigger.getProjectId(), projectId)) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      requireWorkflow(trigger.getWorkflowId());
      trigger.setProjectId(projectId);
      return;
    }
    if (trigger.getProjectId() != null) return;
    if (scheduleMapper != null && trigger.getScheduleId() != null) {
      WorkflowSchedulePO schedule = scheduleMapper.selectById(trigger.getScheduleId());
      if (schedule != null && schedule.getProjectId() != null) {
        trigger.setProjectId(schedule.getProjectId());
        return;
      }
    }
    if (backfillMapper != null && trigger.getBackfillId() != null) {
      WorkflowBackfillPO backfill = backfillMapper.selectById(trigger.getBackfillId());
      if (backfill != null && backfill.getProjectId() != null) {
        trigger.setProjectId(backfill.getProjectId());
        return;
      }
    }
    if (definitionMapper != null && trigger.getWorkflowId() != null) {
      WorkflowDefinitionPO definition = definitionMapper.selectById(trigger.getWorkflowId());
      if (definition != null) trigger.setProjectId(definition.getProjectId());
    }
  }

  private void requireWorkflow(String workflowId) {
    Long projectId = currentProjectId();
    if (projectId == null || definitionMapper == null) return;
    long count = definitionMapper.selectCount(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId));
    if (count == 0L) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }
}
