package io.yak.ops.business.development.domain;

import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.util.Map;
import java.util.Objects;

/** Synchronous manual-run response; durable TaskExecution persistence is introduced later. */
public record DevelopmentTaskRunResult(
    TaskExecutionStatus status,
    String message,
    long durationMs,
    Map<String, Object> output) {

  public DevelopmentTaskRunResult {
    status = Objects.requireNonNull(status, "status");
    message = message == null ? "" : message;
    durationMs = Math.max(0L, durationMs);
    output = output == null ? Map.of() : Map.copyOf(output);
  }
}
