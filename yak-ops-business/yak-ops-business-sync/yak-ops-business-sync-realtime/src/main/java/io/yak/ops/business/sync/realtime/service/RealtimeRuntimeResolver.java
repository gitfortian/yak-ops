package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import org.springframework.stereotype.Service;

/** Resolves the mutable task binding and the immutable deployment runtime snapshot. */
@Service
public class RealtimeRuntimeResolver {

  private final ComputeEnvironmentStore environments;
  private final RealtimeJobStore jobs;

  public RealtimeRuntimeResolver(ComputeEnvironmentStore environments, RealtimeJobStore jobs) {
    this.environments = environments;
    this.jobs = jobs;
  }

  public long defaultEnvironmentId() {
    return environments
        .defaultEnvironment()
        .orElseThrow(() -> new IllegalStateException("请先在设置 → 计算引擎中配置默认运行环境"))
        .id();
  }

  public ComputeEnvironmentSnapshot environment(long environmentId, boolean requireEnabled) {
    ComputeEnvironment environment =
        environments
            .find(environmentId)
            .orElseThrow(() -> new IllegalArgumentException("运行环境不存在：" + environmentId));
    if (requireEnabled && !environment.enabled()) {
      throw new IllegalStateException("运行环境已停用，请切换到已启用的运行环境：" + environment.name());
    }
    return ComputeEnvironmentSnapshot.from(environment);
  }

  public ComputeEnvironmentSnapshot definition(DefinitionRow definition, boolean requireEnabled) {
    Long environmentId = jobs.runtimeEnvironmentId(definition.id());
    if (environmentId == null) {
      environmentId = defaultEnvironmentId();
    }
    return environment(environmentId, requireEnabled);
  }

  public ComputeEnvironmentSnapshot deployment(
      DefinitionRow definition, DeploymentRow deployment) {
    ComputeEnvironmentSnapshot snapshot = jobs.deploymentEnvironment(deployment.id()).orElse(null);
    if (snapshot != null) {
      return snapshot;
    }
    // Compatibility fallback for deployments created before V7. New deployments always snapshot.
    return definition(definition, false);
  }
}
