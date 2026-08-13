package io.yak.ops.business.development.domain;

import java.time.LocalDateTime;

/** Lightweight row used by the data-development execution history page. */
public record DevelopmentTaskExecutionSummary(
    Long id,
    Long nodeId,
    String taskName,
    String taskType,
    String triggerType,
    String runtimeExecutionId,
    String status,
    String operatorName,
    Long durationMs,
    String errorMessage,
    LocalDateTime startTime,
    LocalDateTime endTime) {}
