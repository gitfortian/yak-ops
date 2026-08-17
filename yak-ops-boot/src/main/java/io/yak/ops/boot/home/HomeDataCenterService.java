package io.yak.ops.boot.home;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.quality.dao.mapper.QualityExecutionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowExecutionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 首页数据中心运行统计聚合服务。 */
@Service
@RequiredArgsConstructor
public class HomeDataCenterService {

  private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
  private static final Set<String> WORKFLOW_RUNNING =
      Set.of("CREATED", "RUNNING", "PAUSING", "PAUSED", "RESUMING");
  private static final Set<String> WORKFLOW_SUCCESS =
      Set.of("SUCCESS", "SUCCESS_WITH_WARNINGS", "WARNING");
  private static final Set<String> WORKFLOW_FAILED = Set.of("FAILED", "TIMED_OUT");
  private static final Set<String> QUALITY_RUNNING = Set.of("QUEUED", "RUNNING");
  private static final Set<String> QUALITY_SUCCESS = Set.of("SUCCESS", "SUCCEEDED", "COMPLETED");
  private static final Set<String> QUALITY_FAILED = Set.of("FAILED", "ERROR", "TIMED_OUT");
  private static final Set<String> OFFLINE_RUNNING = Set.of("CREATED", "SUBMITTED", "QUEUED", "RUNNING");
  private static final Set<String> OFFLINE_SUCCESS = Set.of("SUCCEEDED", "SUCCESS", "FINISHED", "COMPLETED");
  private static final Set<String> OFFLINE_FAILED = Set.of("FAILED", "LOST");

  private final ObjectProvider<OfflineJobExecutionMapper> offlineExecutionMapperProvider;
  private final ObjectProvider<OfflineJobDefinitionMapper> offlineDefinitionMapperProvider;
  private final ObjectProvider<WorkflowExecutionMapper> workflowExecutionMapperProvider;
  private final ObjectProvider<WorkflowScheduleMapper> workflowScheduleMapperProvider;
  private final ObjectProvider<QualityExecutionMapper> qualityExecutionMapperProvider;

  public OverviewResponse overview(String periodValue) {
    PeriodRange range = PeriodRange.resolve(periodValue);
    PeriodRange previous = range.previous();

    List<RuntimeExecution> current = executions(range.start(), range.end());
    List<RuntimeExecution> previousExecutions = executions(previous.start(), previous.end());

    Metrics currentMetrics = metrics(current, range.end());
    Metrics previousMetrics = metrics(previousExecutions, previous.end());
    LatestTask latestTask = latestTask(current);

    return new OverviewResponse(
        new PeriodView(range.start().toLocalDate().toString(), range.end().minusNanos(1).toLocalDate().toString()),
        latestTask,
        trend(range, current),
        currentMetrics,
        compare(currentMetrics, previousMetrics));
  }

  public RecentResponse recent() {
    LocalDateTime end = LocalDateTime.now().plusNanos(1);
    LocalDateTime start = end.minusDays(7);
    List<RuntimeExecution> all = executions(start, end);

    Map<String, List<RuntimeExecution>> grouped =
        all.stream().collect(Collectors.groupingBy(RuntimeExecution::taskKey));

    List<RecentTask> items = new ArrayList<>();
    grouped.values().forEach(group -> {
      RuntimeExecution latest = group.stream().max(Comparator.comparing(RuntimeExecution::occurredAt)).orElse(null);
      if (latest == null) return;
      long success = group.stream().filter(RuntimeExecution::success).count();
      long failed = group.stream().filter(RuntimeExecution::failed).count();
      items.add(new RecentTask(
          latest.taskId(),
          latest.taskType(),
          latest.taskName(),
          latest.occurredAt().toString(),
          group.size(),
          success,
          failed,
          latest.durationMs(),
          latest.status(),
          detailPath(latest)));
    });

    items.sort(Comparator.comparing(RecentTask::lastRunTime).reversed());
    if (items.size() > 5) items = new ArrayList<>(items.subList(0, 5));
    return new RecentResponse(items);
  }

  public ScheduleResponse schedules(String periodValue) {
    PeriodRange range = PeriodRange.resolve(periodValue);
    List<ScheduleItem> items = new ArrayList<>();

    OfflineJobDefinitionMapper offlineMapper = offlineDefinitionMapperProvider.getIfAvailable();
    if (offlineMapper != null) {
      List<OfflineJobDefinitionPO> definitions = offlineMapper.selectList(
          new LambdaQueryWrapper<OfflineJobDefinitionPO>()
              .eq(OfflineJobDefinitionPO::getScheduleEnabled, true)
              .orderByDesc(OfflineJobDefinitionPO::getScheduleNextFireTime)
              .last("LIMIT 20"));
      for (OfflineJobDefinitionPO definition : definitions) {
        items.add(new ScheduleItem(
            String.valueOf(definition.getId()),
            "OFFLINE_SYNC",
            defaultText(definition.getJobName(), "离线同步任务"),
            definition.getCronExpression(),
            "ENABLED",
            toText(definition.getScheduleLastFireTime()),
            toText(definition.getScheduleNextFireTime()),
            "/sync/batch-link-up/" + definition.getId() + "/detail"));
      }
    }

    WorkflowScheduleMapper workflowMapper = workflowScheduleMapperProvider.getIfAvailable();
    if (workflowMapper != null) {
      List<WorkflowSchedulePO> schedules = workflowMapper.selectList(
          new LambdaQueryWrapper<WorkflowSchedulePO>()
              .orderByDesc(WorkflowSchedulePO::getNextFireTime)
              .last("LIMIT 20"));
      for (WorkflowSchedulePO schedule : schedules) {
        items.add(new ScheduleItem(
            schedule.getId(),
            "WORKFLOW",
            defaultText(schedule.getName(), "工作流调度"),
            schedule.getCronExpression(),
            defaultText(schedule.getStatus(), "UNKNOWN"),
            toText(schedule.getLastFireTime()),
            toText(schedule.getNextFireTime()),
            "/workflow/schedules"));
      }
    }

    items.sort(Comparator.comparing(
        ScheduleItem::nextScheduleTime,
        Comparator.nullsLast(Comparator.naturalOrder())));
    if (items.size() > 8) items = new ArrayList<>(items.subList(0, 8));
    return new ScheduleResponse(
        new PeriodView(range.start().toLocalDate().toString(), range.end().minusNanos(1).toLocalDate().toString()),
        items.size(),
        items);
  }

  private List<RuntimeExecution> executions(LocalDateTime start, LocalDateTime end) {
    List<RuntimeExecution> result = new ArrayList<>();
    result.addAll(offlineExecutions(start, end));
    result.addAll(workflowExecutions(start, end));
    result.addAll(qualityExecutions(start, end));
    result.sort(Comparator.comparing(RuntimeExecution::occurredAt));
    return result;
  }

  private List<RuntimeExecution> offlineExecutions(LocalDateTime start, LocalDateTime end) {
    OfflineJobExecutionMapper mapper = offlineExecutionMapperProvider.getIfAvailable();
    if (mapper == null) return List.of();

    List<OfflineJobExecutionPO> rows = mapper.selectList(
        new LambdaQueryWrapper<OfflineJobExecutionPO>()
            .ge(OfflineJobExecutionPO::getCreateTime, start)
            .lt(OfflineJobExecutionPO::getCreateTime, end)
            .orderByAsc(OfflineJobExecutionPO::getCreateTime));

    OfflineJobDefinitionMapper definitionMapper = offlineDefinitionMapperProvider.getIfAvailable();
    Map<Long, String> names = new HashMap<>();
    if (definitionMapper != null) {
      Set<Long> ids = rows.stream()
          .map(OfflineJobExecutionPO::getJobDefinitionId)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      if (!ids.isEmpty()) {
        names = definitionMapper.selectBatchIds(ids).stream()
            .collect(Collectors.toMap(OfflineJobDefinitionPO::getId, OfflineJobDefinitionPO::getJobName, (a, b) -> a));
      }
    }

    Map<Long, String> finalNames = names;
    return rows.stream().map(row -> {
      String status = normalize(row.getStatus());
      LocalDateTime occurredAt = firstNonNull(row.getStartTime(), row.getCreateTime(), row.getUpdateTime());
      return new RuntimeExecution(
          "OFFLINE_SYNC",
          String.valueOf(row.getJobDefinitionId()),
          defaultText(finalNames.get(row.getJobDefinitionId()), "离线同步任务 #" + row.getJobDefinitionId()),
          status,
          occurredAt,
          row.getEndTime(),
          zero(row.getDurationMillis()),
          zero(row.getSinkSuccessRecordCount()),
          "SCHEDULE".equalsIgnoreCase(row.getTriggerType()),
          OFFLINE_SUCCESS.contains(status),
          OFFLINE_FAILED.contains(status),
          OFFLINE_RUNNING.contains(status),
          row.getErrorMessage(),
          String.valueOf(row.getId()));
    }).toList();
  }

  private List<RuntimeExecution> workflowExecutions(LocalDateTime start, LocalDateTime end) {
    WorkflowExecutionMapper mapper = workflowExecutionMapperProvider.getIfAvailable();
    if (mapper == null) return List.of();
    ZoneId zone = ZoneId.systemDefault();
    Instant startInstant = start.atZone(zone).toInstant();
    Instant endInstant = end.atZone(zone).toInstant();

    List<WorkflowExecutionPO> rows = mapper.selectList(
        new LambdaQueryWrapper<WorkflowExecutionPO>()
            .ge(WorkflowExecutionPO::getCreatedAt, startInstant)
            .lt(WorkflowExecutionPO::getCreatedAt, endInstant)
            .orderByAsc(WorkflowExecutionPO::getCreatedAt));

    return rows.stream().map(row -> {
      String status = normalize(row.getStatus());
      Instant started = firstNonNull(row.getRunStartedAt(), row.getCreatedAt(), row.getUpdatedAt());
      long duration = row.getEndedAt() == null || started == null
          ? 0L
          : Math.max(0L, Duration.between(started, row.getEndedAt()).toMillis());
      return new RuntimeExecution(
          "WORKFLOW",
          defaultText(row.getDefinitionId(), row.getId()),
          defaultText(row.getWorkflowName(), "工作流 #" + row.getDefinitionId()),
          status,
          LocalDateTime.ofInstant(started == null ? Instant.EPOCH : started, zone),
          row.getEndedAt() == null ? null : LocalDateTime.ofInstant(row.getEndedAt(), zone),
          duration,
          0L,
          false,
          WORKFLOW_SUCCESS.contains(status),
          WORKFLOW_FAILED.contains(status),
          WORKFLOW_RUNNING.contains(status),
          null,
          row.getId());
    }).toList();
  }

  private List<RuntimeExecution> qualityExecutions(LocalDateTime start, LocalDateTime end) {
    QualityExecutionMapper mapper = qualityExecutionMapperProvider.getIfAvailable();
    if (mapper == null) return List.of();

    List<QualityExecutionPO> rows = mapper.selectList(
        new LambdaQueryWrapper<QualityExecutionPO>()
            .ge(QualityExecutionPO::getQueuedAt, start)
            .lt(QualityExecutionPO::getQueuedAt, end)
            .orderByAsc(QualityExecutionPO::getQueuedAt));

    return rows.stream().map(row -> {
      String status = normalize(row.getExecutionStatus());
      LocalDateTime occurredAt = firstNonNull(row.getStartedAt(), row.getQueuedAt(), row.getCreatedAt());
      // 质量检查结果 FAIL 仅表示发现质量问题；技术执行成功仍计入成功任务。
      boolean success = QUALITY_SUCCESS.contains(status);
      return new RuntimeExecution(
          "DATA_QUALITY",
          String.valueOf(row.getMonitorId()),
          defaultText(row.getMonitorName(), "数据质量任务 #" + row.getMonitorId()),
          status,
          occurredAt,
          row.getFinishedAt(),
          zero(row.getDurationMs()),
          0L,
          "SCHEDULE".equalsIgnoreCase(row.getTriggerType()),
          success,
          QUALITY_FAILED.contains(status),
          QUALITY_RUNNING.contains(status),
          row.getErrorMessage(),
          row.getExecutionNo());
    }).toList();
  }

  private Metrics metrics(List<RuntimeExecution> executions, LocalDateTime end) {
    long success = executions.stream().filter(RuntimeExecution::success).count();
    long failed = executions.stream().filter(RuntimeExecution::failed).count();
    long schedule = executions.stream().filter(RuntimeExecution::scheduled).count();
    long processed = executions.stream()
        .filter(item -> "OFFLINE_SYNC".equals(item.taskType()))
        .mapToLong(RuntimeExecution::processedRecords)
        .sum();
    List<Long> durations = executions.stream()
        .filter(item -> !item.running() && item.durationMs() > 0)
        .map(RuntimeExecution::durationMs)
        .toList();
    long avgDuration = durations.isEmpty()
        ? 0L
        : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0D));
    long running = runningAt(end);
    return new Metrics(success, running, failed, schedule, processed, avgDuration);
  }

  private long runningAt(LocalDateTime point) {
    long count = 0L;

    OfflineJobExecutionMapper offline = offlineExecutionMapperProvider.getIfAvailable();
    if (offline != null) {
      count += offline.selectCount(
          new LambdaQueryWrapper<OfflineJobExecutionPO>()
              .le(OfflineJobExecutionPO::getCreateTime, point)
              .and(wrapper -> wrapper.isNull(OfflineJobExecutionPO::getEndTime)
                  .or().gt(OfflineJobExecutionPO::getEndTime, point)));
    }

    WorkflowExecutionMapper workflow = workflowExecutionMapperProvider.getIfAvailable();
    if (workflow != null) {
      Instant instant = point.atZone(ZoneId.systemDefault()).toInstant();
      count += workflow.selectCount(
          new LambdaQueryWrapper<WorkflowExecutionPO>()
              .le(WorkflowExecutionPO::getCreatedAt, instant)
              .and(wrapper -> wrapper.isNull(WorkflowExecutionPO::getEndedAt)
                  .or().gt(WorkflowExecutionPO::getEndedAt, instant)));
    }

    QualityExecutionMapper quality = qualityExecutionMapperProvider.getIfAvailable();
    if (quality != null) {
      count += quality.selectCount(
          new LambdaQueryWrapper<QualityExecutionPO>()
              .le(QualityExecutionPO::getQueuedAt, point)
              .and(wrapper -> wrapper.isNull(QualityExecutionPO::getFinishedAt)
                  .or().gt(QualityExecutionPO::getFinishedAt, point)));
    }
    return count;
  }

  private Trend trend(PeriodRange range, List<RuntimeExecution> executions) {
    if (range.days() == 1) {
      List<String> labels = List.of("00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00");
      long[] buckets = new long[7];
      for (RuntimeExecution execution : executions) {
        int index = Math.min(5, Math.max(0, execution.occurredAt().getHour() / 4));
        buckets[index]++;
      }
      // 最后一个点保持 0，用于保留现有 24:00 坐标与折线收尾。
      List<Long> values = new ArrayList<>();
      for (long bucket : buckets) values.add(bucket);
      return new Trend(labels, values);
    }

    Map<LocalDate, Long> counts = executions.stream().collect(Collectors.groupingBy(
        item -> item.occurredAt().toLocalDate(), LinkedHashMap::new, Collectors.counting()));
    List<String> labels = new ArrayList<>();
    List<Long> values = new ArrayList<>();
    for (int i = 0; i < range.days(); i++) {
      LocalDate day = range.start().toLocalDate().plusDays(i);
      labels.add(DAY_LABEL.format(day));
      values.add(counts.getOrDefault(day, 0L));
    }
    return new Trend(labels, values);
  }

  private LatestTask latestTask(List<RuntimeExecution> current) {
    RuntimeExecution latest = current.stream()
        .max(Comparator.comparing(RuntimeExecution::occurredAt))
        .orElseGet(this::latestExecutionAcrossAll);
    if (latest == null) return null;

    LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
    List<RuntimeExecution> recentRuns = executions(sevenDaysAgo, LocalDateTime.now().plusNanos(1)).stream()
        .filter(item -> item.taskKey().equals(latest.taskKey()))
        .toList();
    long exceptions = recentRuns.stream().filter(RuntimeExecution::failed).count();
    return new LatestTask(
        latest.taskId(),
        latest.taskType(),
        latest.taskName(),
        latest.durationMs(),
        recentRuns.size(),
        exceptions,
        latest.status(),
        detailPath(latest));
  }

  private RuntimeExecution latestExecutionAcrossAll() {
    LocalDateTime end = LocalDateTime.now().plusNanos(1);
    List<RuntimeExecution> candidates = executions(end.minusDays(90), end);
    return candidates.stream().max(Comparator.comparing(RuntimeExecution::occurredAt)).orElse(null);
  }

  private MetricCompare compare(Metrics current, Metrics previous) {
    return new MetricCompare(
        current.successCount() - previous.successCount(),
        current.runningCount() - previous.runningCount(),
        current.failedCount() - previous.failedCount(),
        current.scheduleCount() - previous.scheduleCount(),
        rateChange(current.processedRecords(), previous.processedRecords()),
        current.avgDurationMs() - previous.avgDurationMs());
  }

  private BigDecimal rateChange(long current, long previous) {
    if (previous <= 0L) return current <= 0L ? BigDecimal.ZERO : BigDecimal.valueOf(100);
    return BigDecimal.valueOf(current - previous)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
  }

  private String detailPath(RuntimeExecution execution) {
    return switch (execution.taskType()) {
      case "OFFLINE_SYNC" -> "/sync/batch-link-up/" + execution.taskId() + "/detail";
      case "WORKFLOW" -> "/workflow/instances";
      case "DATA_QUALITY" -> "/data-quality/execution/" + execution.executionId();
      default -> "/home";
    };
  }

  private static String normalize(String value) {
    return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static long zero(Long value) {
    return value == null ? 0L : value;
  }

  private static String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String toText(LocalDateTime value) {
    return value == null ? null : value.toString();
  }

  private static String toText(Instant value) {
    return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault()).toString();
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    for (T value : values) if (value != null) return value;
    return null;
  }

  public record OverviewResponse(
      PeriodView period,
      LatestTask latestTask,
      Trend trend,
      Metrics metrics,
      MetricCompare compare) {}

  public record PeriodView(String start, String end) {}

  public record Trend(List<String> labels, List<Long> values) {}

  public record Metrics(
      long successCount,
      long runningCount,
      long failedCount,
      long scheduleCount,
      long processedRecords,
      long avgDurationMs) {}

  public record MetricCompare(
      long successCount,
      long runningCount,
      long failedCount,
      long scheduleCount,
      BigDecimal processedRecordsRate,
      long avgDurationMs) {}

  public record LatestTask(
      String taskId,
      String taskType,
      String taskName,
      long durationMs,
      long runCount,
      long exceptionCount,
      String status,
      String detailPath) {}

  public record RecentResponse(List<RecentTask> items) {}

  public record RecentTask(
      String taskId,
      String taskType,
      String taskName,
      String lastRunTime,
      long runCount,
      long successCount,
      long failedCount,
      long lastDurationMs,
      String lastStatus,
      String detailPath) {}

  public record ScheduleResponse(PeriodView period, long total, List<ScheduleItem> items) {}

  public record ScheduleItem(
      String taskId,
      String taskType,
      String taskName,
      String cronExpression,
      String status,
      String lastScheduleTime,
      String nextScheduleTime,
      String detailPath) {}

  private record RuntimeExecution(
      String taskType,
      String taskId,
      String taskName,
      String status,
      LocalDateTime occurredAt,
      LocalDateTime endedAt,
      long durationMs,
      long processedRecords,
      boolean scheduled,
      boolean success,
      boolean failed,
      boolean running,
      String errorMessage,
      String executionId) {
    String taskKey() {
      return taskType + ":" + taskId;
    }
  }

  private record PeriodRange(LocalDateTime start, LocalDateTime end, int days) {
    static PeriodRange resolve(String value) {
      String normalized = value == null ? "7d" : value.trim().toLowerCase(Locale.ROOT);
      int days = switch (normalized) {
        case "yesterday" -> 1;
        case "30d" -> 30;
        default -> 7;
      };
      LocalDate yesterday = LocalDate.now().minusDays(1);
      LocalDate startDay = yesterday.minusDays(days - 1L);
      return new PeriodRange(startDay.atStartOfDay(), yesterday.plusDays(1).atStartOfDay(), days);
    }

    PeriodRange previous() {
      return new PeriodRange(start.minusDays(days), start, days);
    }
  }
}
