package io.yak.ops.business.dataservice.domain;

import java.time.LocalDateTime;

/** Immutable business projection of one invocation audit event. */
public record InvocationRecord(
    Long id,
    Long apiId,
    String serviceName,
    String servicePath,
    String callerType,
    Long apiKeyId,
    String apiKeyName,
    String apiKeyPrefix,
    String paramsJson,
    boolean success,
    long durationMs,
    int rowCount,
    String errorMessage,
    LocalDateTime createTime) {}
