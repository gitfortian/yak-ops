package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically recovers observed state after Yak Ops restarts or uncertain Gateway calls. */
@Component
public class RealtimeJobReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeJobReconciler.class);
  private final RealtimeJobService service;
  private final RealtimeJobStore store;
  private final RealtimeSyncProperties properties;
  private final String leaseOwner = UUID.randomUUID().toString();

  public RealtimeJobReconciler(
      RealtimeJobService service, RealtimeJobStore store, RealtimeSyncProperties properties) {
    this.service = service;
    this.store = store;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${yak.sync.realtime.reconcile-delay:10000}")
  public void reconcile() {
    try {
      if (!store.tryAcquireReconcileLease(leaseOwner, properties.getReconcileLeaseSeconds())) {
        return;
      }
      service.reconcile();
    } catch (RuntimeException exception) {
      LOG.warn("Realtime state reconciliation failed", exception);
    }
  }
}
