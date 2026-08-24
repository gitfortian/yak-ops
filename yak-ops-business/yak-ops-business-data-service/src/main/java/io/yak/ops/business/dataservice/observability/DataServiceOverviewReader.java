package io.yak.ops.business.dataservice.observability;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceOverviewReader {
  private static final int HOT_API_LIMIT = 8;
  private static final int FAILURE_LIMIT = 8;
  private final DataServiceReader dataServiceReader;
  private final DataServiceCallLogRepository callLogRepository;

  public Overview overview(String range) { return overviewAt(range, LocalDateTime.now()); }

  Overview overviewAt(String range, LocalDateTime now) {
    RangeWindow window = RangeWindow.of(range, now);
    List<DataServiceDefinition> apis = dataServiceReader.list();
    List<InvocationRecord> logs = callLogRepository.between(window.startTime(), now);
    long runningApis = apis.stream().filter(api -> api.settings().enabled()).count();
    long totalCalls = logs.size();
    long successCalls = 0, totalDuration = 0, totalRows = 0;
    List<MutableTrendPoint> trend = createTrend(window);
    Map<Long, MutableApiStats> apiStats = new LinkedHashMap<>();
    for (InvocationRecord log : logs) {
      if (log.success()) successCalls++;
      totalDuration += Math.max(0L, log.durationMs());
      totalRows += Math.max(0, log.rowCount());
      int bucketIndex = window.bucketIndex(log.createTime());
      if (bucketIndex >= 0 && bucketIndex < trend.size()) trend.get(bucketIndex).accept(log.success(), log.durationMs());
      if (log.apiId() != null) apiStats.computeIfAbsent(log.apiId(), ignored -> new MutableApiStats(log)).accept(log);
    }
    Map<Long, DataServiceDefinition> apiById = new LinkedHashMap<>();
    for (DataServiceDefinition api : apis) if (api.id() != null) apiById.put(api.id(), api);
    List<HotApi> hotApis = apiStats.entrySet().stream()
        .sorted((left, right) -> Long.compare(right.getValue().calls, left.getValue().calls))
        .limit(HOT_API_LIMIT).map(entry -> entry.getValue().toHotApi(entry.getKey(), apiById.get(entry.getKey()))).toList();
    List<FailureItem> failures = logs.stream().filter(log -> !log.success())
        .sorted(Comparator.comparing(InvocationRecord::createTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .limit(FAILURE_LIMIT).map(FailureItem::from).toList();
    long failureCalls = totalCalls - successCalls;
    return new Overview(window.range(), window.startTime(), now, apis.size(), runningApis, apis.size() - runningApis,
        totalCalls, successCalls, failureCalls, percent(successCalls, totalCalls),
        totalCalls == 0 ? 0 : Math.round((double) totalDuration / totalCalls), totalRows,
        trend.stream().map(MutableTrendPoint::toView).toList(), hotApis, failures);
  }

  private List<MutableTrendPoint> createTrend(RangeWindow window) {
    List<MutableTrendPoint> points = new ArrayList<>(window.bucketCount());
    for (int index = 0; index < window.bucketCount(); index++) {
      points.add(new MutableTrendPoint(window.label(window.startTime().plusHours((long) index * window.bucketHours()))));
    }
    return points;
  }

  private static double percent(long numerator, long denominator) {
    return denominator <= 0 ? 0D : Math.round((numerator * 1000D) / denominator) / 10D;
  }

  public record Overview(String range, LocalDateTime startTime, LocalDateTime endTime, long apiTotal,
      long runningApis, long stoppedApis, long totalCalls, long successCalls, long failureCalls,
      double successRate, long averageDurationMs, long totalRows, List<TrendPoint> trend,
      List<HotApi> hotApis, List<FailureItem> recentFailures) {}
  public record TrendPoint(String time, long calls, long successCalls, long failureCalls, long averageDurationMs) {}
  public record HotApi(Long apiId, String name, String path, long calls, double successRate, long averageDurationMs) {}
  public record FailureItem(Long id, Long apiId, String serviceName, String servicePath, long durationMs,
      String errorMessage, LocalDateTime createTime) {
    static FailureItem from(InvocationRecord log) {
      return new FailureItem(log.id(), log.apiId(), log.serviceName(), log.servicePath(), Math.max(0L, log.durationMs()),
          log.errorMessage(), log.createTime());
    }
  }

  private static final class MutableTrendPoint {
    private final String time; private long calls, successes, failures, duration;
    MutableTrendPoint(String time) { this.time = time; }
    void accept(boolean success, long value) { calls++; if (success) successes++; else failures++; duration += Math.max(0L, value); }
    TrendPoint toView() { return new TrendPoint(time, calls, successes, failures, calls == 0 ? 0 : Math.round((double) duration / calls)); }
  }

  private static final class MutableApiStats {
    private String name, path; private long calls, successes, duration;
    MutableApiStats(InvocationRecord first) { name = first.serviceName(); path = first.servicePath(); }
    void accept(InvocationRecord log) { calls++; if (log.success()) successes++; duration += Math.max(0L, log.durationMs());
      if (log.serviceName() != null) name = log.serviceName(); if (log.servicePath() != null) path = log.servicePath(); }
    HotApi toHotApi(Long id, DataServiceDefinition current) {
      String currentName = current == null ? name : current.settings().name();
      String currentPath = current == null ? path : current.settings().path();
      return new HotApi(id, currentName, currentPath, calls, percent(successes, calls),
          calls == 0 ? 0 : Math.round((double) duration / calls));
    }
  }

  private record RangeWindow(String range, LocalDateTime startTime, int bucketHours, int bucketCount,
      DateTimeFormatter labelFormatter) {
    static RangeWindow of(String raw, LocalDateTime now) {
      String range = raw == null || raw.isBlank() ? "24h" : raw.trim().toLowerCase();
      return switch (range) {
        case "24h" -> create("24h", now, 1, 24, DateTimeFormatter.ofPattern("HH:mm"));
        case "7d" -> create("7d", now, 6, 28, DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        case "30d" -> create("30d", now, 24, 30, DateTimeFormatter.ofPattern("MM-dd"));
        default -> throw new IllegalArgumentException("运行概览时间范围仅支持 24h、7d、30d");
      };
    }
    static RangeWindow create(String range, LocalDateTime now, int bucketHours, int count, DateTimeFormatter formatter) {
      LocalDateTime aligned = bucketHours >= 24 ? now.toLocalDate().atStartOfDay()
          : now.withHour((now.getHour() / bucketHours) * bucketHours).withMinute(0).withSecond(0).withNano(0);
      LocalDateTime endBoundary = aligned.plusHours(bucketHours);
      return new RangeWindow(range, endBoundary.minusHours((long) bucketHours * count), bucketHours, count, formatter);
    }
    int bucketIndex(LocalDateTime time) { if (time == null || time.isBefore(startTime)) return -1;
      return (int) (Duration.between(startTime, time).toMinutes() / (bucketHours * 60L)); }
    String label(LocalDateTime time) { return labelFormatter.format(time); }
  }
}
