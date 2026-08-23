package io.yak.ops.business.sync.realtime.reconcile;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Orchestrates manual and batch reconciliation without owning the individual state transitions. */
@Component
public class RealtimeReconcileCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeReconcileCoordinator.class);

  private final RealtimeJobStore store;
  private final RealtimeEngineGateway gateway;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final RealtimeRuntimeIdentityRecovery identityRecovery;
  private final RealtimeRuntimeStateReconciler stateReconciler;
  private final RealtimeSyncProperties properties;
  private final ConcurrentHashMap<Long, AtomicInteger> consecutiveEngineFailures =
      new ConcurrentHashMap<>();

  public RealtimeReconcileCoordinator(
      RealtimeJobStore store,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      RealtimeRuntimeIdentityRecovery identityRecovery,
      RealtimeRuntimeStateReconciler stateReconciler,
      RealtimeSyncProperties properties) {
    this.store = store;
    this.gateway = gateway;
    this.runtimeResolver = runtimeResolver;
    this.identityRecovery = identityRecovery;
    this.stateReconciler = stateReconciler;
    this.properties = properties;
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
          stateReconciler.markEngineUnavailable(taskId, exception.getMessage());
        }
      } catch (RuntimeException exception) {
        LOG.warn("Realtime execution for task {} reconciliation failed", taskId, exception);
      }
    }
  }

  public RealtimeJobView reconcile(long taskId) {
    DefinitionRow definition = requireDefinition(taskId);
    DeploymentRow deployment = store.latestDeployment(taskId).orElse(null);
    if (deployment == null) {
      return store.view(taskId);
    }

    String jobId = deployment.engineJobId();
    if (!hasText(jobId)) {
      jobId = identityRecovery.recoverJobId(definition, deployment);
      if (!hasText(jobId)) {
        return store.view(taskId);
      }
      deployment = store.latestDeployment(taskId).orElse(deployment);
    }

    ComputeEnvironmentSnapshot runtimeEnvironment =
        runtimeResolver.deployment(definition, deployment);
    RuntimeStatus runtime = gateway.status(runtimeEnvironment, jobId);
    stateReconciler.applyRuntimeState(
        taskId, deployment.id(), jobId, runtimeEnvironment, runtime);
    return store.view(taskId);
  }

  private DefinitionRow requireDefinition(long taskId) {
    return store
        .definition(taskId)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + taskId));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
