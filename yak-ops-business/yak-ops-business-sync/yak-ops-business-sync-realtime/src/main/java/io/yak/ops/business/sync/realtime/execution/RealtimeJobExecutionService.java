package io.yak.ops.business.sync.realtime.execution;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.service.RealtimeJobLifecycleCoordinator;
import io.yak.ops.business.sync.realtime.service.RealtimeJobService;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime execution lifecycle commands. */
@Service("realtimeJobExecutionApplicationService")
public class RealtimeJobExecutionService {

  private final RealtimeJobService jobs;
  private final RealtimeJobLifecycleCoordinator lifecycle;

  public RealtimeJobExecutionService(
      RealtimeJobService jobs, RealtimeJobLifecycleCoordinator lifecycle) {
    this.jobs = jobs;
    this.lifecycle = lifecycle;
  }

  public RealtimeJobView.Deployment start(long id, String idempotencyKey) {
    return jobs.start(id, idempotencyKey);
  }

  public void stop(long id) {
    jobs.stop(id);
  }

  public RealtimeJobView.Deployment restartExecution(long id, String idempotencyKey) {
    return jobs.restartExecution(id, idempotencyKey);
  }

  public RealtimeJobView.Deployment applyPublishedVersion(long id, String idempotencyKey) {
    return jobs.applyPublishedVersion(id, idempotencyKey);
  }

  public RealtimeJobView reconcile(long id) {
    return lifecycle.reconcile(id);
  }

  public void assertSafeToDelete(long id) {
    lifecycle.assertSafeToDelete(id);
  }

  public JsonNode capabilities(long runtimeEnvironmentId) {
    return jobs.capabilities(runtimeEnvironmentId);
  }
}
