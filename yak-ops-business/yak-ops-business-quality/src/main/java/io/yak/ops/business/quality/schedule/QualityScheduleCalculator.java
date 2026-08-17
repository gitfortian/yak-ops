package io.yak.ops.business.quality.schedule;

import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class QualityScheduleCalculator {
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  public LocalDateTime nextRun(
      RunMode runMode,
      ScheduleFrequency frequency,
      String scheduleTime,
      ScheduleWeekday weekday,
      String cronExpression,
      LocalDateTime from) {
    if (runMode != RunMode.SCHEDULE) return null;
    if (frequency == null) throw new IllegalArgumentException("调度触发必须选择调度周期");
    LocalDateTime base = from == null ? LocalDateTime.now() : from;
    return switch (frequency) {
      case DAILY -> nextDaily(parseTime(scheduleTime), base);
      case WEEKLY -> nextWeekly(parseTime(scheduleTime), requiredWeekday(weekday), base);
      case CRON -> nextCron(cronExpression, base);
    };
  }

  /** 将数据质量的友好调度配置统一转换成 Yak Schedule/Quartz 使用的 Cron。 */
  public String cronExpression(
      ScheduleFrequency frequency,
      String scheduleTime,
      ScheduleWeekday weekday,
      String cronExpression) {
    if (frequency == null) throw new IllegalArgumentException("调度触发必须选择调度周期");
    return switch (frequency) {
      case DAILY -> dailyCron(parseTime(scheduleTime));
      case WEEKLY -> weeklyCron(parseTime(scheduleTime), requiredWeekday(weekday));
      case CRON -> normalizeQuartzCron(cronExpression);
    };
  }

  public void validateCron(String expression) { parseCron(expression); }

  private LocalDateTime nextDaily(LocalTime time, LocalDateTime from) {
    LocalDateTime candidate = from.toLocalDate().atTime(time);
    return candidate.isAfter(from) ? candidate : candidate.plusDays(1);
  }

  private LocalDateTime nextWeekly(LocalTime time, ScheduleWeekday weekday, LocalDateTime from) {
    DayOfWeek target = DayOfWeek.valueOf(weekday.name());
    int days = Math.floorMod(target.getValue() - from.getDayOfWeek().getValue(), 7);
    LocalDateTime candidate = from.toLocalDate().plusDays(days).atTime(time);
    if (!candidate.isAfter(from)) candidate = candidate.plusWeeks(1);
    return candidate;
  }

  private LocalDateTime nextCron(String expression, LocalDateTime from) {
    LocalDateTime next = parseCron(expression).next(from);
    if (next == null) throw new IllegalArgumentException("Cron 表达式无法计算下一次执行时间");
    return next;
  }

  private String dailyCron(LocalTime time) {
    return "0 " + time.getMinute() + " " + time.getHour() + " * * ?";
  }

  private String weeklyCron(LocalTime time, ScheduleWeekday weekday) {
    return "0 " + time.getMinute() + " " + time.getHour() + " ? * " + weekday.name();
  }

  private String normalizeQuartzCron(String value) {
    validateCron(value);
    String[] fields = value.trim().replaceAll("\\s+", " ").split(" ");
    if (fields.length >= 6) {
      if ("*".equals(fields[3]) && !"?".equals(fields[5])) {
        fields[3] = "?";
      } else if (!"?".equals(fields[3]) && "*".equals(fields[5])) {
        fields[5] = "?";
      }
    }
    return String.join(" ", Arrays.asList(fields));
  }

  private LocalTime parseTime(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("调度触发必须填写执行时间");
    try {
      return LocalTime.parse(value.trim(), TIME_FORMATTER);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("执行时间格式必须为 HH:mm", exception);
    }
  }

  private ScheduleWeekday requiredWeekday(ScheduleWeekday weekday) {
    if (weekday == null) throw new IllegalArgumentException("每周调度必须选择执行日期");
    return weekday;
  }

  private CronExpression parseCron(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Cron 表达式不能为空");
    try {
      return CronExpression.parse(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Cron 表达式格式不正确", exception);
    }
  }
}
