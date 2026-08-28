package io.yak.ops.business.dataservice.repository;

import java.time.LocalDateTime;
import java.util.List;

/** Cluster-wide invocation metric projection built from durable audit evidence and rollups. */
public interface DataServiceRuntimeMetricsRepository {

  Metrics load(Long apiId, int durationSampleSize);

  record Metrics(
      long totalCalls,
      long successCalls,
      long failureCalls,
      long totalDurationMs,
      List<Long> recentDurationsMs,
      LocalDateTime lastSuccessAt,
      LocalDateTime lastFailureAt) {}
}
