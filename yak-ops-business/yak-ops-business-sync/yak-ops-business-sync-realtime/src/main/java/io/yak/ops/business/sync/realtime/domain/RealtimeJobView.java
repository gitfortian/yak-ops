package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;

/** Secret-free realtime job response model. */
public record RealtimeJobView(
    long id,
    String name,
    String description,
    CdcPipelineSpec spec,
    Long runtimeEnvironmentId,
    String releaseState,
    String desiredState,
    String observedState,
    int definitionVersion,
    Integer publishedVersion,
    String configDigest,
    String lastError,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    Deployment latestDeployment) {

  /** Compatibility constructor for tests/integrations compiled before runtime binding was added. */
  public RealtimeJobView(
      long id,
      String name,
      String description,
      CdcPipelineSpec spec,
      String releaseState,
      String desiredState,
      String observedState,
      int definitionVersion,
      Integer publishedVersion,
      String configDigest,
      String lastError,
      LocalDateTime createTime,
      LocalDateTime updateTime,
      Deployment latestDeployment) {
    this(
        id,
        name,
        description,
        spec,
        null,
        releaseState,
        desiredState,
        observedState,
        definitionVersion,
        publishedVersion,
        configDigest,
        lastError,
        createTime,
        updateTime,
        latestDeployment);
  }

  public record Deployment(
      long id,
      int definitionVersion,
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

    /** Compatibility constructor for deployment rows created before environment snapshots existed. */
    public Deployment(
        long id,
        int definitionVersion,
        String specSummary,
        String configDigest,
        String idempotencyKey,
        String engineJobId,
        String runtimeRevision,
        String status,
        boolean resultUncertain,
        String errorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
      this(
          id,
          definitionVersion,
          specSummary,
          configDigest,
          idempotencyKey,
          engineJobId,
          runtimeRevision,
          null,
          status,
          resultUncertain,
          errorMessage,
          createTime,
          updateTime);
    }
  }
}
