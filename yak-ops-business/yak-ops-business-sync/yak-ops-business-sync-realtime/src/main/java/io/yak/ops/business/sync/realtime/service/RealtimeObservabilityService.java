package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.RuntimeLog;
import io.yak.ops.business.sync.realtime.engine.FlinkObservabilityClient;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Application facade for read-only realtime sync observability. */
@Service
public class RealtimeObservabilityService {

  private final RealtimeJobStore store;
  private final FlinkObservabilityClient flink;

  public RealtimeObservabilityService(RealtimeJobStore store, FlinkObservabilityClient flink) {
    this.store = store;
    this.flink = flink;
  }

  public RealtimeObservabilityView snapshot(long definitionId) {
    return flink.snapshot(requireJobId(definitionId));
  }

  public String submissionLog(long definitionId, int tailLines) {
    return flink.submissionLog(requireJobId(definitionId), tailLines);
  }

  public RuntimeLog runtimeLog(long definitionId, int maxExceptions) {
    return flink.runtimeLog(requireJobId(definitionId), maxExceptions);
  }

  private String requireJobId(long definitionId) {
    store
        .definition(definitionId)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + definitionId));
    DeploymentRow deployment =
        store
            .latestDeployment(definitionId)
            .orElseThrow(() -> new IllegalStateException("任务尚无部署记录"));
    if (!StringUtils.hasText(deployment.engineJobId())) {
      throw new IllegalStateException("部署记录尚无 Flink jobId，请先执行状态对账");
    }
    return deployment.engineJobId();
  }
}
