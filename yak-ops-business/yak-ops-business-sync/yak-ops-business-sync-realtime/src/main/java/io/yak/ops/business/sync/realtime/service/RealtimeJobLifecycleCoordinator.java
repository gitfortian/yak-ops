package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.RealtimeStateMachine;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Authoritative scheduled/manual reconciliation against the real Flink runtime. */
@Service
public class RealtimeJobLifecycleCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeJobLifecycleCoordinator.class);

  private final RealtimeJobStore store;
  private final RealtimeRuntimeIdentityStore identityStore;
  private final FlinkJobDiscoveryClient discovery;
  private final RealtimeEngineGateway gateway;
  private final RealtimeStateMachine stateMachine;
  private final TransactionTemplate transactions;
  private final long orphanGraceSeconds;

  public RealtimeJobLifecycleCoordinator(
      RealtimeJobStore store,
      RealtimeRuntimeIdentityStore identityStore,
      FlinkJobDiscoveryClient discovery,
      RealtimeEngineGateway gateway,
      RealtimeStateMachine stateMachine,
      @Value("${yak.sync.realtime.orphan-recovery-grace-seconds:120}") long orphanGraceSeconds,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.identityStore = identityStore;
    this.discovery = discovery;
    this.gateway = gateway;
    this.stateMachine = stateMachine;
    this.orphanGraceSeconds = Math.max(10, orphanGraceSeconds);
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public void reconcileAll() {
    for (DefinitionRow candidate : store.desiredJobs()) {
      try {
        reconcile(candidate.id());
      } catch (RuntimeException exception) {
        LOG.warn("Realtime job {} reconciliation failed", candidate.id(), exception);
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

    RuntimeStatus runtime = gateway.status(jobId);
    applyRuntimeState(id, deployment.id(), jobId, runtime);
    return store.view(id);
  }

  /** Refuses metadata deletion unless Flink proves that no matching runtime job is active. */
  public void assertSafeToDelete(long id) {
    DefinitionRow definition = requireDefinition(id);
    stateMachine.requireDefinitionMutable(definition.desiredState(), definition.observedState());
    DeploymentRow deployment = store.latestDeployment(id).orElse(null);
    if (deployment == null) {
      return;
    }
    if (StringUtils.hasText(deployment.engineJobId())) {
      requireInactive(gateway.status(deployment.engineJobId()));
      return;
    }
    String runtimeName = identityStore.findByDeploymentId(deployment.id()).orElse(null);
    if (StringUtils.hasText(runtimeName)) {
      for (String jobId : discovery.findJobIds(runtimeName)) {
        requireInactive(gateway.status(jobId));
      }
      return;
    }
    String identityState = identityStore.state(deployment.id()).orElse("LEGACY");
    if ("REQUIRED".equals(identityState)) {
      return;
    }
    if (deployment.resultUncertain()) {
      throw new IllegalStateException(
          "历史部署结果不确定且缺少 runtime job identity，请先在 Flink UI 确认无活动任务");
    }
  }

  private String recoverJobId(DefinitionRow definition, DeploymentRow deployment) {
    String runtimeName = identityStore.findByDeploymentId(deployment.id()).orElse(null);
    if (!StringUtils.hasText(runtimeName)) {
      String identityState = identityStore.state(deployment.id()).orElse("LEGACY");
      if ("REQUIRED".equals(identityState) && graceExpired(deployment)) {
        settleMissing(
            definition.id(), deployment, "Gateway 尚未绑定 runtime identity，确认 CLI 未开始提交");
      }
      return null;
    }
    List<String> matches = discovery.findJobIds(runtimeName);
    if (matches.size() > 1) {
      markConflict(definition.id(), deployment, matches.size());
      return null;
    }
    if (matches.isEmpty()) {
      if (graceExpired(deployment)) {
        settleMissing(
            definition.id(), deployment, "恢复窗口内未发现匹配的 Flink runtime job");
      }
      return null;
    }

    String recoveredJobId = matches.get(0);
    transactions.executeWithoutResult(
        ignored -> {
          DefinitionRow current = store.lockDefinition(definition.id());
          DeploymentRow latest = store.latestDeployment(definition.id()).orElse(null);
          if (!sameDeployment(deployment, latest) || StringUtils.hasText(latest.engineJobId())) {
            return;
          }
          store.reconcile(
              current.id(), latest.id(), current.observedState(), latest.status(), recoveredJobId,
              current.lastError());
          store.event(
              current.id(), latest.id(), "FLINK_JOB_ID_RECOVERED", current.observedState(),
              current.observedState(), "已通过 runtime job identity 找回 Flink JobId：" + recoveredJobId);
        });
    return recoveredJobId;
  }

  private void applyRuntimeState(
      long definitionId, long deploymentId, String jobId, RuntimeStatus runtime) {
    Boolean stop =
        transactions.execute(
            ignored -> {
              DefinitionRow current = store.lockDefinition(definitionId);
              DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
              if (latest == null || latest.id() != deploymentId
                  || (StringUtils.hasText(latest.engineJobId())
                      && !Objects.equals(latest.engineJobId(), jobId))) {
                return false;
              }

              if (runtime.state() == RuntimeStatus.State.UNKNOWN) {
                transition(current, latest, "UNKNOWN", "UNKNOWN", jobId, "Flink 当前运行状态未知");
                return false;
              }
              if ("RUNNING".equals(current.desiredState())) {
                if (runtime.state() == RuntimeStatus.State.RUNNING) {
                  transition(current, latest, "RUNNING", "RUNNING", jobId, null);
                } else {
                  failExpectedRunning(current, latest,
                      runtime.state() == RuntimeStatus.State.TERMINATED
                          ? "Flink 任务已终止" : "Flink 中未找到期望运行的任务");
                }
                return false;
              }
              if (runtime.state() == RuntimeStatus.State.RUNNING) {
                transition(current, latest, "STOPPING", "STOPPING", jobId, null);
                return true;
              }
              toStopped(current, latest, jobId, "Flink 已确认无活动任务");
              return false;
            });

    if (Boolean.TRUE.equals(stop)) {
      try {
        gateway.stop(jobId);
      } catch (RealtimeEngineException exception) {
        markUnknown(definitionId, deploymentId, jobId, exception.getMessage());
        throw exception;
      }
    }
  }

  private void settleMissing(long definitionId, DeploymentRow deployment, String reason) {
    transactions.executeWithoutResult(
        ignored -> {
          DefinitionRow current = store.lockDefinition(definitionId);
          DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
          if (!sameDeployment(deployment, latest) || StringUtils.hasText(latest.engineJobId())) {
            return;
          }
          if ("RUNNING".equals(current.desiredState())) {
            failExpectedRunning(current, latest, "提交结果不确定，" + reason);
          } else {
            toStopped(current, latest, null, reason + "，已确认停止");
          }
        });
  }

  private void markConflict(long definitionId, DeploymentRow deployment, int count) {
    transactions.executeWithoutResult(
        ignored -> {
          DefinitionRow current = store.lockDefinition(definitionId);
          DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
          if (!sameDeployment(deployment, latest) || StringUtils.hasText(latest.engineJobId())) {
            return;
          }
          String target = "STOPPING".equals(current.observedState()) ? "UNKNOWN" : "CONFLICT";
          String message = "runtime job identity 匹配到 " + count + " 个 Flink Job，拒绝自动绑定";
          transition(current, latest, target, "UNKNOWN", null, message);
        });
  }

  private void failExpectedRunning(
      DefinitionRow current, DeploymentRow deployment, String message) {
    stateMachine.requireTransition(current.observedState(), "FAILED");
    store.markTerminalFailure(current.id(), deployment.id(), message);
    store.event(current.id(), deployment.id(), "FLINK_JOB_LOST", current.observedState(), "FAILED", message);
  }

  private void markUnknown(
      long definitionId, long deploymentId, String jobId, String message) {
    transactions.executeWithoutResult(
        ignored -> {
          DefinitionRow current = store.lockDefinition(definitionId);
          DeploymentRow latest = store.latestDeployment(definitionId).orElse(null);
          if (latest == null || latest.id() != deploymentId) {
            return;
          }
          transition(current, latest, "UNKNOWN", "UNKNOWN", jobId, message);
        });
  }

  private void transition(
      DefinitionRow current, DeploymentRow deployment, String observed, String deploymentState,
      String jobId, String error) {
    if (!observed.equals(current.observedState())) {
      stateMachine.requireTransition(current.observedState(), observed);
    }
    store.reconcile(current.id(), deployment.id(), observed, deploymentState, jobId, error);
    if (!observed.equals(current.observedState()) || !Objects.equals(error, current.lastError())) {
      store.event(current.id(), deployment.id(), "STATE_RECONCILED", current.observedState(), observed,
          error == null ? "Flink 状态已对账" : error);
    }
  }

  private void toStopped(
      DefinitionRow current, DeploymentRow deployment, String jobId, String message) {
    String from = current.observedState();
    if ("STARTING".equals(from) || "RUNNING".equals(from)) {
      stateMachine.requireTransition(from, "STOPPING");
      store.reconcile(current.id(), deployment.id(), "STOPPING", "STOPPING", jobId, null);
      from = "STOPPING";
    }
    if (!"STOPPED".equals(from)) {
      stateMachine.requireTransition(from, "STOPPED");
    }
    store.reconcile(current.id(), deployment.id(), "STOPPED", "STOPPED", jobId, null);
    store.event(current.id(), deployment.id(), "STOPPED", from, "STOPPED", message);
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
    return store.definition(id)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
  }
}
