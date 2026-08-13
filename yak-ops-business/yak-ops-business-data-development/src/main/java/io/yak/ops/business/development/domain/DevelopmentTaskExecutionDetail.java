package io.yak.ops.business.development.domain;

import java.time.LocalDateTime;
import java.util.Map;

/** Full execution snapshot used by the run-history detail drawer. */
public record DevelopmentTaskExecutionDetail(
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
    String content,
    String configJson,
    Map<String, Object> output,
    LocalDateTime startTime,
    LocalDateTime endTime) {}
