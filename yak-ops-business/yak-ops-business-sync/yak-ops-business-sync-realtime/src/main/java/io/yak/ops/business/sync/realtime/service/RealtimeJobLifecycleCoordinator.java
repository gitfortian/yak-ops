package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.FlinkJobDiscoveryClient;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Authoritative reconciliation between SyncExecution state and the real Flink runtime. */
@Service
public class RealtimeJobLifecycleCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeJobLifecycleCoordinator.class);

  private final RealtimeJobStore store;
  private final RealtimeRuntimeIdentityStore identityStore;
  private final FlinkJobDiscoveryClient discovery;
  private final RealtimeEngineGateway gateway;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final SyncExecutionStateMachine executionStateMachine;
  private final RealtimeSyncProperties properties;
  private final TransactionTemplate transactions;
  private final long orphanGraceSeconds;
  private final ConcurrentHashMap<Long, AtomicInteger> consecutiveEngineFailures =
      new ConcurrentHashMap<>();

  public RealtimeJobLifecycleCoordinator(
      RealtimeJobStore store,
      RealtimeRuntimeIdentityStore identityStore,
      FlinkJobDiscoveryClient discovery,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      SyncExecutionStateMachine executionStateMachine,
      RealtimeSyncProperties properties,
      @Value("${yak.sync.realtime.orphan-recovery-grace-seconds:120}") long orphanGraceSeconds,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.identityStore = identityStore;
    this.discovery = discovery;
    this.gateway = gateway;
    this.runtimeResolver = runtimeResolver;
    this.executionStateMachine = executionStateMachine;
    this.properties = properties;
    this.orphanGraceSeconds = Math.max(10, orphanGraceSeconds);
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public void reconcileAll() {
    for (DeploymentRow candidate : store.reconcileCandidates()) {
      long taskId = candidate.definitionId();
      try {
        reconcile(taskId);
        consecutiveEngineFailures.remove(taskId);
      } catch (RealtimeEngineException exception) {
        int failures =
            consecutiveEngineFailures
                .computeIfAbsent(taskId, ignored -> new AtomicInteger())
                .incrementAndGet();
        if (failures >= Math.max(1, properties.getReconcileFailureThreshold())) {
          markEngineUnavailable(taskId, exception.getMessage());
        }
      } catch (RuntimeException exception) {
        LOG.warn("Realtime execution for task {} reconciliation failed", taskId, exception);
      }
    }
  }

  public RealtimeJobView reconcile(long id) {
    DefinitionRow definition = requireDefinition(id);
    DeploymentRow deployment = store.latestDeployment(id).orElse(null);
    if (deployment == null) {
      return store.view(id);
    }

    String jobId = deployment.engineJobId();
    if (!StringUtils.hasText(jobId)) {
      jobId = recoverJobId(definition, deployment);
      if (!StringUtils.hasText(jobId)) {
        return store.view(id);
      }
      deployment = store.latestDeployment(id).orElse(deployment);
    }

    ComputeEnvironmentSnapshot runtimeEnvironment = runtimeResolver.deployment(definition, deployment);
    RuntimeStatus runtime = gateway.status(runtimeEnvironment, jobId);
    applyRuntimeState(id, deployment.id(), jobId, runtimeEnvironment, runtime);
    return store.view(id);
  }

  /** Refuses metadata deletion unless the latest execution is terminal and Flink is inactive. */
  public void assertSafeToDelete(long id) {
    DefinitionRow definition = requireDefinition(id);
    DeploymentRow deployment = store.latestDeployment(id).orElse(null);
    if (deployment == null) return;

    executionStateMachine.requireDefinitionMutable(deployment.execution());
    ComputeEnvironmentSnapshot runtimeEnvironment = runtimeResolver.deployment(definition, deployment);
    if (StringUtils.hasText(deployment.engineJobId())) {
      requireInactive(gateway.status(runtimeEnvironment, deployment.engineJobId()));
      return;
    }
    String runtimeName = identityStore.findByDeploymentId(deployment.id()).orElse(null);
    if (!StringUtils.hasText(runtimeName)) {
      return;
    }
    for (String jobId : discovery.findJobIds(runtimeEnvironment, runtimeName)) {
      requireInactive(gateway.status(runtimeEnvironment, jobId));
    }
  }

  private String recoverJobId(DefinitionRow definition, DeploymentRow deployment) {
    String runtimeName = identityStore.findByDeploymentId(deployment.id()).orElse(null);
    if (!StringUtils.hasText(runtimeName)) {
      if (graceExpired(deployment)) {
        settleMissing(
            definition.id(), deployment, "Gateway 尚未绑定 runtime identity，确认 CLI 未开始提交");
      }
      return null;
    }
    ComputeEnvironmentSnapshot runtimeEnvironment = runtimeResolver.deployment(definition, deployment);
    List<String> matches = discovery.findJobIds(runtimeEnvironment, runtimeName);
    if (matches.size() > 1) {
      markConflict(definition.id(), deployment, matches.size());
      return null;
    }
    if (matches.isEmpty()) {
      if (graceExpired(deployment)) {
        settleMissing(definition.id(), deployment, "恢复窗口内未发现匹配的 Flink runtime job");
      }
      return null;
    }

    String recoveredJobId = matches.get(0);
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(definition.id());
          DeploymentRow latest = store.latestDeployment(definition.id()).orElse(null);
          if (!sameDeployment(deployment, latest) || StringUtils.hasText(latest.engineJobId())) {
            return;
          }
          SyncExecution execution = latest.execution();
          store.reconcile(
              definition.id(),
              latest.id(),
              execution.observedState().name(),
              latest.status(),
              recoveredJobId,
              execution.errorMessage());
          store.event(
              definition.id(),
              latest.id(),
              "FLINK_JOB_ID_RECOVERED",
              execution.observedState().name(),
              execution.observedState().name(),
              "已通过 runtime job identity 找回 Flink JobId：" + recoveredJobId);
        });
    return recoveredJobId;
  }

  private void applyRuntimeState(
      long definitionId,
      long deploymentId,
      String jobId,
      ComputeEnvironmentSnapshot runtimeEnvironment,
      RuntimeStatus runtime) {
    Boolean stop =
        transactions.execute(
            ignored -> {
              store.lockDefinition(definitionId);
              DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
              if (latest == null
                  || latest.id() != deploymentId
                  || (StringUtils.hasText(latest.engineJobId())
                      && !Objects.equals(latest.engineJobId(), jobId))) {
                return false;
              }
              SyncExecution execution = latest.execution();

              if (runtime.state() == RuntimeStatus.State.UNKNOWN) {
                transition(
                    definitionId,
                    latest,
                    "UNKNOWN",
                    "UNKNOWN",
                    jobId,
                    "Flink 当前运行状态未知");
                return false;
              }
              if (execution.desiredState().name().equals("RUNNING")) {
                if (runtime.state() == RuntimeStatus.State.RUNNING) {
                  transition(definitionId, latest, "RUNNING", "RUNNING", jobId, null);
                } else {
                  failExpectedRunning(
                      definitionId,
                      latest,
                      runtime.state() == RuntimeStatus.State.TERMINATED
                          ? "Flink 任务已终止"
                          : "Flink 中未找到期望运行的任务");
                }
                return false;
              }
              if (runtime.state() == RuntimeStatus.State.RUNNING) {
                transition(definitionId, latest, "STOPPING", "STOPPING", jobId, null);
                return true;
              }
              toStopped(definitionId, latest, jobId, "Flink 已确认无活动任务");
              return false;
            });

    if (Boolean.TRUE.equals(stop)) {
      try {
        gateway.stop(runtimeEnvironment, jobId);
      } catch (RealtimeEngineException exception) {
        markUnknown(definitionId, deploymentId, jobId, exception.getMessage());
        throw exception;
      }
    }
  }

  private void settleMissing(long definitionId, DeploymentRow deployment, String reason) {
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(definitionId);
          DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
          if (!sameDeployment(deployment, latest) || StringUtils.hasText(latest.engineJobId())) {
            return;
          }
          SyncExecution execution = latest.execution();
          if (execution.desiredState().name().equals("RUNNING")) {
            failExpectedRunning(definitionId, latest, "提交结果不确定，" + reason);
          } else {
            toStopped(definitionId, latest, null, reason + "，已确认停止");
          }
        });
  }

  private void markConflict(long definitionId, DeploymentRow deployment, int count) {
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(definitionId);
          DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
          if (!sameDeployment(deployment, latest) || StringUtils.hasText(latest.engineJobId())) {
            return;
          }
          SyncExecution execution = latest.execution();
          String target =
              execution.observedState() == ObservedState.STOPPING ? "UNKNOWN" : "CONFLICT";
          String message = "runtime job identity 匹配到 " + count + " 个 Flink Job，拒绝自动绑定";
          transition(definitionId, latest, target, "UNKNOWN", null, message);
        });
  }

  private void failExpectedRunning(
      long definitionId, DeploymentRow deployment, String message) {
    SyncExecution execution = deployment.execution();
    executionStateMachine.requireTransition(execution, "FAILED");
    store.markTerminalFailure(definitionId, deployment.id(), message);
    store.event(
        definitionId,
        deployment.id(),
        "FLINK_JOB_LOST",
        execution.observedState().name(),
        "FAILED",
        message);
  }

  private void markEngineUnavailable(long definitionId, String detail) {
    try {
      transactions.executeWithoutResult(
          ignored -> {
            store.lockDefinition(definitionId);
            DeploymentRow deployment = store.latestDeployment(definitionId).orElse(null);
            if (deployment == null) return;
            SyncExecution execution = deployment.execution();
            if (execution.terminal() || execution.observedState() == ObservedState.UNKNOWN) {
              return;
            }
            executionStateMachine.requireTransition(execution, "UNKNOWN");
            String message = "Flink 状态不可用" + (detail == null ? "" : "：" + detail);
            store.reconcile(
                definitionId,
                deployment.id(),
                "UNKNOWN",
                "UNKNOWN",
                deployment.engineJobId(),
                message);
            store.event(
                definitionId,
                deployment.id(),
                "FLINK_UNAVAILABLE",
                execution.observedState().name(),
                "UNKNOWN",
                message);
          });
    } catch (RuntimeException exception) {
      LOG.debug("Unable to mark realtime execution for task {} UNKNOWN", definitionId, exception);
    }
  }

  private void markUnknown(long definitionId, long deploymentId, String jobId, String message) {
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(definitionId);
          DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
          if (latest == null || latest.id() != deploymentId) {
            return;
          }
          transition(definitionId, latest, "UNKNOWN", "UNKNOWN", jobId, message);
        });
  }

  private void transition(
      long definitionId,
      DeploymentRow deployment,
      String observed,
      String deploymentState,
      String jobId,
      String error) {
    SyncExecution execution = deployment.execution();
    executionStateMachine.requireTransition(execution, observed);
    store.reconcile(definitionId, deployment.id(), observed, deploymentState, jobId, error);
    if (!observed.equals(execution.observedState().name())
        || !Objects.equals(error, execution.errorMessage())) {
      store.event(
          definitionId,
          deployment.id(),
          "STATE_RECONCILED",
          execution.observedState().name(),
          observed,
          error == null ? "Flink 状态已对账" : error);
    }
  }

  private void toStopped(
      long definitionId, DeploymentRow deployment, String jobId, String message) {
    ObservedState from = deployment.execution().observedState();
    if (from == ObservedState.STARTING || from == ObservedState.RUNNING) {
      executionStateMachine.requireTransition(from, ObservedState.STOPPING);
      store.reconcile(definitionId, deployment.id(), "STOPPING", "STOPPING", jobId, null);
      from = ObservedState.STOPPING;
    }
    if (from != ObservedState.STOPPED) {
      executionStateMachine.requireTransition(from, ObservedState.STOPPED);
    }
    store.reconcile(definitionId, deployment.id(), "STOPPED", "STOPPED", jobId, null);
    store.event(
        definitionId,
        deployment.id(),
        "STOPPED",
        from.name(),
        "STOPPED",
        message);
  }

  private void requireInactive(RuntimeStatus runtime) {
    if (runtime.state() == RuntimeStatus.State.RUNNING) {
      throw new IllegalStateException("Flink 中仍存在活动任务，禁止删除实时同步元数据");
    }
    if (runtime.state() == RuntimeStatus.State.UNKNOWN) {
      throw new IllegalStateException("无法确认 Flink 任务是否已停止，禁止删除实时同步元数据");
    }
  }

  private boolean graceExpired(DeploymentRow deployment) {
    return deployment.createTime() != null
        && deployment.createTime().isBefore(LocalDateTime.now().minusSeconds(orphanGraceSeconds));
  }

  private boolean sameDeployment(DeploymentRow expected, DeploymentRow current) {
    return expected != null && current != null && expected.id() == current.id();
  }

  private DefinitionRow requireDefinition(long id) {
    return store
        .definition(id)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
  }
}
