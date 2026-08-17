package io.yak.ops.boot.home;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorSettingMapper;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.business.workflow.dao.mapper.WorkflowScheduleMapper;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.quartz.CronExpression;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 首页调度中心统一调度配置聚合服务。 */
@Service
@RequiredArgsConstructor
public class HomeScheduleCenterService {

  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
  private static final Set<String> WORKFLOW_ACTIVE_STATUS = Set.of("ONLINE");

  private final ObjectProvider<OfflineJobDefinitionMapper> offlineDefinitionMapperProvider;
  private final ObjectProvider<WorkflowScheduleMapper> workflowScheduleMapperProvider;
  private final ObjectProvider<QualityMonitorSettingMapper> qualitySettingMapperProvider;
  private final ObjectProvider<QualityMonitorMapper> qualityMonitorMapperProvider;

  public CalendarResponse calendar(String monthValue) {
    YearMonth month = resolveMonth(monthValue);
    LocalDate monthStart = month.atDay(1);
    LocalDate monthEnd = month.plusMonths(1).atDay(1);

    List<UnifiedScheduleTask> tasks = new ArrayList<>();
    tasks.addAll(offlineTasks());
    tasks.addAll(workflowTasks());
    tasks.addAll(qualityTasks());

    Map<LocalDate, List<ScheduleOccurrence>> occurrencesByDay = new LinkedHashMap<>();
    Map<String, ScheduleSummary> summariesByTask = new HashMap<>();

    for (UnifiedScheduleTask task : tasks) {
      List<ScheduleOccurrenceAt> occurrences = occurrences(task, monthStart, monthEnd);
      if (occurrences.isEmpty()) continue;

      ScheduleOccurrenceAt first = occurrences.get(0);
      summariesByTask.put(
          task.key(),
          new ScheduleSummary(
              task.taskId(),
              task.taskType(),
              task.taskName(),
              task.scheduleText(),
              first.dateTime().toLocalDate().toString(),
              first.dateTime().format(TIME_FORMATTER),
              task.detailPath()));

      for (ScheduleOccurrenceAt occurrence : occurrences) {
        ScheduleOccurrence item = new ScheduleOccurrence(
            task.taskId(),
            task.taskType(),
            task.taskName(),
            occurrence.dateTime().format(TIME_FORMATTER),
            task.scheduleText(),
            task.detailPath());
        occurrencesByDay.computeIfAbsent(occurrence.dateTime().toLocalDate(), ignored -> new ArrayList<>())
            .add(item);
      }
    }

    List<DaySchedule> days = occurrencesByDay.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> {
          List<ScheduleOccurrence> sorted = entry.getValue().stream()
              .sorted(Comparator
                  .comparing(ScheduleOccurrence::time)
                  .thenComparing(ScheduleOccurrence::taskType)
                  .thenComparing(ScheduleOccurrence::taskName))
              .toList();
          List<ScheduleOccurrence> preview = sorted.size() > 3 ? sorted.subList(0, 3) : sorted;
          return new DaySchedule(entry.getKey().toString(), sorted.size(), preview);
        })
        .toList();

    List<ScheduleSummary> overview = summariesByTask.values().stream()
        .sorted(Comparator
            .comparing(ScheduleSummary::nextRunDate)
            .thenComparing(ScheduleSummary::nextRunTime)
            .thenComparing(ScheduleSummary::taskName))
        .limit(3)
        .toList();

    return new CalendarResponse(
        month.format(MONTH_FORMATTER),
        summariesByTask.size(),
        days,
        overview);
  }

  private List<UnifiedScheduleTask> offlineTasks() {
    OfflineJobDefinitionMapper mapper = offlineDefinitionMapperProvider.getIfAvailable();
    if (mapper == null) return List.of();

    return mapper.selectList(
            new LambdaQueryWrapper<OfflineJobDefinitionPO>()
                .eq(OfflineJobDefinitionPO::getScheduleEnabled, true)
                .isNotNull(OfflineJobDefinitionPO::getCronExpression))
        .stream()
        .filter(item -> hasText(item.getCronExpression()))
        .map(item -> new UnifiedScheduleTask(
            String.valueOf(item.getId()),
            "OFFLINE_SYNC",
            defaultText(item.getJobName(), "离线同步任务 #" + item.getId()),
            item.getCronExpression(),
            ZoneId.systemDefault(),
            null,
            null,
            item.getCronExpression(),
            "/sync/batch-link-up/" + item.getId() + "/detail"))
        .toList();
  }

  private List<UnifiedScheduleTask> workflowTasks() {
    WorkflowScheduleMapper mapper = workflowScheduleMapperProvider.getIfAvailable();
    if (mapper == null) return List.of();

    return mapper.selectList(
            new LambdaQueryWrapper<WorkflowSchedulePO>()
                .isNotNull(WorkflowSchedulePO::getCronExpression))
        .stream()
        .filter(item -> WORKFLOW_ACTIVE_STATUS.contains(normalize(item.getStatus())))
        .filter(item -> hasText(item.getCronExpression()))
        .map(item -> new UnifiedScheduleTask(
            item.getId(),
            "WORKFLOW",
            defaultText(item.getName(), "工作流调度"),
            item.getCronExpression(),
            resolveZone(item.getTimezone()),
            item.getStartTime(),
            item.getEndTime(),
            item.getCronExpression(),
            "/workflow/schedules"))
        .toList();
  }

  private List<UnifiedScheduleTask> qualityTasks() {
    QualityMonitorSettingMapper settingMapper = qualitySettingMapperProvider.getIfAvailable();
    QualityMonitorMapper monitorMapper = qualityMonitorMapperProvider.getIfAvailable();
    if (settingMapper == null || monitorMapper == null) return List.of();

    List<QualityMonitorSettingPO> settings = settingMapper.selectList(
        new LambdaQueryWrapper<QualityMonitorSettingPO>()
            .eq(QualityMonitorSettingPO::getRunMode, "SCHEDULE"));
    if (settings.isEmpty()) return List.of();

    Set<Long> monitorIds = settings.stream()
        .map(QualityMonitorSettingPO::getMonitorId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (monitorIds.isEmpty()) return List.of();

    Map<Long, QualityMonitorPO> monitors = monitorMapper.selectBatchIds(monitorIds).stream()
        .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
        .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
        .collect(Collectors.toMap(QualityMonitorPO::getId, item -> item, (left, right) -> left));

    List<UnifiedScheduleTask> result = new ArrayList<>();
    for (QualityMonitorSettingPO setting : settings) {
      QualityMonitorPO monitor = monitors.get(setting.getMonitorId());
      if (monitor == null) continue;
      String cron = qualityCron(setting);
      if (!hasText(cron)) continue;
      result.add(new UnifiedScheduleTask(
          String.valueOf(monitor.getId()),
          "DATA_QUALITY",
          defaultText(monitor.getMonitorName(), "数据质量任务 #" + monitor.getId()),
          cron,
          ZoneId.systemDefault(),
          null,
          null,
          qualityScheduleText(setting),
          "/data-quality/monitor/" + monitor.getId()));
    }
    return result;
  }

  private List<ScheduleOccurrenceAt> occurrences(
      UnifiedScheduleTask task,
      LocalDate monthStart,
      LocalDate monthEnd) {
    CronExpression cron;
    try {
      cron = new CronExpression(normalizeCron(task.cronExpression()));
      cron.setTimeZone(TimeZone.getTimeZone(task.zoneId()));
    } catch (Exception ignored) {
      return List.of();
    }

    Instant lowerBound = monthStart.atStartOfDay(task.zoneId()).toInstant();
    Instant upperBound = monthEnd.atStartOfDay(task.zoneId()).toInstant();
    if (task.startTime() != null && task.startTime().isAfter(lowerBound)) {
      lowerBound = task.startTime();
    }
    if (task.endTime() != null && task.endTime().isBefore(upperBound)) {
      upperBound = task.endTime();
    }
    if (!lowerBound.isBefore(upperBound)) return List.of();

    List<ScheduleOccurrenceAt> result = new ArrayList<>();
    Date cursor = Date.from(lowerBound.minusMillis(1));

    while (true) {
      Date next = cron.getNextValidTimeAfter(cursor);
      if (next == null) break;
      Instant nextInstant = next.toInstant();
      if (!nextInstant.isBefore(upperBound)) break;

      LocalDateTime dateTime = LocalDateTime.ofInstant(nextInstant, task.zoneId());
      result.add(new ScheduleOccurrenceAt(dateTime));

      Instant nextDay = dateTime.toLocalDate().plusDays(1).atStartOfDay(task.zoneId()).toInstant();
      if (!nextDay.isBefore(upperBound)) break;
      cursor = Date.from(nextDay.minusMillis(1));
    }
    return result;
  }

  private String qualityCron(QualityMonitorSettingPO setting) {
    String frequency = normalize(setting.getScheduleFrequency());
    return switch (frequency) {
      case "DAILY" -> dailyCron(setting.getScheduleTime());
      case "WEEKLY" -> weeklyCron(setting.getScheduleTime(), setting.getScheduleWeekday());
      case "CRON" -> setting.getCronExpression();
      default -> null;
    };
  }

  private String qualityScheduleText(QualityMonitorSettingPO setting) {
    String frequency = normalize(setting.getScheduleFrequency());
    return switch (frequency) {
      case "DAILY" -> "每天 " + timeText(setting.getScheduleTime());
      case "WEEKLY" -> "每周" + weekdayText(setting.getScheduleWeekday()) + " " + timeText(setting.getScheduleTime());
      case "CRON" -> defaultText(setting.getCronExpression(), "Cron");
      default -> "调度";
    };
  }

  private String dailyCron(LocalTime time) {
    if (time == null) return null;
    return String.format("0 %d %d * * ?", time.getMinute(), time.getHour());
  }

  private String weeklyCron(LocalTime time, String weekday) {
    if (time == null || !hasText(weekday)) return null;
    return String.format(
        "0 %d %d ? * %s",
        time.getMinute(),
        time.getHour(),
        normalize(weekday));
  }

  private String normalizeCron(String expression) {
    String normalized = expression == null ? "" : expression.trim().replaceAll("\\s+", " ");
    int fields = normalized.isEmpty() ? 0 : normalized.split(" ").length;
    if (fields == 5) return "0 " + normalized;
    return normalized;
  }

  private YearMonth resolveMonth(String value) {
    if (!hasText(value)) return YearMonth.now();
    try {
      return YearMonth.parse(value.trim(), MONTH_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("month 格式必须为 yyyy-MM", exception);
    }
  }

  private ZoneId resolveZone(String value) {
    try {
      return hasText(value) ? ZoneId.of(value.trim()) : ZoneId.systemDefault();
    } catch (Exception ignored) {
      return ZoneId.systemDefault();
    }
  }

  private String weekdayText(String value) {
    return switch (normalize(value)) {
      case "MON" -> "一";
      case "TUE" -> "二";
      case "WED" -> "三";
      case "THU" -> "四";
      case "FRI" -> "五";
      case "SAT" -> "六";
      case "SUN" -> "日";
      default -> "";
    };
  }

  private String timeText(LocalTime value) {
    return value == null ? "--:--" : value.format(TIME_FORMATTER);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String defaultText(String value, String fallback) {
    return hasText(value) ? value : fallback;
  }

  public record CalendarResponse(
      String month,
      long totalSchedules,
      List<DaySchedule> days,
      List<ScheduleSummary> overview) {}

  public record DaySchedule(
      String date,
      int count,
      List<ScheduleOccurrence> items) {}

  public record ScheduleOccurrence(
      String taskId,
      String taskType,
      String taskName,
      String time,
      String scheduleText,
      String detailPath) {}

  public record ScheduleSummary(
      String taskId,
      String taskType,
      String taskName,
      String scheduleText,
      String nextRunDate,
      String nextRunTime,
      String detailPath) {}

  private record ScheduleOccurrenceAt(LocalDateTime dateTime) {}

  private record UnifiedScheduleTask(
      String taskId,
      String taskType,
      String taskName,
      String cronExpression,
      ZoneId zoneId,
      Instant startTime,
      Instant endTime,
      String scheduleText,
      String detailPath) {
    String key() {
      return taskType + ":" + taskId;
    }
  }
}
