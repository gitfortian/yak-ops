package io.yak.ops.business.workflow.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.dao.mapper.WorkflowDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowVersionMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
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
import org.springframework.transaction.annotation.Transactional;

/** 基于 MyBatis-Plus 的工作流定义与版本 DAO。 */
@Repository
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowCatalogDaoImpl implements WorkflowCatalogDao {
  private final WorkflowDefinitionMapper definitionMapper;
  private final WorkflowVersionMapper versionMapper;
  private final WorkflowScheduleMapper scheduleMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public WorkflowCatalogDaoImpl(
      WorkflowDefinitionMapper definitionMapper,
      WorkflowVersionMapper versionMapper,
      WorkflowScheduleMapper scheduleMapper,
      CurrentProject currentProject) {
    this.definitionMapper = definitionMapper;
    this.versionMapper = versionMapper;
    this.scheduleMapper = scheduleMapper;
    this.currentProject = currentProject;
  }

  public WorkflowCatalogDaoImpl(
      WorkflowDefinitionMapper definitionMapper,
      WorkflowVersionMapper versionMapper,
      WorkflowScheduleMapper scheduleMapper) {
    this(
        definitionMapper,
        versionMapper,
        scheduleMapper,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public List<WorkflowDefinitionPO> selectDefinitions() {
    Long projectId = currentProjectId();
    return definitionMapper.selectList(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(projectId != null, WorkflowDefinitionPO::getProjectId, projectId)
            .orderByDesc(WorkflowDefinitionPO::getUpdateTime));
  }

  @Override
  public List<WorkflowVersionPO> selectPublishedVersions(String workflowId) {
    if (!accessible(workflowId)) return List.of();
    return versionMapper.selectList(
        Wrappers.<WorkflowVersionPO>lambdaQuery()
            .eq(WorkflowVersionPO::getWorkflowId, workflowId)
            .eq(WorkflowVersionPO::getVersionKind, "PUBLISHED")
            .orderByAsc(WorkflowVersionPO::getVersionNo));
  }

  @Override
  public WorkflowVersionPO selectVersionById(String versionId) {
    WorkflowVersionPO version = versionMapper.selectById(versionId);
    if (version == null || currentProjectId() == null) return version;
    return accessible(version.getWorkflowId()) ? version : null;
  }

  @Override
  public int upsertDefinition(WorkflowDefinitionPO definition) {
    Long projectId = currentProjectId();
    if (projectId != null) {
      WorkflowDefinitionPO existing = definitionMapper.selectById(definition.getId());
      if (existing != null
          && existing.getProjectId() != null
          && !Objects.equals(existing.getProjectId(), projectId)) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      definition.setProjectId(projectId);
    }
    return definitionMapper.upsert(definition);
  }

  @Override
  public int insertVersion(WorkflowVersionPO version) {
    requireAccessible(version.getWorkflowId());
    return versionMapper.insert(version);
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public int deleteDefinition(String workflowId) {
    Long projectId = currentProjectId();
    if (projectId != null && !accessible(workflowId)) return 0;
    List<String> scheduleIds = scheduleMapper.selectObjs(
            Wrappers.<WorkflowSchedulePO>query()
                .select("id")
                .eq("workflow_id", workflowId)
                .eq(projectId != null, "project_id", projectId))
        .stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();

    int deleted = definitionMapper.delete(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(projectId != null, WorkflowDefinitionPO::getProjectId, projectId));
    if (deleted != 1 || scheduleIds.isEmpty()) return deleted;

    scheduleMapper.delete(
        Wrappers.<WorkflowSchedulePO>lambdaQuery()
            .in(WorkflowSchedulePO::getId, scheduleIds)
            .eq(projectId != null, WorkflowSchedulePO::getProjectId, projectId));
    return deleted;
  }

  @Override
  public int initializeEngineDefinition(String versionId, String engineDefinitionJson) {
    if (selectVersionById(versionId) == null) return 0;
    return versionMapper.initializeEngineDefinition(versionId, engineDefinitionJson);
  }

  @Override
  public int initializeRuntimeMetadata(String versionId, String runtimeMetadataJson) {
    if (selectVersionById(versionId) == null) return 0;
    return versionMapper.initializeRuntimeMetadata(versionId, runtimeMetadataJson);
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private boolean accessible(String workflowId) {
    Long projectId = currentProjectId();
    if (projectId == null) return true;
    if (workflowId == null || workflowId.isBlank()) return false;
    return definitionMapper.selectCount(
        Wrappers.<WorkflowDefinitionPO>lambdaQuery()
            .eq(WorkflowDefinitionPO::getId, workflowId)
            .eq(WorkflowDefinitionPO::getProjectId, projectId)) > 0L;
  }

  private void requireAccessible(String workflowId) {
    if (!accessible(workflowId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }
}
