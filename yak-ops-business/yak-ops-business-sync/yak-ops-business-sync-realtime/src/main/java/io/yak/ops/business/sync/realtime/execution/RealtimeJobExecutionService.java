package io.yak.ops.business.sync.realtime.execution;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.service.RealtimeJobLifecycleCoordinator;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime execution lifecycle commands. */
@Service("realtimeJobExecutionApplicationService")
public class RealtimeJobExecutionService {

  private final RealtimeExecutionCoordinator executions;
  private final RealtimeExecutionPreparation preparation;
  private final RealtimeJobLifecycleCoordinator lifecycle;

  public RealtimeJobExecutionService(
      RealtimeExecutionCoordinator executions,
      RealtimeExecutionPreparation preparation,
      RealtimeJobLifecycleCoordinator lifecycle) {
    this.executions = executions;
    this.preparation = preparation;
    this.lifecycle = lifecycle;
  }

  public RealtimeJobView.Deployment start(long id, String idempotencyKey) {
    return executions.start(id, idempotencyKey);
  }

  public void stop(long id) {
    executions.stop(id);
  }

  public RealtimeJobView.Deployment restartExecution(long id, String idempotencyKey) {
    return executions.restartExecution(id, idempotencyKey);
  }

  public RealtimeJobView.Deployment applyPublishedVersion(long id, String idempotencyKey) {
    return executions.applyPublishedVersion(id, idempotencyKey);
  }

  public RealtimeJobView reconcile(long id) {
    return lifecycle.reconcile(id);
  }

  public void assertSafeToDelete(long id) {
    lifecycle.assertSafeToDelete(id);
  }

  public JsonNode capabilities(long runtimeEnvironmentId) {
    return preparation.capabilities(runtimeEnvironmentId);
  }
}
