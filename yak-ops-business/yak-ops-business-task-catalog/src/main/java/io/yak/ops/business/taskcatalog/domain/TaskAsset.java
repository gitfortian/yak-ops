package io.yak.ops.business.taskcatalog.domain;

import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.Objects;

/** Published task asset discoverable by orchestration surfaces. */
public record TaskAsset(
    long id,
    TaskAssetSource source,
    String sourceRef,
    Long projectId,
    String name,
    String taskType,
    TaskAssetStatus status,
    TaskRevisionRef currentRevision,
    Instant createTime,
    Instant updateTime) {

  public TaskAsset {
    if (id <= 0L) throw new IllegalArgumentException("Task asset id must be positive");
    source = Objects.requireNonNull(source, "source");
    if (sourceRef == null || sourceRef.isBlank()) {
      throw new IllegalArgumentException("Task asset sourceRef must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Task asset name must not be blank");
    }
    if (taskType == null || taskType.isBlank()) {
      throw new IllegalArgumentException("Task asset taskType must not be blank");
    }
    status = Objects.requireNonNull(status, "status");
    currentRevision = Objects.requireNonNull(currentRevision, "currentRevision");
  }
}
