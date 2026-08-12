package io.yak.ops.business.taskcatalog.spi;

import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Objects;

/** Source-neutral immutable revision payload returned by a task authoring domain. */
public record TaskSourceRevision(
    long revisionId,
    int revisionNo,
    TaskDefinition definition,
    String checksum) {

  public TaskSourceRevision {
    if (revisionId <= 0L) throw new IllegalArgumentException("revisionId must be positive");
    if (revisionNo <= 0) throw new IllegalArgumentException("revisionNo must be positive");
    definition = Objects.requireNonNull(definition, "definition");
  }
}
