package io.yak.ops.business.sync.realtime.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.sync.realtime.dao.RealtimeJobDao;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobCommandMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDefinitionMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobDeploymentMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobEventMapper;
import io.yak.ops.business.sync.realtime.dao.mapper.RealtimeJobQueryMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDefinitionPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobDeploymentPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobEventPO;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobListRow;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("realtimeSyncFlyway")
public class RealtimeJobDaoImpl implements RealtimeJobDao {

  private final RealtimeJobDefinitionMapper definitionMapper;
  private final RealtimeJobDeploymentMapper deploymentMapper;
  private final RealtimeJobEventMapper eventMapper;
  private final RealtimeJobCommandMapper commandMapper;
  private final RealtimeJobQueryMapper queryMapper;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public RealtimeJobDaoImpl(
      RealtimeJobDefinitionMapper definitionMapper,
      RealtimeJobDeploymentMapper deploymentMapper,
      RealtimeJobEventMapper eventMapper,
      RealtimeJobCommandMapper commandMapper,
      RealtimeJobQueryMapper queryMapper,
      CurrentProject currentProject) {
    this.definitionMapper = definitionMapper;
    this.deploymentMapper = deploymentMapper;
    this.eventMapper = eventMapper;
    this.commandMapper = commandMapper;
    this.queryMapper = queryMapper;
    this.currentProject = currentProject;
  }

  public RealtimeJobDaoImpl(
      RealtimeJobDefinitionMapper definitionMapper,
      RealtimeJobDeploymentMapper deploymentMapper,
      RealtimeJobEventMapper eventMapper,
      RealtimeJobCommandMapper commandMapper,
      RealtimeJobQueryMapper queryMapper) {
    this(
        definitionMapper,
        deploymentMapper,
        eventMapper,
        commandMapper,
        queryMapper,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public long insertDefinition(RealtimeJobDefinitionPO definition) {
    bindCurrentProject(definition);
    definitionMapper.insert(definition);
    if (definition.getId() == null) throw new IllegalStateException("新增实时任务未返回主键");
    return definition.getId();
  }

  @Override
  public int updateDefinition(
      long id,
      String name,
      String description,
      String specJson,
      String digest,
      long environmentId) {
    Long projectId = currentProjectId();
    return definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, id)
            .eq(projectId != null, RealtimeJobDefinitionPO::getProjectId, projectId)
            .set(RealtimeJobDefinitionPO::getJobName, name)
            .set(RealtimeJobDefinitionPO::getDescription, description)
            .set(RealtimeJobDefinitionPO::getRuntimeEnvironmentId, environmentId)
            .set(RealtimeJobDefinitionPO::getSpecJson, specJson)
            .set(RealtimeJobDefinitionPO::getConfigDigest, digest)
            .setSql("definition_version = definition_version + 1")
            .set(RealtimeJobDefinitionPO::getReleaseState, "DRAFT"));
  }

  @Override
  public int publish(long id, int expectedDefinitionVersion, String expectedDigest) {
    Long projectId = currentProjectId();
    return definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, id)
            .eq(projectId != null, RealtimeJobDefinitionPO::getProjectId, projectId)
            .eq(RealtimeJobDefinitionPO::getDefinitionVersion, expectedDefinitionVersion)
            .eq(RealtimeJobDefinitionPO::getConfigDigest, expectedDigest)
            .set(RealtimeJobDefinitionPO::getReleaseState, "PUBLISHED")
            .set(RealtimeJobDefinitionPO::getPublishedVersion, expectedDefinitionVersion));
  }

  @Override
  public Optional<RealtimeJobDefinitionPO> findDefinition(long id) {
    Long projectId = currentProjectId();
    return Optional.ofNullable(
        definitionMapper.selectOne(
            Wrappers.<RealtimeJobDefinitionPO>lambdaQuery()
                .eq(RealtimeJobDefinitionPO::getId, id)
                .eq(projectId != null, RealtimeJobDefinitionPO::getProjectId, projectId)));
  }

  @Override
  public Optional<RealtimeJobDefinitionPO> lockDefinition(long id) {
    Long projectId = currentProjectId();
    return Optional.ofNullable(
        projectId == null
            ? commandMapper.lockDefinition(id)
            : commandMapper.lockDefinitionByProject(id, projectId));
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> deploymentByIdempotencyKey(String key) {
    Long projectId = currentProjectId();
    return deploymentMapper.selectList(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getIdempotencyKey, key)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> latestDeployment(long definitionId) {
    Long projectId = currentProjectId();
    return deploymentMapper.selectList(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .orderByDesc(RealtimeJobDeploymentPO::getId)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> findDeployment(long deploymentId) {
    Long projectId = currentProjectId();
    return Optional.ofNullable(
        deploymentMapper.selectOne(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)));
  }

  @Override
  public long insertDeployment(RealtimeJobDeploymentPO deployment) {
    deployment.setProjectId(resolveDeploymentProject(deployment));
    deploymentMapper.insert(deployment);
    if (deployment.getId() == null) throw new IllegalStateException("新增部署未返回主键");
    return deployment.getId();
  }

  @Override
  public void bindDeploymentDefinitionVersion(
      long deploymentId, long definitionVersionId, int sourceDraftRevision) {
    Long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getStatus, "SUBMITTING")
                .isNull(RealtimeJobDeploymentPO::getDefinitionVersionId)
                .set(RealtimeJobDeploymentPO::getDefinitionVersionId, definitionVersionId)
                .set(RealtimeJobDeploymentPO::getDefinitionVersion, sourceDraftRevision));
    if (updated != 1) {
      throw new IllegalStateException("部署绑定 Published DefinitionVersion 失败：" + deploymentId);
    }
  }

  @Override
  public int markDeploymentRunning(
      long definitionId, long deploymentId, String engineJobId, String runtimeRevision) {
    Long projectId = currentProjectId();
    return deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
            .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
            .eq(RealtimeJobDeploymentPO::getDesiredState, "RUNNING")
            .eq(RealtimeJobDeploymentPO::getObservedState, "STARTING")
            .set(RealtimeJobDeploymentPO::getGatewayJobId, engineJobId)
            .set(RealtimeJobDeploymentPO::getRuntimeVersion, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getRuntimeRevision, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getObservedState, "RUNNING")
            .set(RealtimeJobDeploymentPO::getStatus, "RUNNING")
            .set(RealtimeJobDeploymentPO::getResultUncertain, false)
            .set(RealtimeJobDeploymentPO::getErrorMessage, null));
  }

  @Override
  public void bindDeploymentForStop(long deploymentId, String engineJobId, String runtimeRevision) {
    Long projectId = currentProjectId();
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
            .set(RealtimeJobDeploymentPO::getGatewayJobId, engineJobId)
            .set(RealtimeJobDeploymentPO::getRuntimeVersion, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getRuntimeRevision, runtimeRevision)
            .set(RealtimeJobDeploymentPO::getStatus, "STOPPING")
            .set(RealtimeJobDeploymentPO::getResultUncertain, false)
            .set(RealtimeJobDeploymentPO::getErrorMessage, null));
  }

  @Override
  public void markDeployFailure(
      long definitionId,
      long deploymentId,
      boolean uncertain,
      boolean stopRequested,
      String message) {
    String desiredState = stopRequested ? "STOPPED" : (uncertain ? "RUNNING" : "STOPPED");
    String observedState = uncertain ? "UNKNOWN" : "FAILED";
    Long projectId = currentProjectId();
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
            .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
            .set(RealtimeJobDeploymentPO::getDesiredState, desiredState)
            .set(RealtimeJobDeploymentPO::getObservedState, observedState)
            .set(RealtimeJobDeploymentPO::getStatus, observedState)
            .set(RealtimeJobDeploymentPO::getResultUncertain, uncertain)
            .set(RealtimeJobDeploymentPO::getErrorMessage, message));
  }

  @Override
  public void markStopping(long definitionId, Long deploymentId) {
    if (deploymentId == null) return;
    Long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
                .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
                .set(RealtimeJobDeploymentPO::getObservedState, "STOPPING")
                .set(RealtimeJobDeploymentPO::getStatus, "STOPPING"));
    if (updated != 1) {
      throw new IllegalStateException("Execution 不存在或已变化，无法记录停止意图：" + deploymentId);
    }
  }

  @Override
  public void reserveReplacementStop(
      long definitionId,
      long deploymentId,
      String commandType,
      long targetDefinitionVersionId,
      String idempotencyKey) {
    Long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getDesiredState, "RUNNING")
                .eq(RealtimeJobDeploymentPO::getObservedState, "RUNNING")
                .eq(RealtimeJobDeploymentPO::getResultUncertain, false)
                .isNull(RealtimeJobDeploymentPO::getReplacementIdempotencyKey)
                .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
                .set(RealtimeJobDeploymentPO::getObservedState, "STOPPING")
                .set(RealtimeJobDeploymentPO::getStatus, "STOPPING")
                .set(RealtimeJobDeploymentPO::getReplacementCommandType, commandType)
                .set(
                    RealtimeJobDeploymentPO::getReplacementTargetDefinitionVersionId,
                    targetDefinitionVersionId)
                .set(RealtimeJobDeploymentPO::getReplacementIdempotencyKey, idempotencyKey));
    if (updated != 1) {
      throw new IllegalStateException("Execution 已变化，无法预留版本替换命令：" + deploymentId);
    }
  }

  @Override
  public void clearReplacementIntent(long deploymentId, String idempotencyKey) {
    Long projectId = currentProjectId();
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
                .eq(RealtimeJobDeploymentPO::getReplacementIdempotencyKey, idempotencyKey)
                .set(RealtimeJobDeploymentPO::getReplacementCommandType, null)
                .set(RealtimeJobDeploymentPO::getReplacementTargetDefinitionVersionId, null)
                .set(RealtimeJobDeploymentPO::getReplacementIdempotencyKey, null));
    if (updated != 1) {
      throw new IllegalStateException("版本替换命令完成标记失败：" + deploymentId);
    }
  }

  @Override
  public void reconcile(
      long definitionId,
      Long deploymentId,
      String observedState,
      String deploymentState,
      String engineJobId,
      String error) {
    if (deploymentId == null) return;
    Long projectId = currentProjectId();
    int updated = projectId == null
        ? commandMapper.reconcileDeployment(
            deploymentId, observedState, deploymentState, engineJobId, error)
        : commandMapper.reconcileDeploymentByProject(
            deploymentId, projectId, observedState, deploymentState, engineJobId, error);
    if (projectId != null && updated != 1) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  @Override
  public void markTerminalFailure(long definitionId, Long deploymentId, String message) {
    if (deploymentId == null) return;
    Long projectId = currentProjectId();
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
            .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
            .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
            .set(RealtimeJobDeploymentPO::getObservedState, "FAILED")
            .set(RealtimeJobDeploymentPO::getStatus, "FAILED")
            .set(RealtimeJobDeploymentPO::getErrorMessage, message));
  }

  @Override
  public List<RealtimeJobDeploymentPO> reconcileExecutions() {
    Long projectId = currentProjectId();
    return projectId == null
        ? commandMapper.reconcileExecutions()
        : commandMapper.reconcileExecutionsByProject(projectId);
  }

  @Override
  public int deleteDefinition(long id) {
    Long projectId = currentProjectId();
    int deleted =
        definitionMapper.delete(
            Wrappers.<RealtimeJobDefinitionPO>lambdaQuery()
                .eq(RealtimeJobDefinitionPO::getId, id)
                .eq(projectId != null, RealtimeJobDefinitionPO::getProjectId, projectId));
    if (deleted != 1) return deleted;
    eventMapper.delete(
        Wrappers.<RealtimeJobEventPO>lambdaQuery()
            .eq(RealtimeJobEventPO::getDefinitionId, id));
    deploymentMapper.delete(
        Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
            .eq(RealtimeJobDeploymentPO::getDefinitionId, id)
            .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId));
    return deleted;
  }

  @Override
  public void insertEvent(RealtimeJobEventPO event) {
    if (currentProjectId() != null && findDefinition(event.getDefinitionId()).isEmpty()) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    eventMapper.insert(event);
  }

  @Override
  public boolean tryAcquireReconcileLease(String owner, int leaseSeconds) {
    return commandMapper.tryAcquireLease(owner, Math.max(5, leaseSeconds)) == 1;
  }

  @Override
  public List<RealtimeJobEventPO> events(long definitionId) {
    if (currentProjectId() != null && findDefinition(definitionId).isEmpty()) return List.of();
    return eventMapper.selectList(
        Wrappers.<RealtimeJobEventPO>lambdaQuery()
            .eq(RealtimeJobEventPO::getDefinitionId, definitionId)
            .orderByDesc(RealtimeJobEventPO::getId)
            .last("LIMIT 200"));
  }

  @Override
  public int bindRuntimeIdentity(String idempotencyKey, String runtimeJobName) {
    Long projectId = currentProjectId();
    return deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getIdempotencyKey, idempotencyKey)
            .eq(projectId != null, RealtimeJobDeploymentPO::getProjectId, projectId)
            .isNull(RealtimeJobDeploymentPO::getGatewayJobId)
            .eq(RealtimeJobDeploymentPO::getRuntimeIdentityState, "REQUIRED")
            .and(
                q ->
                    q.isNull(RealtimeJobDeploymentPO::getRuntimeJobName)
                        .or()
                        .eq(RealtimeJobDeploymentPO::getRuntimeJobName, runtimeJobName))
            .set(RealtimeJobDeploymentPO::getRuntimeJobName, runtimeJobName)
            .set(RealtimeJobDeploymentPO::getRuntimeIdentityState, "BOUND"));
  }

  @Override
  public Optional<String> runtimeJobName(long deploymentId) {
    return findDeployment(deploymentId)
        .map(RealtimeJobDeploymentPO::getRuntimeJobName)
        .filter(value -> value != null && !value.isBlank());
  }

  @Override
  public long countPage(String keyword, Long id, String releaseState, String stateGroup) {
    Long projectId = currentProjectId();
    return projectId == null
        ? queryMapper.count(keyword, id, releaseState, stateGroup)
        : queryMapper.countByProject(projectId, keyword, id, releaseState, stateGroup);
  }

  @Override
  public List<RealtimeJobListRow> page(
      String keyword,
      Long id,
      String releaseState,
      String stateGroup,
      int limit,
      int offset) {
    Long projectId = currentProjectId();
    return projectId == null
        ? queryMapper.page(keyword, id, releaseState, stateGroup, limit, offset)
        : queryMapper.pageByProject(
            projectId, keyword, id, releaseState, stateGroup, limit, offset);
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private void bindCurrentProject(RealtimeJobDefinitionPO definition) {
    Long projectId = currentProjectId();
    if (projectId == null) return;
    if (definition.getProjectId() != null
        && !Objects.equals(projectId, definition.getProjectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    definition.setProjectId(projectId);
  }

  private Long resolveDeploymentProject(RealtimeJobDeploymentPO deployment) {
    Long projectId = currentProjectId();
    if (projectId != null) {
      if (deployment.getProjectId() != null
          && !Objects.equals(projectId, deployment.getProjectId())) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      if (findDefinition(deployment.getDefinitionId()).isEmpty()) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      return projectId;
    }
    if (deployment.getProjectId() != null) return deployment.getProjectId();
    RealtimeJobDefinitionPO definition = definitionMapper.selectById(deployment.getDefinitionId());
    return definition == null ? null : definition.getProjectId();
  }
}
