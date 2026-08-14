package io.yak.ops.business.workflow.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

/** 使用与 Stage 3 Quartz 相同的 Cron 语义生成历史补数逻辑计划时间。 */
@Component
public class WorkflowBackfillPlanner {
  public static final int MAX_OCCURRENCES = 1000;
  private static final long MAX_RANGE_DAYS = 3660L;

  public Plan plan(
      String cronExpression,
      String timezone,
      LocalDate startBusinessDate,
      LocalDate endBusinessDate) {
    if (startBusinessDate == null || endBusinessDate == null) {
      throw new IllegalArgumentException("补数开始和结束业务日期不能为空");
    }
    if (endBusinessDate.isBefore(startBusinessDate)) {
      throw new IllegalArgumentException("补数结束业务日期不能早于开始业务日期");
    }
    long days = ChronoUnit.DAYS.between(startBusinessDate, endBusinessDate) + 1L;
    if (days > MAX_RANGE_DAYS) {
      throw new IllegalArgumentException("单次补数业务日期跨度不能超过 " + MAX_RANGE_DAYS + " 天");
    }

    ZoneId zone = ZoneId.of(required(timezone, "补数时区不能为空"));
    String normalizedCron = normalizeCron(cronExpression);
    CronExpression cron = parseCron(normalizedCron, zone);
    Instant rangeStart = startBusinessDate.atStartOfDay(zone).toInstant();
    Instant rangeEndExclusive = endBusinessDate.plusDays(1).atStartOfDay(zone).toInstant();

    List<Occurrence> occurrences = new ArrayList<>();
    Date cursor = Date.from(rangeStart.minusMillis(1));
    while (true) {
      Date next = cron.getNextValidTimeAfter(cursor);
      if (next == null) break;
      Instant instant = next.toInstant();
      if (!instant.isBefore(rangeEndExclusive)) break;
      if (!instant.isBefore(rangeStart)) {
        ZonedDateTime local = instant.atZone(zone);
        occurrences.add(new Occurrence(
            local.toLocalDate(),
            instant,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local)));
        if (occurrences.size() > MAX_OCCURRENCES) {
          throw new IllegalArgumentException(
              "单次补数最多生成 " + MAX_OCCURRENCES + " 个计划实例，请缩小日期范围");
        }
      }
      cursor = next;
    }

    if (occurrences.isEmpty()) {
      throw new IllegalArgumentException("当前 Cron 在所选业务日期范围内没有可执行计划时间");
    }
    return new Plan(zone.getId(), normalizedCron, List.copyOf(occurrences));
  }

  private CronExpression parseCron(String normalized, ZoneId zone) {
    try {
      CronExpression cron = new CronExpression(normalized);
      cron.setTimeZone(TimeZone.getTimeZone(zone));
      return cron;
    } catch (ParseException exception) {
      throw new IllegalArgumentException("补数无法解析 Cron 表达式：" + normalized, exception);
    }
  }

  private String normalizeCron(String expression) {
    String cron = required(expression, "Cron 表达式不能为空").replaceAll("\\s+", " ");
    String[] fields = cron.split(" ");
    if (fields.length == 5) {
      String minute = fields[0];
      String hour = fields[1];
      String dayOfMonth = fields[2];
      String month = fields[3];
      String dayOfWeek = fields[4];
      boolean monthDaySpecified = !"*".equals(dayOfMonth) && !"?".equals(dayOfMonth);
      boolean weekDaySpecified = !"*".equals(dayOfWeek) && !"?".equals(dayOfWeek);
      if (monthDaySpecified && weekDaySpecified) {
        throw new IllegalArgumentException(
            "5 字段 Cron 同时指定日和周时与 Quartz 语义不等价，请改为 6/7 字段 Quartz Cron");
      }
      if (weekDaySpecified) dayOfMonth = "?";
      else dayOfWeek = "?";
      return String.join(" ", "0", minute, hour, dayOfMonth, month, dayOfWeek);
    }
    if (fields.length == 6 || fields.length == 7) return cron;
    throw new IllegalArgumentException("Backfill 仅支持 5、6 或 7 字段 Cron 表达式");
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  public record Plan(
      String timezone,
      String cronExpression,
      List<Occurrence> occurrences) {
  }

  public record Occurrence(
      LocalDate businessDate,
      Instant scheduleInstant,
      String scheduleTime) {
  }
}
