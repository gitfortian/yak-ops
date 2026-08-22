package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically recovers JobIds and reconciles Yak Ops state against the real Flink runtime. */
@Component
public class RealtimeJobReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeJobReconciler.class);
  private final RealtimeJobLifecycleCoordinator lifecycleCoordinator;
  private final RealtimeJobStore store;
  private final RealtimeSyncProperties properties;
  private final String leaseOwner = UUID.randomUUID().toString();

  public RealtimeJobReconciler(
      RealtimeJobLifecycleCoordinator lifecycleCoordinator,
      RealtimeJobStore store,
      RealtimeSyncProperties properties) {
    this.lifecycleCoordinator = lifecycleCoordinator;
    this.store = store;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${yak.sync.realtime.reconcile-delay:10000}")
  public void reconcile() {
    try {
      if (!store.tryAcquireReconcileLease(leaseOwner, properties.getReconcileLeaseSeconds())) {
        return;
      }
      lifecycleCoordinator.reconcileAll();
    } catch (RuntimeException exception) {
      LOG.warn("Realtime state reconciliation failed", exception);
    }
  }
}
