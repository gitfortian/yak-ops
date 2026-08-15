package io.yak.ops.business.dataservice.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.service.DataServiceService.QueryResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Lightweight single-node Runtime resilience layer.
 *
 * <p>It deliberately stays local to the process: bounded result cache, per-service circuit breaker and
 * rolling runtime metrics. The API boundary is intentionally small so these implementations can later
 * be replaced by Redis/gateway/metrics backends without changing Data Service publication semantics.
 */
@Service
public class DataServiceRuntimeService {

  private static final int DEFAULT_CACHE_TTL_SECONDS = 60;
  private static final int DEFAULT_CACHE_MAX_ENTRIES = 200;
  private static final int DEFAULT_FAILURE_THRESHOLD = 5;
  private static final int DEFAULT_RECOVERY_SECONDS = 30;
  private static final int DURATION_SAMPLE_SIZE = 256;

  private final Clock clock;
  private final ConcurrentHashMap<Long, RuntimeState> states = new ConcurrentHashMap<>();

  public DataServiceRuntimeService() {
    this(Clock.systemUTC());
  }

  DataServiceRuntimeService(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public QueryResponse execute(
      DataServiceApiPO api,
      String cacheKey,
      Supplier<QueryResponse> loader) {
    if (api == null || api.getId() == null) throw new IllegalArgumentException("数据服务 Runtime 缺少 API ID");
    RuntimePolicy policy = policy(api);
    RuntimeState state = states.computeIfAbsent(api.getId(), ignored -> new RuntimeState());
    state.ensurePolicy(policy);

    if (policy.cacheEnabled()) {
      QueryResponse cached = state.cache().getIfPresent(cacheKey);
      if (cached != null) {
        state.metrics.recordSuccess(0L, true, clock.instant());
        return copyWithDuration(cached, 0L);
      }
    }

    CircuitDecision decision = state.circuit.beforeCall(policy, clock.instant());
    if (!decision.allowed()) {
      state.metrics.recordCircuitRejected(clock.instant());
      throw new DataServiceCircuitOpenException(
          "数据服务下游暂时不可用，熔断保护中，请在 " + decision.retryAfterSeconds() + " 秒后重试");
    }

    long started = System.nanoTime();
    try {
      QueryResponse response = loader.get();
      long durationMs = elapsedMs(started);
      state.circuit.onSuccess();
      state.metrics.recordSuccess(durationMs, false, clock.instant());
      if (policy.cacheEnabled()) {
        state.cache().put(cacheKey, copyWithDuration(response, durationMs));
      }
      return copyWithDuration(response, durationMs);
    } catch (RuntimeException exception) {
      long durationMs = elapsedMs(started);
      state.circuit.onFailure(policy, clock.instant());
      state.metrics.recordFailure(durationMs, clock.instant());
      throw exception;
    }
  }

  /** Builds a stable cache key from the executable SQL snapshot and ordered JDBC bindings. */
  public String cacheKey(String compiledSql, List<Object> bindings) {
    StringBuilder raw = new StringBuilder(compiledSql == null ? "" : compiledSql).append('\u001f');
    if (bindings != null) {
      for (Object value : bindings) {
        raw.append(value == null ? "<null>" : value.getClass().getName() + ':' + value).append('\u001e');
      }
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成数据服务缓存 Key", exception);
    }
  }

  public RuntimeSnapshot snapshot(DataServiceApiPO api) {
    RuntimePolicy policy = policy(api);
    RuntimeState state = states.computeIfAbsent(api.getId(), ignored -> new RuntimeState());
    state.ensurePolicy(policy);
    return state.snapshot(api.getId(), policy, clock.instant());
  }

  /** Invalidates query cache/circuit state while keeping process-lifetime metrics for observability. */
  public void invalidate(Long apiId) {
    RuntimeState state = apiId == null ? null : states.get(apiId);
    if (state != null) state.invalidateOperationalState();
  }

  public void remove(Long apiId) {
    if (apiId != null) states.remove(apiId);
  }

  private RuntimePolicy policy(DataServiceApiPO api) {
    return new RuntimePolicy(
        Boolean.TRUE.equals(api.getCacheEnabled()),
        positive(api.getCacheTtlSeconds(), DEFAULT_CACHE_TTL_SECONDS),
        positive(api.getCacheMaxEntries(), DEFAULT_CACHE_MAX_ENTRIES),
        Boolean.TRUE.equals(api.getCircuitBreakerEnabled()),
        positive(api.getCircuitFailureThreshold(), DEFAULT_FAILURE_THRESHOLD),
        positive(api.getCircuitRecoverySeconds(), DEFAULT_RECOVERY_SECONDS));
  }

  private int positive(Integer value, int fallback) {
    return value == null || value <= 0 ? fallback : value;
  }

  private QueryResponse copyWithDuration(QueryResponse response, long durationMs) {
    return new QueryResponse(
        response.columns(), response.rows(), response.truncated(), response.rowCount(), durationMs);
  }

  private long elapsedMs(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }

  public record RuntimePolicy(
      boolean cacheEnabled,
      int cacheTtlSeconds,
      int cacheMaxEntries,
      boolean circuitBreakerEnabled,
      int failureThreshold,
      int recoverySeconds) {}

  public record RuntimeSnapshot(
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

  private static final class RuntimeState {

    private volatile RuntimePolicy policy;
    private volatile Cache<String, QueryResponse> cache;
    private final CircuitBreaker circuit = new CircuitBreaker();
    private final RuntimeMetrics metrics = new RuntimeMetrics();

    synchronized void ensurePolicy(RuntimePolicy next) {
      if (Objects.equals(policy, next) && cache != null) return;
      RuntimePolicy previous = policy;
      policy = next;
      if (cache != null) cache.invalidateAll();
      cache = Caffeine.newBuilder()
          .maximumSize(next.cacheMaxEntries())
          .expireAfterWrite(Duration.ofSeconds(next.cacheTtlSeconds()))
          .build();
      if (previous != null
          && (previous.circuitBreakerEnabled() != next.circuitBreakerEnabled()
              || previous.failureThreshold() != next.failureThreshold()
              || previous.recoverySeconds() != next.recoverySeconds())) {
        circuit.reset();
      }
    }

    Cache<String, QueryResponse> cache() {
      return cache;
    }

    synchronized void invalidateOperationalState() {
      if (cache != null) cache.invalidateAll();
      circuit.reset();
    }

    RuntimeSnapshot snapshot(Long apiId, RuntimePolicy policy, Instant now) {
      RuntimeMetrics.MetricSnapshot metric = metrics.snapshot();
      CircuitBreaker.CircuitSnapshot breaker = circuit.snapshot(policy, now);
      long entries = cache == null ? 0L : cache.estimatedSize();
      return new RuntimeSnapshot(
          apiId,
          policy.cacheEnabled(),
          policy.cacheTtlSeconds(),
          policy.cacheMaxEntries(),
          entries,
          policy.circuitBreakerEnabled(),
          policy.failureThreshold(),
          policy.recoverySeconds(),
          breaker.state(),
          breaker.openUntil(),
          metric.totalCalls(),
          metric.successCalls(),
          metric.failureCalls(),
          metric.cacheHits(),
          metric.circuitRejected(),
          metric.successRate(),
          metric.cacheHitRate(),
          metric.averageDurationMs(),
          metric.p95DurationMs(),
          metric.lastSuccessAt(),
          metric.lastFailureAt());
    }
  }

  private static final class CircuitBreaker {

    private int consecutiveFailures;
    private Instant openUntil;
    private boolean halfOpenProbeInFlight;

    synchronized CircuitDecision beforeCall(RuntimePolicy policy, Instant now) {
      if (!policy.circuitBreakerEnabled()) return new CircuitDecision(true, 0L);
      if (openUntil == null) return new CircuitDecision(true, 0L);
      if (now.isBefore(openUntil)) {
        return new CircuitDecision(false, Math.max(1L, Duration.between(now, openUntil).toSeconds()));
      }
      if (halfOpenProbeInFlight) return new CircuitDecision(false, 1L);
      halfOpenProbeInFlight = true;
      return new CircuitDecision(true, 0L);
    }

    synchronized void onSuccess() {
      consecutiveFailures = 0;
      openUntil = null;
      halfOpenProbeInFlight = false;
    }

    synchronized void onFailure(RuntimePolicy policy, Instant now) {
      if (!policy.circuitBreakerEnabled()) return;
      halfOpenProbeInFlight = false;
      consecutiveFailures++;
      if (openUntil != null || consecutiveFailures >= policy.failureThreshold()) {
        openUntil = now.plusSeconds(policy.recoverySeconds());
      }
    }

    synchronized void reset() {
      consecutiveFailures = 0;
      openUntil = null;
      halfOpenProbeInFlight = false;
    }

    synchronized CircuitSnapshot snapshot(RuntimePolicy policy, Instant now) {
      if (!policy.circuitBreakerEnabled()) return new CircuitSnapshot("DISABLED", null);
      if (openUntil == null) return new CircuitSnapshot("CLOSED", null);
      if (now.isBefore(openUntil)) return new CircuitSnapshot("OPEN", openUntil);
      return new CircuitSnapshot("HALF_OPEN", openUntil);
    }

    record CircuitSnapshot(String state, Instant openUntil) {}
  }

  private static final class RuntimeMetrics {

    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong successCalls = new AtomicLong();
    private final AtomicLong failureCalls = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong circuitRejected = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final ArrayDeque<Long> durations = new ArrayDeque<>();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;

    void recordSuccess(long durationMs, boolean cacheHit, Instant now) {
      totalCalls.incrementAndGet();
      successCalls.incrementAndGet();
      if (cacheHit) cacheHits.incrementAndGet();
      totalDurationMs.addAndGet(Math.max(0L, durationMs));
      lastSuccessAt = now;
      addDuration(durationMs);
    }

    void recordFailure(long durationMs, Instant now) {
      totalCalls.incrementAndGet();
      failureCalls.incrementAndGet();
      totalDurationMs.addAndGet(Math.max(0L, durationMs));
      lastFailureAt = now;
      addDuration(durationMs);
    }

    void recordCircuitRejected(Instant now) {
      totalCalls.incrementAndGet();
      failureCalls.incrementAndGet();
      circuitRejected.incrementAndGet();
      lastFailureAt = now;
      addDuration(0L);
    }

    private synchronized void addDuration(long durationMs) {
      if (durations.size() >= DURATION_SAMPLE_SIZE) durations.removeFirst();
      durations.addLast(Math.max(0L, durationMs));
    }

    synchronized MetricSnapshot snapshot() {
      long total = totalCalls.get();
      long success = successCalls.get();
      long failures = failureCalls.get();
      long hits = cacheHits.get();
      List<Long> sorted = new ArrayList<>(durations);
      sorted.sort(Long::compareTo);
      long p95 = sorted.isEmpty()
          ? 0L
          : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
      long average = total == 0 ? 0L : totalDurationMs.get() / total;
      double successRate = total == 0 ? 1D : (double) success / total;
      double cacheHitRate = total == 0 ? 0D : (double) hits / total;
      return new MetricSnapshot(
          total,
          success,
          failures,
          hits,
          circuitRejected.get(),
          successRate,
          cacheHitRate,
          average,
          p95,
          lastSuccessAt,
          lastFailureAt);
    }

    record MetricSnapshot(
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
  }

  private record CircuitDecision(boolean allowed, long retryAfterSeconds) {}
}
