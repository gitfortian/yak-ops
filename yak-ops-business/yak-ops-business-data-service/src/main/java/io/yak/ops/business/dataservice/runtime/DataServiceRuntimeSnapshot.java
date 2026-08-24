package io.yak.ops.business.dataservice.runtime;

import java.time.Instant;

/** Process-local runtime evidence exposed for observability. */
public record DataServiceRuntimeSnapshot(
    Long apiId,
    boolean cacheEnabled,
    int cacheTtlSeconds,
    int cacheMaxEntries,
    long cacheEntries,
    boolean circuitBreakerEnabled,
    int failureThreshold,
    int recoverySeconds,
    String circuitState,
    Instant circuitOpenUntil,
    long totalCalls,
    long successCalls,
    long failureCalls,
    long cacheHits,
    long circuitRejected,
    double successRate,
    double cacheHitRate,
    long averageDurationMs,
    long p95DurationMs,
    Instant lastSuccessAt,
    Instant lastFailureAt) {}
