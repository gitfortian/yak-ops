package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
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

/** 基于 MyBatis-Plus 的工作流调度定义 DAO。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowScheduleDaoImpl implements WorkflowScheduleDao {
  private final WorkflowScheduleMapper mapper;
  private final WorkflowDefinitionMapper definitionMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowScheduleDaoImpl(
      WorkflowScheduleMapper mapper,
      WorkflowDefinitionMapper definitionMapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.definitionMapper = definitionMapper;
    this.currentProject = currentProject;
  }

  public WorkflowScheduleDaoImpl(WorkflowScheduleMapper mapper) {
    this(mapper, null, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public List<WorkflowSchedulePO> selectSchedules(String workflowId, String status) {
    Long projectId = currentProjectId();
    var query = Wrappers.<WorkflowSchedulePO>lambdaQuery()
        .eq(projectId != null, WorkflowSchedulePO::getProjectId, projectId);
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowSchedulePO::getWorkflowId, workflowId.trim());
    }
    if (status != null && !status.isBlank()) {
      query.eq(WorkflowSchedulePO::getStatus, status.trim().toUpperCase());
    }
    return mapper.selectList(query.orderByDesc(WorkflowSchedulePO::getUpdateTime));
  }

  @Override
  public WorkflowSchedulePO selectSchedule(String id) {
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowSchedulePO>lambdaQuery()
            .eq(WorkflowSchedulePO::getId, id)
            .eq(projectId != null, WorkflowSchedulePO::getProjectId, projectId));
  }

  @Override
  public int insertSchedule(WorkflowSchedulePO schedule) {
    schedule.setProjectId(resolveProjectId(schedule.getProjectId(), schedule.getWorkflowId()));
    return mapper.insert(schedule);
  }

  @Override
  public int updateSchedule(WorkflowSchedulePO schedule) {
    Long projectId = currentProjectId();
    if (projectId == null) return mapper.updateById(schedule);
    schedule.setProjectId(resolveProjectId(schedule.getProjectId(), schedule.getWorkflowId()));
    return mapper.update(
        schedule,
        Wrappers.<WorkflowSchedulePO>lambdaUpdate()
            .eq(WorkflowSchedulePO::getId, schedule.getId())
            .eq(WorkflowSchedulePO::getProjectId, projectId));
  }

  @Override
  public int updateRuntimeState(String id, Instant lastFireTime, Instant nextFireTime) {
    Long projectId = currentProjectId();
    return mapper.update(
        null,
        Wrappers.<WorkflowSchedulePO>lambdaUpdate()
            .eq(WorkflowSchedulePO::getId, id)
            .eq(projectId != null, WorkflowSchedulePO::getProjectId, projectId)
            .set(WorkflowSchedulePO::getLastFireTime, lastFireTime)
            .set(WorkflowSchedulePO::getNextFireTime, nextFireTime));
  }

  @Override
  public int deleteSchedule(String id) {
    Long projectId = currentProjectId();
    return mapper.delete(
        Wrappers.<WorkflowSchedulePO>lambdaQuery()
            .eq(WorkflowSchedulePO::getId, id)
            .eq(projectId != null, WorkflowSchedulePO::getProjectId, projectId));
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private Long resolveProjectId(Long storedProjectId, String workflowId) {
    Long projectId = currentProjectId();
    if (projectId != null) {
      if (storedProjectId != null && !Objects.equals(storedProjectId, projectId)) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      if (definitionMapper != null && !workflowOwned(workflowId, projectId)) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      return projectId;
    }
    if (storedProjectId != null || definitionMapper == null) return storedProjectId;
    WorkflowDefinitionPO definition = definitionMapper.selectById(workflowId);
    return definition == null ? null : definition.getProjectId();
  }

  private boolean workflowOwned(String workflowId, Long projectId) {
    return definitionMapper.selectCount(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId)) > 0L;
  }
}
