package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.FlinkJobDiscoveryClient;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeDeleteSafetyChecker;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeReconcileCoordinator;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeRuntimeIdentityRecovery;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeRuntimeStateReconciler;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import org.springframework.transaction.PlatformTransactionManager;

/** Test-scope source-compatible adapter that executes the real decomposed Reconcile Core. */
final class RealtimeJobLifecycleCoordinator {

  private final RealtimeReconcileCoordinator coordinator;
  private final RealtimeDeleteSafetyChecker deleteSafety;

  RealtimeJobLifecycleCoordinator(
      RealtimeJobStore store,
      RealtimeRuntimeIdentityStore identityStore,
      FlinkJobDiscoveryClient discovery,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      SyncExecutionStateMachine stateMachine,
      RealtimeSyncProperties properties,
      long orphanGraceSeconds,
      PlatformTransactionManager transactionManager) {
    RealtimeRuntimeStateReconciler states =
        new RealtimeRuntimeStateReconciler(store, gateway, stateMachine, transactionManager);
    RealtimeRuntimeIdentityRecovery recovery =
        new RealtimeRuntimeIdentityRecovery(
            store,
            identityStore,
            discovery,
            runtimeResolver,
            states,
            orphanGraceSeconds,
            transactionManager);
    this.coordinator =
        new RealtimeReconcileCoordinator(
            store, gateway, runtimeResolver, recovery, states, properties);
    this.deleteSafety =
        new RealtimeDeleteSafetyChecker(
            store, identityStore, discovery, gateway, runtimeResolver, stateMachine);
  }

  void reconcileAll() {
    coordinator.reconcileAll();
  }

  RealtimeJobView reconcile(long taskId) {
    return coordinator.reconcile(taskId);
  }

  void assertSafeToDelete(long taskId) {
    deleteSafety.assertSafeToDelete(taskId);
  }
}
