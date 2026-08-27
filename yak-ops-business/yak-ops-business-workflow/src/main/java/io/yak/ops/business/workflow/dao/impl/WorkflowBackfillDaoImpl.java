package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowBackfillMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的 Backfill 批次 DAO。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowBackfillDaoImpl implements WorkflowBackfillDao {
  private final WorkflowBackfillMapper mapper;
  private final WorkflowDefinitionMapper definitionMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowBackfillDaoImpl(
      WorkflowBackfillMapper mapper,
      WorkflowDefinitionMapper definitionMapper,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.definitionMapper = definitionMapper;
    this.currentProject = currentProject;
  }

  public WorkflowBackfillDaoImpl(WorkflowBackfillMapper mapper) {
    this(mapper, null, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public int insert(WorkflowBackfillPO backfill) {
    backfill.setProjectId(resolveProjectId(backfill.getProjectId(), backfill.getWorkflowId()));
    return mapper.insert(backfill);
  }

  @Override
  public int update(WorkflowBackfillPO backfill) {
    Long projectId = currentProjectId();
    if (projectId == null) return mapper.updateById(backfill);
    backfill.setProjectId(resolveProjectId(backfill.getProjectId(), backfill.getWorkflowId()));
    return mapper.update(
        backfill,
        Wrappers.<WorkflowBackfillPO>lambdaUpdate()
            .eq(WorkflowBackfillPO::getId, backfill.getId())
            .eq(WorkflowBackfillPO::getProjectId, projectId));
  }

  @Override
  public WorkflowBackfillPO select(String id) {
    Long projectId = currentProjectId();
    return mapper.selectOne(
        Wrappers.<WorkflowBackfillPO>lambdaQuery()
            .eq(WorkflowBackfillPO::getId, id)
            .eq(projectId != null, WorkflowBackfillPO::getProjectId, projectId));
  }

  @Override
  public List<WorkflowBackfillPO> selectList(String workflowId, String scheduleId) {
    Long projectId = currentProjectId();
    var query = Wrappers.<WorkflowBackfillPO>lambdaQuery()
        .eq(projectId != null, WorkflowBackfillPO::getProjectId, projectId);
    if (workflowId != null && !workflowId.isBlank()) {
      query.eq(WorkflowBackfillPO::getWorkflowId, workflowId.trim());
    }
    if (scheduleId != null && !scheduleId.isBlank()) {
      query.eq(WorkflowBackfillPO::getScheduleId, scheduleId.trim());
    }
    return mapper.selectList(query.orderByDesc(WorkflowBackfillPO::getCreateTime));
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
