package io.yak.ops.business.sync.realtime.execution;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.PublishedDefinitionRow;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Owns command keys, single-execution claims and replacement-stop reservations. */
@Component
public class RealtimeExecutionReservationManager {

  private final RealtimeJobStore store;
  private final SyncExecutionStateMachine stateMachine;
  private final RealtimeExecutionPreparation preparation;
  private final TransactionTemplate transactions;

  public RealtimeExecutionReservationManager(
      RealtimeJobStore store,
      SyncExecutionStateMachine stateMachine,
      RealtimeExecutionPreparation preparation,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.stateMachine = stateMachine;
    this.preparation = preparation;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  String normalizeKey(String requestedKey) {
    String key =
        StringUtils.hasText(requestedKey) ? requestedKey.trim() : UUID.randomUUID().toString();
    if (key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
      throw new IllegalArgumentException("Idempotency-Key 格式无效");
    }
    return key;
  }

  RealtimeJobView.Deployment idempotentView(long taskId, String key) {
    DeploymentRow existing = idempotentDeployment(taskId, key);
    return existing == null ? null : store.deploymentView(existing);
  }

  RealtimeJobView.Deployment requireIdempotentView(long taskId, String key) {
    DeploymentRow existing =
        store.deploymentByIdempotencyKey(key)
            .orElseThrow(() -> new IllegalStateException("幂等 Execution 记录不存在"));
    requireIdempotencyOwner(taskId, existing);
    return store.deploymentView(existing);
  }

  RealtimeJobView.Deployment recoverDuplicate(
      long taskId, String key, RuntimeException exception) {
    DeploymentRow raced =
        store.deploymentByIdempotencyKey(key)
            .orElseThrow(() -> new IllegalStateException("幂等 Execution 冲突", exception));
    requireIdempotencyOwner(taskId, raced);
    return store.deploymentView(raced);
  }

  StartReservation reserveStart(
      long taskId,
      String key,
      RealtimeExecutionPrepared prepared,
      boolean requireCurrentPublished,
      RealtimeExecutionIntent intent) {
    StartReservation reservation =
        transactions.execute(
            status -> {
              DefinitionRow locked = store.lockDefinition(taskId);

              DeploymentRow duplicate = idempotentDeployment(taskId, key);
              if (duplicate != null) {
                return new StartReservation(duplicate.id(), false);
              }

              PublishedDefinitionRow currentTarget;
              if (requireCurrentPublished) {
                currentTarget = preparation.requirePublishedDefinition(taskId);
                preparation.requireCurrent(
                    prepared, currentTarget, "已发布定义在校验期间已变化，请刷新后重试");
              } else {
                currentTarget =
                    preparation.requireDefinitionVersion(
                        taskId, prepared.definitionVersion().id());
                preparation.requireCurrent(
                    prepared,
                    currentTarget,
                    "目标 DefinitionVersion 在执行期间已变化，请刷新后重试");
              }

              DeploymentRow previousRow = store.latestDeployment(taskId).orElse(null);
              requireReplacementAllowsStart(
                  previousRow, key, prepared.definitionVersion().id(), intent);
              SyncExecution previous = previousRow == null ? null : previousRow.execution();
              stateMachine.requireNewExecutionAllowed(previous);
              String previousDescription =
                  previous == null
                      ? ""
                      : "；上一 Execution 终态为 " + previous.observedState().name();

              long created =
                  store.insertDeployment(
                      locked,
                      prepared.spec(),
                      prepared.compiled().summary(),
                      preparation.artifactDigest(prepared),
                      prepared.runtimeEnvironment(),
                      key);
              store.bindDeploymentDefinitionVersion(
                  created,
                  prepared.definitionVersion().id(),
                  prepared.definitionVersion().sourceDraftRevision());
              store.event(
                  taskId,
                  created,
                  intent.eventType,
                  null,
                  "STARTING",
                  intent.messagePrefix
                      + " DefinitionVersion v"
                      + prepared.definitionVersion().versionNo()
                      + "，通过运行环境「"
                      + prepared.runtimeEnvironment().name()
                      + "」提交 Flink CDC 任务"
                      + previousDescription);
              return new StartReservation(created, true);
            });

    if (reservation == null) {
      throw new IllegalStateException("未能创建实时同步 Execution 启动预留");
    }
    return reservation;
  }

  DeploymentRow pendingReplacement(long taskId) {
    DeploymentRow latest = store.latestDeployment(taskId).orElse(null);
    return latest != null && latest.replacementPending() ? latest : null;
  }

  DeploymentRow requireStableRunningExecutionRow(long taskId, String action) {
    DeploymentRow row =
        store.latestDeployment(taskId)
            .orElseThrow(() -> new IllegalStateException("当前没有可" + action + "的 SyncExecution"));
    if (row.replacementPending()) {
      throw new IllegalStateException("已有版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
    SyncExecution execution = row.execution();
    requireStableRunningExecution(execution, action);
    if (!execution.engineExecutionRef().bound() || !StringUtils.hasText(row.engineJobId())) {
      throw new IllegalStateException(
          action + "要求当前 SyncExecution 已绑定 EngineExecutionRef，请先执行状态对账");
    }
    return row;
  }

  long requireExecutionVersionId(SyncExecution execution) {
    Long versionId = execution.definitionVersionId();
    if (versionId == null) {
      throw new IllegalStateException("当前 SyncExecution 缺少 DefinitionVersionRef，无法安全执行版本命令");
    }
    return versionId;
  }

  DeploymentRow reserveReplacementStop(
      long taskId,
      long expectedExecutionId,
      long targetDefinitionVersionId,
      String key,
      RealtimeExecutionIntent intent,
      String eventType,
      String message) {
    DeploymentRow reserved =
        transactions.execute(
            status -> {
              store.lockDefinition(taskId);
              DeploymentRow latest =
                  store.latestDeployment(taskId)
                      .orElseThrow(() -> new IllegalStateException("当前 SyncExecution 已变化，请刷新后重试"));
              if (latest.id() != expectedExecutionId) {
                throw new IllegalStateException("当前 SyncExecution 已变化，请刷新后重试");
              }
              SyncExecution execution = latest.execution();
              requireStableRunningExecution(execution, "版本切换");
              if (!execution.engineExecutionRef().bound()
                  || !StringUtils.hasText(latest.engineJobId())) {
                throw new IllegalStateException("当前 SyncExecution 缺少 EngineExecutionRef，请先执行状态对账");
              }
              stateMachine.requireTransition(execution, "STOPPING");
              store.reserveReplacementStop(
                  taskId, latest.id(), intent.name(), targetDefinitionVersionId, key);
              store.event(
                  taskId,
                  latest.id(),
                  eventType,
                  execution.observedState().name(),
                  "STOPPING",
                  message);
              return latest;
            });

    if (reserved == null) {
      throw new IllegalStateException("未能预留版本切换停止命令");
    }
    return reserved;
  }

  void requireLatestExecutionSettled(long taskId) {
    SyncExecution settled = store.latestExecution(taskId).orElse(null);
    if (settled != null && settled.activeOrUncertain()) {
      throw new IllegalStateException("Execution 仍在停止中，请稍后使用相同 Idempotency-Key 重试");
    }
  }

  private DeploymentRow idempotentDeployment(long taskId, String key) {
    DeploymentRow existing = store.deploymentByIdempotencyKey(key).orElse(null);
    if (existing != null) {
      requireIdempotencyOwner(taskId, existing);
    }
    return existing;
  }

  private void requireIdempotencyOwner(long taskId, DeploymentRow deployment) {
    if (deployment.definitionId() != taskId) {
      throw new IllegalStateException("幂等键已被其他实时任务使用");
    }
  }

  private void requireReplacementAllowsStart(
      DeploymentRow previous,
      String key,
      long targetDefinitionVersionId,
      RealtimeExecutionIntent intent) {
    if (previous == null || !previous.replacementPending()) {
      return;
    }
    if (intent == RealtimeExecutionIntent.START) {
      throw new IllegalStateException(
          "已有 Restart/Apply 版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
    if (!previous.replacementMatches(intent.name(), targetDefinitionVersionId, key)) {
      throw new IllegalStateException("已有其他版本替换命令待完成，请使用原 Idempotency-Key 继续该命令");
    }
  }

  private void requireStableRunningExecution(SyncExecution execution, String action) {
    if (execution.terminal()) {
      throw new IllegalStateException("当前 SyncExecution 已结束，请直接启动已发布版本");
    }
    if (!"RUNNING".equals(execution.desiredState().name())
        || !"RUNNING".equals(execution.observedState().name())
        || execution.resultUncertain()) {
      throw new IllegalStateException(
          action
              + "只允许对稳定 RUNNING 的 SyncExecution 执行；STARTING/STOPPING/UNKNOWN/CONFLICT 请先对账");
    }
  }

  record StartReservation(long deploymentId, boolean created) {}
}
