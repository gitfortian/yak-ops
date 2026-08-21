package io.yak.ops.business.sync.realtime.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically recovers observed state after Yak Ops restarts or uncertain Gateway calls. */
@Component
public class RealtimeJobReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeJobReconciler.class);
  private final RealtimeJobService service;

  public RealtimeJobReconciler(RealtimeJobService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${yak.sync.realtime.reconcile-delay:10000}")
  public void reconcile() {
    try {
      service.reconcile();
    } catch (RuntimeException exception) {
      LOG.warn("Realtime state reconciliation failed", exception);
    }
  }
}
