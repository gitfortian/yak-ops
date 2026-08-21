package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;

/** Secret-free realtime job response model. */
public record RealtimeJobView(
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

  public record Deployment(
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
      LocalDateTime updateTime) {}
}
