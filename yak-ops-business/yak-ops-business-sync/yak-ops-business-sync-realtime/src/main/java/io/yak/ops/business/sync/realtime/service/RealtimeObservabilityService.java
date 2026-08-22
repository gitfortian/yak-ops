package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.RuntimeLog;
import io.yak.ops.business.sync.realtime.engine.FlinkObservabilityClient;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Application facade for read-only realtime sync observability. */
@Service
public class RealtimeObservabilityService {

  private final RealtimeJobStore store;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final FlinkObservabilityClient flink;

  public RealtimeObservabilityService(
      RealtimeJobStore store,
      RealtimeRuntimeResolver runtimeResolver,
      FlinkObservabilityClient flink) {
    this.store = store;
    this.runtimeResolver = runtimeResolver;
    this.flink = flink;
  }

  public RealtimeObservabilityView snapshot(long definitionId) {
    DeploymentTarget target = requireTarget(definitionId, true);
    return flink.snapshot(target.environment(), target.deployment().engineJobId());
  }

  public String submissionLog(long definitionId, int tailLines) {
    DeploymentRow deployment = requireTarget(definitionId, false).deployment();
    return flink.submissionLog(deployment.idempotencyKey(), tailLines);
  }

  public RuntimeLog runtimeLog(long definitionId, int maxExceptions) {
    DeploymentTarget target = requireTarget(definitionId, true);
    return flink.runtimeLog(
        target.environment(), target.deployment().engineJobId(), maxExceptions);
  }

  private DeploymentTarget requireTarget(long definitionId, boolean requireJobId) {
    DefinitionRow definition =
        store
            .definition(definitionId)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + definitionId));
    DeploymentRow deployment =
        store
            .latestDeployment(definitionId)
            .orElseThrow(() -> new IllegalStateException("任务尚无部署记录"));
    if (requireJobId && !StringUtils.hasText(deployment.engineJobId())) {
      throw new IllegalStateException("部署记录尚无 Flink jobId，请先执行状态对账");
    }
    return new DeploymentTarget(
        deployment, runtimeResolver.deployment(definition, deployment));
  }

  private record DeploymentTarget(
      DeploymentRow deployment, ComputeEnvironmentSnapshot environment) {}
}
