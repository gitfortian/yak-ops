package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Adds a durable deterministic Flink job name before entering the submission boundary. */
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
  public JsonNode health(ComputeEnvironmentSnapshot environment) {
    return delegate.health(environment);
  }

  @Override
  public JsonNode capabilities(ComputeEnvironmentSnapshot environment) {
    return delegate.capabilities(environment);
  }

  @Override
  public ValidationResult validate(ComputeEnvironmentSnapshot environment, String pipelineYaml) {
    return delegate.validate(environment, pipelineYaml);
  }

  @Override
  public DeployResult deploy(
      ComputeEnvironmentSnapshot environment, RealtimeDeployRequest request) {
    String runtimeJobName = RealtimeRuntimeIdentity.jobName(request.idempotencyKey());
    String recoverableYaml =
        RealtimeRuntimeIdentity.decoratePipeline(request.pipelineYaml(), request.idempotencyKey());

    identityStore.bind(request.idempotencyKey(), runtimeJobName);
    RealtimeDeployRequest recoverable =
        new RealtimeDeployRequest(
            recoverableYaml,
            request.idempotencyKey(),
            request.source(),
            request.sink());
    return delegate.deploy(environment, recoverable);
  }

  @Override
  public RuntimeStatus status(ComputeEnvironmentSnapshot environment, String jobId) {
    return delegate.status(environment, jobId);
  }

  @Override
  public void stop(ComputeEnvironmentSnapshot environment, String jobId) {
    delegate.stop(environment, jobId);
  }
}
