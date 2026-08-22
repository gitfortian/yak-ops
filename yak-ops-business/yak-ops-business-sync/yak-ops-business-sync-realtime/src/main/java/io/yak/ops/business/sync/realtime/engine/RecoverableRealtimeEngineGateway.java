package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Adds a deterministic Flink job name before submission so Yak Ops can recover the JobId after an
 * uncertain CLI result or a process crash between submit and persistence.
 */
@Primary
@Component
public class RecoverableRealtimeEngineGateway implements RealtimeEngineGateway {

  private final FlinkCdcEngineGateway delegate;
  private final RealtimeRuntimeIdentityStore identityStore;

  public RecoverableRealtimeEngineGateway(
      FlinkCdcEngineGateway delegate, RealtimeRuntimeIdentityStore identityStore) {
    this.delegate = delegate;
    this.identityStore = identityStore;
  }

  @Override
  public JsonNode health() {
    return delegate.health();
  }

  @Override
  public JsonNode capabilities() {
    return delegate.capabilities();
  }

  @Override
  public ValidationResult validate(String pipelineYaml) {
    return delegate.validate(pipelineYaml);
  }

  @Override
  public DeployResult deploy(RealtimeDeployRequest request) {
    String runtimeJobName = RealtimeRuntimeIdentity.jobName(request.idempotencyKey());
    String recoverableYaml =
        RealtimeRuntimeIdentity.decoratePipeline(request.pipelineYaml(), request.idempotencyKey());

    // This is the last durable step before entering the CLI. REQUIRED means the CLI never started;
    // BOUND means an orphan Flink job is possible and exact-name recovery must be attempted.
    identityStore.bind(request.idempotencyKey(), runtimeJobName);
    RealtimeDeployRequest recoverable =
        new RealtimeDeployRequest(
            recoverableYaml,
            request.idempotencyKey(),
            request.source(),
            request.sink());
    return delegate.deploy(recoverable);
  }

  @Override
  public RuntimeStatus status(String jobId) {
    return delegate.status(jobId);
  }

  @Override
  public void stop(String jobId) {
    delegate.stop(jobId);
  }

  @Override
  public String logs(String jobId, int tailLines) {
    return delegate.logs(jobId, tailLines);
  }

  @Override
  public JsonNode checkpoints(String jobId) {
    return delegate.checkpoints(jobId);
  }

  @Override
  public JsonNode metrics(String jobId) {
    return delegate.metrics(jobId);
  }
}
