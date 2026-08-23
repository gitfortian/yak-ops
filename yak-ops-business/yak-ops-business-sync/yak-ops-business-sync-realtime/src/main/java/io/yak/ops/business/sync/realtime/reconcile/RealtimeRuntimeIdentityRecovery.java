package io.yak.ops.business.sync.realtime.reconcile;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.engine.FlinkJobDiscoveryClient;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Recovers a missing Flink JobId only from the deterministic persisted runtime identity. */
@Component
public class RealtimeRuntimeIdentityRecovery {

  private final RealtimeJobStore store;
  private final RealtimeRuntimeIdentityStore identityStore;
  private final FlinkJobDiscoveryClient discovery;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final RealtimeRuntimeStateReconciler stateReconciler;
  private final TransactionTemplate transactions;
  private final long orphanGraceSeconds;

  public RealtimeRuntimeIdentityRecovery(
      RealtimeJobStore store,
      RealtimeRuntimeIdentityStore identityStore,
      FlinkJobDiscoveryClient discovery,
      RealtimeRuntimeResolver runtimeResolver,
      RealtimeRuntimeStateReconciler stateReconciler,
      @Value("${yak.sync.realtime.orphan-recovery-grace-seconds:120}") long orphanGraceSeconds,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.identityStore = identityStore;
    this.discovery = discovery;
    this.runtimeResolver = runtimeResolver;
    this.stateReconciler = stateReconciler;
    this.orphanGraceSeconds = Math.max(10, orphanGraceSeconds);
    this.transactions = new TransactionTemplate(transactionManager);
  }

  String recoverJobId(DefinitionRow definition, DeploymentRow deployment) {
    String runtimeName = identityStore.findByDeploymentId(deployment.id()).orElse(null);
    if (!hasText(runtimeName)) {
      if (graceExpired(deployment)) {
        stateReconciler.settleMissing(
            definition.id(), deployment, "Gateway 尚未绑定 runtime identity，确认 CLI 未开始提交");
      }
      return null;
    }

    ComputeEnvironmentSnapshot runtimeEnvironment =
        runtimeResolver.deployment(definition, deployment);
    List<String> matches = discovery.findJobIds(runtimeEnvironment, runtimeName);
    if (matches.size() > 1) {
      stateReconciler.markConflict(definition.id(), deployment, matches.size());
      return null;
    }
    if (matches.isEmpty()) {
      if (graceExpired(deployment)) {
        stateReconciler.settleMissing(
            definition.id(), deployment, "恢复窗口内未发现匹配的 Flink runtime job");
      }
      return null;
    }

    String recoveredJobId = matches.get(0);
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(definition.id());
          DeploymentRow latest = store.latestDeployment(definition.id()).orElse(null);
          if (!sameDeployment(deployment, latest) || hasText(latest.engineJobId())) {
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

  private boolean graceExpired(DeploymentRow deployment) {
    return deployment.createTime() != null
        && deployment.createTime().isBefore(LocalDateTime.now().minusSeconds(orphanGraceSeconds));
  }

  private boolean sameDeployment(DeploymentRow expected, DeploymentRow current) {
    return expected != null && current != null && expected.id() == current.id();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
