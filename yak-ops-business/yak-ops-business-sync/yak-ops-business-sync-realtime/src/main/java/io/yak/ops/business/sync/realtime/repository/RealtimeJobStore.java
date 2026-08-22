package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecution.EngineExecutionRef;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Compatibility persistence contract while Task / Version / Execution are migrated independently. */
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
  default Optional<PublishedDefinitionRow> publishedDefinition(long definitionId) {
    return Optional.empty();
  }
  /** Lookup one explicit immutable DefinitionVersion. */
  default Optional<PublishedDefinitionRow> definitionVersion(long taskId, long definitionVersionId) {
    return Optional.empty();
  }
  Optional<DeploymentRow> deploymentByIdempotencyKey(String key);
  Optional<DeploymentRow> latestDeployment(long definitionId);
  default Optional<SyncExecution> latestExecution(long definitionId) {
    return latestDeployment(definitionId).map(DeploymentRow::execution);
  }
  default Optional<ComputeEnvironmentSnapshot> deploymentEnvironment(long deploymentId) {
    return Optional.empty();
  }
  long insertDeployment(DefinitionRow definition, CdcPipelineSpec spec, String summary, String digest, ComputeEnvironmentSnapshot environment, String idempotencyKey);
  default void bindDeploymentDefinitionVersion(
      long deploymentId, long definitionVersionId, int sourceDraftRevision) {
    throw new UnsupportedOperationException("当前 RealtimeJobStore 不支持 DefinitionVersion 绑定");
  }
  void markStarting(long definitionId);
  void markDeploymentRunning(long definitionId, long deploymentId, String engineJobId, String runtimeRevision);
  void bindDeploymentForStop(long deploymentId, String engineJobId, String runtimeRevision);
  void markDeployFailure(long definitionId, long deploymentId, boolean uncertain, boolean stopRequested, String message);
  void markStopping(long definitionId, Long deploymentId);
  void reconcile(long definitionId, Long deploymentId, String observedState, String deploymentState, String engineJobId, String error);
  void markTerminalFailure(long definitionId, Long deploymentId, String message);

  /** Active/uncertain executions that require authoritative runtime reconciliation. */
  default List<DeploymentRow> reconcileCandidates() {
    return List.of();
  }

  /** Legacy compatibility query. New runtime logic must use reconcileCandidates/latestExecution. */
  @Deprecated
  List<DefinitionRow> desiredJobs();

  boolean hasOtherDesiredRunning(long id);
  void delete(long id);
  void event(long definitionId, Long deploymentId, String type, String from, String to, String message);
  boolean tryAcquireReconcileLease(String owner, int leaseSeconds);
  List<RealtimeJobEventView> events(long definitionId);
  RealtimeJobView view(long id);
  default CdcPipelineSpec spec(DefinitionRow definition) { return definition.spec(); }
  RealtimeJobView.Deployment deploymentView(DeploymentRow deployment);

  record PublishedDefinitionRow(
      long id,
      long taskId,
      int versionNo,
      int sourceDraftRevision,
      CdcPipelineSpec spec,
      long runtimeEnvironmentId,
      String sourceConfigDigest,
      String definitionDigest) {
    public PublishedDefinitionRow {
      if (id <= 0) throw new IllegalArgumentException("DefinitionVersionId 必须大于 0");
      if (taskId <= 0) throw new IllegalArgumentException("TaskId 必须大于 0");
      if (versionNo <= 0) throw new IllegalArgumentException("Published versionNo 必须大于 0");
      if (sourceDraftRevision <= 0) {
        throw new IllegalArgumentException("Published sourceDraftRevision 必须大于 0");
      }
      if (spec == null) throw new IllegalArgumentException("Published definition snapshot 不能为空");
      if (runtimeEnvironmentId <= 0) {
        throw new IllegalArgumentException("Published RuntimeEnvironmentRef 必须大于 0");
      }
    }
  }

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
      Long publishedDefinitionVersionId,
      String configDigest,
      String lastError,
      LocalDateTime createTime,
      LocalDateTime updateTime) {

    /** Compatibility constructor for callers created before Wave 5 surfaced the immutable ref. */
    public DefinitionRow(
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
        LocalDateTime updateTime) {
      this(
          id,
          name,
          description,
          spec,
          runtimeEnvironmentId,
          releaseState,
          desiredState,
          observedState,
          definitionVersion,
          publishedVersion,
          null,
          configDigest,
          lastError,
          createTime,
          updateTime);
    }
  }

  /**
   * Persistence/read compatibility row. From Wave 3 onward desiredState/observedState are the
   * authoritative lifecycle fields; status remains a legacy submission/read projection.
   */
  record DeploymentRow(
      long id,
      long definitionId,
      Long definitionVersionId,
      int definitionVersion,
      CdcPipelineSpec specSnapshot,
      String specSummary,
      String configDigest,
      String idempotencyKey,
      String engineJobId,
      String runtimeRevision,
      ComputeEnvironmentSnapshot runtimeEnvironment,
      String engineType,
      String desiredState,
      String observedState,
      String status,
      boolean resultUncertain,
      String errorMessage,
      LocalDateTime createTime,
      LocalDateTime updateTime) {

    public DeploymentRow {
      engineType = hasText(engineType) ? engineType.trim() : "FLINK_CDC";
      desiredState = hasText(desiredState) ? desiredState.trim() : legacyDesiredState(status);
      observedState = hasText(observedState) ? observedState.trim() : legacyObservedState(status);
    }

    public SyncExecution execution() {
      return new SyncExecution(
          id,
          definitionId,
          definitionVersionId,
          DesiredState.valueOf(desiredState),
          ObservedState.valueOf(observedState),
          new EngineExecutionRef(engineType, engineJobId),
          resultUncertain,
          errorMessage);
    }

    /** Compatibility constructor for Wave-2 callers before execution lifecycle columns existed. */
    public DeploymentRow(
        long id,
        long definitionId,
        Long definitionVersionId,
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
        LocalDateTime updateTime) {
      this(
          id,
          definitionId,
          definitionVersionId,
          definitionVersion,
          specSnapshot,
          specSummary,
          configDigest,
          idempotencyKey,
          engineJobId,
          runtimeRevision,
          runtimeEnvironment,
          "FLINK_CDC",
          legacyDesiredState(status),
          legacyObservedState(status),
          status,
          resultUncertain,
          errorMessage,
          createTime,
          updateTime);
    }

    /** Compatibility constructor for callers created before Wave 2 added DefinitionVersionId. */
    public DeploymentRow(
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
        LocalDateTime updateTime) {
      this(
          id,
          definitionId,
          null,
          definitionVersion,
          specSnapshot,
          specSummary,
          configDigest,
          idempotencyKey,
          engineJobId,
          runtimeRevision,
          runtimeEnvironment,
          status,
          resultUncertain,
          errorMessage,
          createTime,
          updateTime);
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }

    private static String legacyDesiredState(String status) {
      return switch (status == null ? "" : status) {
        case "STOPPING", "STOPPED", "FAILED", "REJECTED" -> "STOPPED";
        default -> "RUNNING";
      };
    }

    private static String legacyObservedState(String status) {
      return switch (status == null ? "" : status) {
        case "SUBMITTING" -> "STARTING";
        case "RUNNING" -> "RUNNING";
        case "STOPPING" -> "STOPPING";
        case "STOPPED" -> "STOPPED";
        case "FAILED", "REJECTED" -> "FAILED";
        case "UNKNOWN" -> "UNKNOWN";
        default -> "UNKNOWN";
      };
    }
  }
}
