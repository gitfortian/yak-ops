package io.yak.ops.business.dataservice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
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
import org.springframework.stereotype.Service;

/** Lightweight runtime overview aggregated from existing API definitions and invocation logs. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceOverviewService {

  private static final int HOT_API_LIMIT = 8;
  private static final int FAILURE_LIMIT = 8;

  private final DataServiceApiMapper apiMapper;
  private final DataServiceCallLogMapper callLogMapper;

  public Overview overview(String range) {
    return overviewAt(range, LocalDateTime.now());
  }

  Overview overviewAt(String range, LocalDateTime now) {
    RangeWindow window = RangeWindow.of(range, now);
    List<DataServiceApiPO> apis = apiMapper.selectList(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .select(
                DataServiceApiPO::getId,
                DataServiceApiPO::getName,
                DataServiceApiPO::getPath,
                DataServiceApiPO::getEnabled)
            .orderByAsc(DataServiceApiPO::getId));

    List<DataServiceCallLogPO> logs = callLogMapper.selectList(
        Wrappers.<DataServiceCallLogPO>lambdaQuery()
            .select(
                DataServiceCallLogPO::getId,
                DataServiceCallLogPO::getApiId,
                DataServiceCallLogPO::getServiceName,
                DataServiceCallLogPO::getServicePath,
                DataServiceCallLogPO::getSuccess,
                DataServiceCallLogPO::getDurationMs,
                DataServiceCallLogPO::getRowCount,
                DataServiceCallLogPO::getErrorMessage,
                DataServiceCallLogPO::getCreateTime)
            .ge(DataServiceCallLogPO::getCreateTime, window.startTime())
            .le(DataServiceCallLogPO::getCreateTime, now)
            .orderByAsc(DataServiceCallLogPO::getCreateTime)
            .orderByAsc(DataServiceCallLogPO::getId));

    long runningApis = apis.stream().filter(api -> Boolean.TRUE.equals(api.getEnabled())).count();
    long totalCalls = logs.size();
    long successCalls = 0;
    long totalDuration = 0;
    long totalRows = 0;

    List<MutableTrendPoint> trend = createTrend(window);
    Map<Long, MutableApiStats> apiStats = new LinkedHashMap<>();

    for (DataServiceCallLogPO log : logs) {
      boolean success = Boolean.TRUE.equals(log.getSuccess());
      long duration = safeLong(log.getDurationMs());
      int rows = safeInt(log.getRowCount());
      if (success) successCalls++;
      totalDuration += duration;
      totalRows += rows;

      int bucketIndex = window.bucketIndex(log.getCreateTime());
      if (bucketIndex >= 0 && bucketIndex < trend.size()) {
        trend.get(bucketIndex).accept(success, duration);
      }

      if (log.getApiId() != null) {
        apiStats
            .computeIfAbsent(log.getApiId(), ignored -> new MutableApiStats(log))
            .accept(log, success, duration);
      }
    }

    long failureCalls = totalCalls - successCalls;
    long averageDurationMs = totalCalls == 0 ? 0 : Math.round((double) totalDuration / totalCalls);
    double successRate = percent(successCalls, totalCalls);

    Map<Long, DataServiceApiPO> apiById = new LinkedHashMap<>();
    for (DataServiceApiPO api : apis) {
      if (api.getId() != null) apiById.put(api.getId(), api);
    }

    List<HotApi> hotApis = apiStats.entrySet().stream()
        .sorted((left, right) -> Long.compare(right.getValue().calls, left.getValue().calls))
        .limit(HOT_API_LIMIT)
        .map(entry -> entry.getValue().toHotApi(entry.getKey(), apiById.get(entry.getKey())))
        .toList();

    List<FailureItem> recentFailures = logs.stream()
        .filter(log -> !Boolean.TRUE.equals(log.getSuccess()))
        .sorted(Comparator
            .comparing(DataServiceCallLogPO::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed())
        .limit(FAILURE_LIMIT)
        .map(FailureItem::from)
        .toList();

    return new Overview(
        window.range(),
        window.startTime(),
        now,
        apis.size(),
        runningApis,
        apis.size() - runningApis,
        totalCalls,
        successCalls,
        failureCalls,
        successRate,
        averageDurationMs,
        totalRows,
        trend.stream().map(MutableTrendPoint::toView).toList(),
        hotApis,
        recentFailures);
  }

  private List<MutableTrendPoint> createTrend(RangeWindow window) {
    List<MutableTrendPoint> points = new ArrayList<>(window.bucketCount());
    for (int index = 0; index < window.bucketCount(); index++) {
      LocalDateTime time = window.startTime().plusHours((long) index * window.bucketHours());
      points.add(new MutableTrendPoint(window.label(time)));
    }
    return points;
  }

  private static long safeLong(Long value) {
    return value == null ? 0L : Math.max(0L, value);
  }

  private static int safeInt(Integer value) {
    return value == null ? 0 : Math.max(0, value);
  }

  private static double percent(long numerator, long denominator) {
    if (denominator <= 0) return 0D;
    return Math.round((numerator * 1000D) / denominator) / 10D;
  }

  public record Overview(
      String range,
      LocalDateTime startTime,
      LocalDateTime endTime,
      long apiTotal,
      long runningApis,
      long stoppedApis,
      long totalCalls,
      long successCalls,
      long failureCalls,
      double successRate,
      long averageDurationMs,
      long totalRows,
      List<TrendPoint> trend,
      List<HotApi> hotApis,
      List<FailureItem> recentFailures) {}

  public record TrendPoint(
      String time,
      long calls,
      long successCalls,
      long failureCalls,
      long averageDurationMs) {}

  public record HotApi(
      Long apiId,
      String name,
      String path,
      long calls,
      double successRate,
      long averageDurationMs) {}

  public record FailureItem(
      Long id,
      Long apiId,
      String serviceName,
      String servicePath,
      long durationMs,
      String errorMessage,
      LocalDateTime createTime) {

    private static FailureItem from(DataServiceCallLogPO log) {
      return new FailureItem(
          log.getId(),
          log.getApiId(),
          log.getServiceName(),
          log.getServicePath(),
          safeLong(log.getDurationMs()),
          log.getErrorMessage(),
          log.getCreateTime());
    }
  }

  private static final class MutableTrendPoint {
    private final String time;
    private long calls;
    private long successCalls;
    private long failureCalls;
    private long totalDuration;

    private MutableTrendPoint(String time) {
      this.time = time;
    }

    private void accept(boolean success, long duration) {
      calls++;
      if (success) successCalls++;
      else failureCalls++;
      totalDuration += duration;
    }

    private TrendPoint toView() {
      long average = calls == 0 ? 0 : Math.round((double) totalDuration / calls);
      return new TrendPoint(time, calls, successCalls, failureCalls, average);
    }
  }

  private static final class MutableApiStats {
    private String name;
    private String path;
    private long calls;
    private long successCalls;
    private long totalDuration;

    private MutableApiStats(DataServiceCallLogPO first) {
      this.name = first.getServiceName();
      this.path = first.getServicePath();
    }

    private void accept(DataServiceCallLogPO log, boolean success, long duration) {
      calls++;
      if (success) successCalls++;
      totalDuration += duration;
      if (log.getServiceName() != null) name = log.getServiceName();
      if (log.getServicePath() != null) path = log.getServicePath();
    }

    private HotApi toHotApi(Long apiId, DataServiceApiPO current) {
      String currentName = current != null && current.getName() != null ? current.getName() : name;
      String currentPath = current != null && current.getPath() != null ? current.getPath() : path;
      long average = calls == 0 ? 0 : Math.round((double) totalDuration / calls);
      return new HotApi(apiId, currentName, currentPath, calls, percent(successCalls, calls), average);
    }
  }

  private record RangeWindow(
      String range,
      LocalDateTime startTime,
      int bucketHours,
      int bucketCount,
      DateTimeFormatter labelFormatter) {

    private static RangeWindow of(String raw, LocalDateTime now) {
      String range = raw == null || raw.isBlank() ? "24h" : raw.trim().toLowerCase();
      return switch (range) {
        case "24h" -> create("24h", now, 1, 24, DateTimeFormatter.ofPattern("HH:mm"));
        case "7d" -> create("7d", now, 6, 28, DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        case "30d" -> create("30d", now, 24, 30, DateTimeFormatter.ofPattern("MM-dd"));
        default -> throw new IllegalArgumentException("运行概览时间范围仅支持 24h、7d、30d");
      };
    }

    private static RangeWindow create(
        String range,
        LocalDateTime now,
        int bucketHours,
        int bucketCount,
        DateTimeFormatter formatter) {
      LocalDateTime aligned;
      if (bucketHours >= 24) {
        aligned = now.toLocalDate().atStartOfDay();
      } else {
        int hour = (now.getHour() / bucketHours) * bucketHours;
        aligned = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
      }
      LocalDateTime endBoundary = aligned.plusHours(bucketHours);
      LocalDateTime start = endBoundary.minusHours((long) bucketHours * bucketCount);
      return new RangeWindow(range, start, bucketHours, bucketCount, formatter);
    }

    private int bucketIndex(LocalDateTime time) {
      if (time == null || time.isBefore(startTime)) return -1;
      long minutes = Duration.between(startTime, time).toMinutes();
      return (int) (minutes / (bucketHours * 60L));
    }

    private String label(LocalDateTime time) {
      return labelFormatter.format(time);
    }
  }
}
