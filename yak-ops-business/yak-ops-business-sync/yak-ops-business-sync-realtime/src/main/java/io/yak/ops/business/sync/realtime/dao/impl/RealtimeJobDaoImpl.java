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
import java.util.List;
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

  public RealtimeJobDaoImpl(
      RealtimeJobDefinitionMapper definitionMapper,
      RealtimeJobDeploymentMapper deploymentMapper,
      RealtimeJobEventMapper eventMapper,
      RealtimeJobCommandMapper commandMapper,
      RealtimeJobQueryMapper queryMapper) {
    this.definitionMapper = definitionMapper;
    this.deploymentMapper = deploymentMapper;
    this.eventMapper = eventMapper;
    this.commandMapper = commandMapper;
    this.queryMapper = queryMapper;
  }

  @Override
  public long insertDefinition(RealtimeJobDefinitionPO definition) {
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
    return definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, id)
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
    return definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, id)
            .eq(RealtimeJobDefinitionPO::getDefinitionVersion, expectedDefinitionVersion)
            .eq(RealtimeJobDefinitionPO::getConfigDigest, expectedDigest)
            .set(RealtimeJobDefinitionPO::getReleaseState, "PUBLISHED")
            .set(RealtimeJobDefinitionPO::getPublishedVersion, expectedDefinitionVersion));
  }

  @Override
  public Optional<RealtimeJobDefinitionPO> findDefinition(long id) {
    return Optional.ofNullable(definitionMapper.selectById(id));
  }

  @Override
  public Optional<RealtimeJobDefinitionPO> lockDefinition(long id) {
    return Optional.ofNullable(commandMapper.lockDefinition(id));
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> deploymentByIdempotencyKey(String key) {
    return deploymentMapper.selectList(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(RealtimeJobDeploymentPO::getIdempotencyKey, key)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> latestDeployment(long definitionId) {
    return deploymentMapper.selectList(
            Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .orderByDesc(RealtimeJobDeploymentPO::getId)
                .last("LIMIT 1"))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<RealtimeJobDeploymentPO> findDeployment(long deploymentId) {
    return Optional.ofNullable(deploymentMapper.selectById(deploymentId));
  }

  @Override
  public long insertDeployment(RealtimeJobDeploymentPO deployment) {
    deploymentMapper.insert(deployment);
    if (deployment.getId() == null) throw new IllegalStateException("新增部署未返回主键");
    return deployment.getId();
  }

  @Override
  public void bindDeploymentDefinitionVersion(
      long deploymentId, long definitionVersionId, int sourceDraftRevision) {
    int updated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getStatus, "SUBMITTING")
                .isNull(RealtimeJobDeploymentPO::getDefinitionVersionId)
                .set(RealtimeJobDeploymentPO::getDefinitionVersionId, definitionVersionId)
                .set(RealtimeJobDeploymentPO::getDefinitionVersion, sourceDraftRevision));
    if (updated != 1) {
      throw new IllegalStateException("部署绑定 Published DefinitionVersion 失败：" + deploymentId);
    }
  }

  @Override
  public int markStarting(long definitionId) {
    return definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, definitionId)
            .set(RealtimeJobDefinitionPO::getDesiredState, "RUNNING")
            .set(RealtimeJobDefinitionPO::getObservedState, "STARTING")
            .set(RealtimeJobDefinitionPO::getLastError, null));
  }

  @Override
  public int markDeploymentRunning(
      long definitionId, long deploymentId, String engineJobId, String runtimeRevision) {
    int executionUpdated =
        deploymentMapper.update(
            null,
            Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                .eq(RealtimeJobDeploymentPO::getDesiredState, "RUNNING")
                .eq(RealtimeJobDeploymentPO::getObservedState, "STARTING")
                .set(RealtimeJobDeploymentPO::getGatewayJobId, engineJobId)
                .set(RealtimeJobDeploymentPO::getRuntimeVersion, runtimeRevision)
                .set(RealtimeJobDeploymentPO::getRuntimeRevision, runtimeRevision)
                .set(RealtimeJobDeploymentPO::getObservedState, "RUNNING")
                .set(RealtimeJobDeploymentPO::getStatus, "RUNNING")
                .set(RealtimeJobDeploymentPO::getResultUncertain, false)
                .set(RealtimeJobDeploymentPO::getErrorMessage, null));
    if (executionUpdated != 1) return executionUpdated;

    definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, definitionId)
            .set(RealtimeJobDefinitionPO::getDesiredState, "RUNNING")
            .set(RealtimeJobDefinitionPO::getObservedState, "RUNNING")
            .set(RealtimeJobDefinitionPO::getLastError, null));
    return executionUpdated;
  }

  @Override
  public void bindDeploymentForStop(long deploymentId, String engineJobId, String runtimeRevision) {
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
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
    deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getId, deploymentId)
            .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
            .set(RealtimeJobDeploymentPO::getDesiredState, desiredState)
            .set(RealtimeJobDeploymentPO::getObservedState, observedState)
            .set(RealtimeJobDeploymentPO::getStatus, observedState)
            .set(RealtimeJobDeploymentPO::getResultUncertain, uncertain)
            .set(RealtimeJobDeploymentPO::getErrorMessage, message));
    definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, definitionId)
            .set(RealtimeJobDefinitionPO::getDesiredState, desiredState)
            .set(RealtimeJobDefinitionPO::getObservedState, observedState)
            .set(RealtimeJobDefinitionPO::getLastError, message));
  }

  @Override
  public void markStopping(long definitionId, Long deploymentId) {
    if (deploymentId != null) {
      int updated =
          deploymentMapper.update(
              null,
              Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
                  .eq(RealtimeJobDeploymentPO::getId, deploymentId)
                  .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
                  .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
                  .set(RealtimeJobDeploymentPO::getObservedState, "STOPPING")
                  .set(RealtimeJobDeploymentPO::getStatus, "STOPPING"));
      if (updated != 1) {
        throw new IllegalStateException("Execution 不存在或已变化，无法记录停止意图：" + deploymentId);
      }
    }
    definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, definitionId)
            .set(RealtimeJobDefinitionPO::getDesiredState, "STOPPED")
            .set(RealtimeJobDefinitionPO::getObservedState, deploymentId == null ? "STOPPED" : "STOPPING"));
  }

  @Override
  public void reconcile(
      long definitionId,
      Long deploymentId,
      String observedState,
      String deploymentState,
      String engineJobId,
      String error) {
    if (deploymentId != null) {
      commandMapper.reconcileDeployment(
          deploymentId, observedState, deploymentState, engineJobId, error);
    }
    definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, definitionId)
            .set(RealtimeJobDefinitionPO::getObservedState, observedState)
            .set(RealtimeJobDefinitionPO::getLastError, error));
  }

  @Override
  public void markTerminalFailure(long definitionId, Long deploymentId, String message) {
    if (deploymentId != null) {
      deploymentMapper.update(
          null,
          Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
              .eq(RealtimeJobDeploymentPO::getId, deploymentId)
              .eq(RealtimeJobDeploymentPO::getDefinitionId, definitionId)
              .set(RealtimeJobDeploymentPO::getDesiredState, "STOPPED")
              .set(RealtimeJobDeploymentPO::getObservedState, "FAILED")
              .set(RealtimeJobDeploymentPO::getStatus, "FAILED")
              .set(RealtimeJobDeploymentPO::getErrorMessage, message));
    }
    definitionMapper.update(
        null,
        Wrappers.<RealtimeJobDefinitionPO>lambdaUpdate()
            .eq(RealtimeJobDefinitionPO::getId, definitionId)
            .set(RealtimeJobDefinitionPO::getDesiredState, "STOPPED")
            .set(RealtimeJobDefinitionPO::getObservedState, "FAILED")
            .set(RealtimeJobDefinitionPO::getLastError, message));
  }

  @Override
  public List<RealtimeJobDeploymentPO> reconcileExecutions() {
    return commandMapper.reconcileExecutions();
  }

  @Override
  public List<RealtimeJobDefinitionPO> desiredJobs() {
    List<Long> ids =
        reconcileExecutions().stream()
            .map(RealtimeJobDeploymentPO::getDefinitionId)
            .distinct()
            .toList();
    if (ids.isEmpty()) return List.of();
    return definitionMapper.selectBatchIds(ids).stream()
        .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
        .toList();
  }

  @Override
  public boolean hasOtherDesiredRunning(long id) {
    return !commandMapper.lockOtherDesiredRunning(id).isEmpty();
  }

  @Override
  public int deleteDefinition(long id) {
    int deleted =
        definitionMapper.delete(
            Wrappers.<RealtimeJobDefinitionPO>lambdaQuery().eq(RealtimeJobDefinitionPO::getId, id));
    if (deleted != 1) return deleted;

    // Audit-safe deletion is tracked separately as GAP-08 and is intentionally not changed in Wave 3.
    eventMapper.delete(
        Wrappers.<RealtimeJobEventPO>lambdaQuery()
            .eq(RealtimeJobEventPO::getDefinitionId, id));
    deploymentMapper.delete(
        Wrappers.<RealtimeJobDeploymentPO>lambdaQuery()
            .eq(RealtimeJobDeploymentPO::getDefinitionId, id));
    return deleted;
  }

  @Override
  public void insertEvent(RealtimeJobEventPO event) {
    eventMapper.insert(event);
  }

  @Override
  public boolean tryAcquireReconcileLease(String owner, int leaseSeconds) {
    return commandMapper.tryAcquireLease(owner, Math.max(5, leaseSeconds)) == 1;
  }

  @Override
  public List<RealtimeJobEventPO> events(long definitionId) {
    return eventMapper.selectList(
        Wrappers.<RealtimeJobEventPO>lambdaQuery()
            .eq(RealtimeJobEventPO::getDefinitionId, definitionId)
            .orderByDesc(RealtimeJobEventPO::getId)
            .last("LIMIT 200"));
  }

  @Override
  public int bindRuntimeIdentity(String idempotencyKey, String runtimeJobName) {
    return deploymentMapper.update(
        null,
        Wrappers.<RealtimeJobDeploymentPO>lambdaUpdate()
            .eq(RealtimeJobDeploymentPO::getIdempotencyKey, idempotencyKey)
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
    return queryMapper.count(keyword, id, releaseState, stateGroup);
  }

  @Override
  public List<RealtimeJobListRow> page(
      String keyword,
      Long id,
      String releaseState,
      String stateGroup,
      int limit,
      int offset) {
    return queryMapper.page(keyword, id, releaseState, stateGroup, limit, offset);
  }
}
