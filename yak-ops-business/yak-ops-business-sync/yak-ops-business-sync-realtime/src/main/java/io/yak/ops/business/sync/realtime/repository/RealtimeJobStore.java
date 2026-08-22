package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Domain-facing repository contract for realtime definitions, deployments and lifecycle evidence. */
public interface RealtimeJobStore {
  long insertDefinition(String name, String description, CdcPipelineSpec spec, String digest, long runtimeEnvironmentId);
  void updateDefinition(long id, String name, String description, CdcPipelineSpec spec, String digest, long runtimeEnvironmentId);
  void publish(long id, int expectedDefinitionVersion, String expectedDigest);
  Optional<DefinitionRow> definition(long id);
  default long runtimeEnvironmentId(long definitionId) {
    return definition(definitionId)
        .map(DefinitionRow::runtimeEnvironmentId)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + definitionId));
  }
  DefinitionRow lockDefinition(long id);
  RealtimeJobPage page(int pageNo, int pageSize, String keyword);
  Optional<DeploymentRow> deploymentByIdempotencyKey(String key);
  Optional<DeploymentRow> latestDeployment(long definitionId);
  default Optional<ComputeEnvironmentSnapshot> deploymentEnvironment(long deploymentId) {
    return Optional.empty();
  }
  long insertDeployment(DefinitionRow definition, CdcPipelineSpec spec, String summary, String digest, ComputeEnvironmentSnapshot environment, String idempotencyKey);
  void markStarting(long definitionId);
  void markDeploymentRunning(long definitionId, long deploymentId, String engineJobId, String runtimeRevision);
  void bindDeploymentForStop(long deploymentId, String engineJobId, String runtimeRevision);
  void markDeployFailure(long definitionId, long deploymentId, boolean uncertain, boolean stopRequested, String message);
  void markStopping(long definitionId, Long deploymentId);
  void reconcile(long definitionId, Long deploymentId, String observedState, String deploymentState, String engineJobId, String error);
  void markTerminalFailure(long definitionId, Long deploymentId, String message);
  List<DefinitionRow> desiredJobs();
  boolean hasOtherDesiredRunning(long id);
  void delete(long id);
  void event(long definitionId, Long deploymentId, String type, String from, String to, String message);
  boolean tryAcquireReconcileLease(String owner, int leaseSeconds);
  List<RealtimeJobEventView> events(long definitionId);
  RealtimeJobView view(long id);
  default CdcPipelineSpec spec(DefinitionRow definition) { return definition.spec(); }
  RealtimeJobView.Deployment deploymentView(DeploymentRow deployment);

  record DefinitionRow(
      long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      long runtimeEnvironmentId,
      String releaseState,
      String desiredState,
      String observedState,
      int definitionVersion,
      Integer publishedVersion,
      String configDigest,
      String lastError,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  record DeploymentRow(
      long id,
      long definitionId,
      int definitionVersion,
      CdcPipelineSpec specSnapshot,
      String specSummary,
      String configDigest,
      String idempotencyKey,
      String engineJobId,
      String runtimeRevision,
      ComputeEnvironmentSnapshot runtimeEnvironment,
      String status,
      boolean resultUncertain,
      String errorMessage,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}
}
