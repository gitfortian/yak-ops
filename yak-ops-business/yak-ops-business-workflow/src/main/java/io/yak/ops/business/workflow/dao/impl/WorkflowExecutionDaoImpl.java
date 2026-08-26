package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeAttemptMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowNodeExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowVersionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的工作流执行 DAO。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowExecutionDaoImpl implements WorkflowExecutionDao {
  private static final List<String> ACTIVE_STATUSES =
      List.of("CREATED", "RUNNING", "PAUSING", "PAUSED", "RESUMING");

  private final WorkflowExecutionMapper executionMapper;
  private final WorkflowNodeExecutionMapper nodeExecutionMapper;
  private final WorkflowNodeAttemptMapper nodeAttemptMapper;
  private final WorkflowDefinitionMapper definitionMapper;
  private final WorkflowVersionMapper versionMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowExecutionDaoImpl(
      WorkflowExecutionMapper executionMapper,
      WorkflowNodeExecutionMapper nodeExecutionMapper,
      WorkflowNodeAttemptMapper nodeAttemptMapper,
      WorkflowDefinitionMapper definitionMapper,
      WorkflowVersionMapper versionMapper,
      CurrentProject currentProject) {
    this.executionMapper = executionMapper;
    this.nodeExecutionMapper = nodeExecutionMapper;
    this.nodeAttemptMapper = nodeAttemptMapper;
    this.definitionMapper = definitionMapper;
    this.versionMapper = versionMapper;
    this.currentProject = currentProject;
  }

  public WorkflowExecutionDaoImpl(
      WorkflowExecutionMapper executionMapper,
      WorkflowNodeExecutionMapper nodeExecutionMapper,
      WorkflowNodeAttemptMapper nodeAttemptMapper) {
    this(
        executionMapper,
        nodeExecutionMapper,
        nodeAttemptMapper,
        null,
        null,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public WorkflowExecutionPO selectExecution(String executionId) {
    Long projectId = currentProjectId();
    return executionMapper.selectOne(
        Wrappers.<WorkflowExecutionPO>lambdaQuery()
            .eq(WorkflowExecutionPO::getId, executionId)
            .eq(projectId != null, WorkflowExecutionPO::getProjectId, projectId));
  }

  @Override
  public List<WorkflowNodeExecutionPO> selectNodeExecutions(String executionId) {
    if (!executionAccessible(executionId)) return List.of();
    return nodeExecutionMapper.selectList(
        Wrappers.<WorkflowNodeExecutionPO>lambdaQuery()
            .eq(WorkflowNodeExecutionPO::getWorkflowExecutionId, executionId)
            .orderByAsc(WorkflowNodeExecutionPO::getId));
  }

  @Override
  public List<WorkflowNodeAttemptPO> selectNodeAttempts(String executionId) {
    if (!executionAccessible(executionId)) return List.of();
    return nodeAttemptMapper.selectList(
        Wrappers.<WorkflowNodeAttemptPO>lambdaQuery()
            .eq(WorkflowNodeAttemptPO::getWorkflowExecutionId, executionId)
            .orderByAsc(WorkflowNodeAttemptPO::getNodeExecutionId)
            .orderByAsc(WorkflowNodeAttemptPO::getAttemptNo));
  }

  @Override
  public int upsertExecution(WorkflowExecutionPO execution) {
    execution.setProjectId(resolveProjectId(execution));
    return executionMapper.upsert(execution);
  }

  @Override
  public int upsertNodeExecution(WorkflowNodeExecutionPO nodeExecution) {
    requireExecution(nodeExecution.getWorkflowExecutionId());
    return nodeExecutionMapper.upsert(nodeExecution);
  }

  @Override
  public int upsertNodeAttempt(WorkflowNodeAttemptPO attempt) {
    requireExecution(attempt.getWorkflowExecutionId());
    return nodeAttemptMapper.upsert(attempt);
  }

  @Override
  public int updateExecution(WorkflowExecutionPO execution) {
    Long projectId = currentProjectId();
    if (projectId == null) return executionMapper.updateById(execution);
    execution.setProjectId(projectId);
    return executionMapper.update(
        execution,
        Wrappers.<WorkflowExecutionPO>lambdaUpdate()
            .eq(WorkflowExecutionPO::getId, execution.getId())
            .eq(WorkflowExecutionPO::getProjectId, projectId));
  }

  @Override
  public List<String> selectExecutionIds() {
    Long projectId = currentProjectId();
    if (projectId == null) return executionMapper.selectExecutionIds();
    return executionMapper.selectObjs(
            Wrappers.<WorkflowExecutionPO>lambdaQuery()
                .select(WorkflowExecutionPO::getId)
                .eq(WorkflowExecutionPO::getProjectId, projectId)
                .orderByDesc(WorkflowExecutionPO::getCreatedAt))
        .stream()
        .map(String::valueOf)
        .toList();
  }

  @Override
  public List<String> selectRecoverableExecutionIds() {
    Long projectId = currentProjectId();
    if (projectId == null) return executionMapper.selectRecoverableExecutionIds();
    return executionMapper.selectObjs(
            Wrappers.<WorkflowExecutionPO>lambdaQuery()
                .select(WorkflowExecutionPO::getId)
                .eq(WorkflowExecutionPO::getProjectId, projectId)
                .in(WorkflowExecutionPO::getStatus, ACTIVE_STATUSES)
                .orderByAsc(WorkflowExecutionPO::getCreatedAt))
        .stream()
        .map(String::valueOf)
        .toList();
  }

  @Override
  public long countActiveExecutions(String workflowId) {
    Long projectId = currentProjectId();
    if (projectId != null && !workflowAccessible(workflowId, projectId)) return 0L;
    return executionMapper.countActiveExecutions(workflowId);
  }

  @Override
  public String selectEffectiveRuntimeMetadata(String executionId) {
    if (!executionAccessible(executionId)) return null;
    return executionMapper.selectEffectiveRuntimeMetadata(executionId);
  }

  @Override
  public WorkflowNodeAttemptPO selectAttempt(String attemptId) {
    WorkflowNodeAttemptPO attempt = nodeAttemptMapper.selectById(attemptId);
    if (attempt == null || !executionAccessible(attempt.getWorkflowExecutionId())) return null;
    return attempt;
  }

  @Override
  public int bindExternalExecution(String attemptId, String externalExecutionId) {
    if (selectAttempt(attemptId) == null) return 0;
    return nodeAttemptMapper.bindExternalExecution(attemptId, externalExecutionId);
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private boolean executionAccessible(String executionId) {
    return currentProjectId() == null || selectExecution(executionId) != null;
  }

  private void requireExecution(String executionId) {
    if (!executionAccessible(executionId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  private Long resolveProjectId(WorkflowExecutionPO execution) {
    Long projectId = currentProjectId();
    if (projectId != null) {
      if (execution.getProjectId() != null
          && !Objects.equals(execution.getProjectId(), projectId)) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      return projectId;
    }
    if (execution.getProjectId() != null) return execution.getProjectId();
    if (execution.getSourceExecutionId() != null) {
      WorkflowExecutionPO source = executionMapper.selectById(execution.getSourceExecutionId());
      if (source != null && source.getProjectId() != null) return source.getProjectId();
    }
    if (versionMapper != null && definitionMapper != null) {
      WorkflowVersionPO version = versionMapper.selectById(execution.getDefinitionId());
      if (version != null && version.getWorkflowId() != null) {
        WorkflowDefinitionPO definition = definitionMapper.selectById(version.getWorkflowId());
        if (definition != null) return definition.getProjectId();
      }
      WorkflowDefinitionPO definition = definitionMapper.selectById(execution.getDefinitionId());
      if (definition != null) return definition.getProjectId();
    }
    return null;
  }

  private boolean workflowAccessible(String workflowId, Long projectId) {
    if (definitionMapper == null) return true;
    return definitionMapper.selectCount(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId)) > 0L;
  }
}
