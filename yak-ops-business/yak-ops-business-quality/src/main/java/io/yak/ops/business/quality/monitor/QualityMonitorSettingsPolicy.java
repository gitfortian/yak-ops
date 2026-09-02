package io.yak.ops.business.quality.monitor;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettingsSpec;
import io.yak.ops.business.quality.schedule.QualityScheduleCalculator;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import org.springframework.stereotype.Component;

/** Normalizes monitor scheduling and notification settings. */
@Component
@ConditionalOnQualityEnabled
public class QualityMonitorSettingsPolicy {
  private final QualityScheduleCalculator scheduleCalculator;

  public QualityMonitorSettingsPolicy(QualityScheduleCalculator scheduleCalculator) {
    this.scheduleCalculator = scheduleCalculator;
  }

  public MonitorSettingsSpec normalize(
      QualityMonitorCommand.Settings command,
      MonitorSettings existing) {
    RuleFailureAction failureAction = command == null
        ? existing == null ? RuleFailureAction.CONTINUE : existing.ruleFailureAction()
        : defaultValue(command.ruleFailureAction(), RuleFailureAction.CONTINUE);
    boolean notifyEnabled = command == null
        ? existing != null && existing.notifyEnabled()
        : Boolean.TRUE.equals(command.notifyEnabled());
    NotifyChannel notifyChannel = command == null
        ? existing == null ? NotifyChannel.MESSAGE : existing.notifyChannel()
        : defaultValue(command.notifyChannel(), NotifyChannel.MESSAGE);
    String notifyTarget = command == null
        ? existing == null ? null : existing.notifyTarget()
        : QualityMonitorPolicy.trimToNull(command.notifyTarget());
    AlertLevel alertLevel = command == null
        ? existing == null ? AlertLevel.WARNING : existing.alertLevel()
        : defaultValue(command.alertLevel(), AlertLevel.WARNING);

    MonitorSettingsSpec schedule = command != null && command.scheduleEnabled() != null
        ? normalizeCanonicalSchedule(command, existing, failureAction, notifyEnabled,
            notifyChannel, notifyTarget, alertLevel)
        : normalizeLegacySchedule(command, existing, failureAction, notifyEnabled,
            notifyChannel, notifyTarget, alertLevel);

    if (notifyEnabled && notifyChannel != NotifyChannel.MESSAGE && notifyTarget == null) {
      throw new IllegalArgumentException(
          notifyChannel == NotifyChannel.EMAIL
              ? "邮件通知必须填写接收邮箱"
              : "Webhook 通知必须填写回调地址");
    }
    return schedule;
  }

  /**
   * Canonical contract used by the new monitor editor: scheduling is an enable flag plus Cron.
   * The old runMode/frequency columns remain persistence compatibility details.
   */
  private MonitorSettingsSpec normalizeCanonicalSchedule(
      QualityMonitorCommand.Settings command,
      MonitorSettings existing,
      RuleFailureAction failureAction,
      boolean notifyEnabled,
      NotifyChannel notifyChannel,
      String notifyTarget,
      AlertLevel alertLevel) {
    boolean scheduleEnabled = Boolean.TRUE.equals(command.scheduleEnabled());
    String cron = QualityMonitorPolicy.trimToNull(command.cronExpression());
    if (cron == null && existing != null && command.cronExpression() == null) {
      cron = existing.cronExpression();
    }
    if (scheduleEnabled && cron == null) {
      throw new IllegalArgumentException("启用调度时 Cron 表达式不能为空");
    }
    if (cron != null) {
      scheduleCalculator.cronExpression(ScheduleFrequency.CRON, null, null, cron);
    }

    return new MonitorSettingsSpec(
        scheduleEnabled ? RunMode.SCHEDULE : RunMode.MANUAL,
        cron == null ? null : ScheduleFrequency.CRON,
        null,
        null,
        cron,
        null,
        failureAction,
        notifyEnabled,
        notifyChannel,
        notifyTarget,
        alertLevel);
  }

  /** Backward-compatible path for older clients that still submit friendly schedule fields. */
  private MonitorSettingsSpec normalizeLegacySchedule(
      QualityMonitorCommand.Settings command,
      MonitorSettings existing,
      RuleFailureAction failureAction,
      boolean notifyEnabled,
      NotifyChannel notifyChannel,
      String notifyTarget,
      AlertLevel alertLevel) {
    RunMode runMode = command == null
        ? existing == null ? RunMode.MANUAL : existing.runMode()
        : defaultValue(command.runMode(), RunMode.MANUAL);
    ScheduleFrequency frequency = command == null
        ? existing == null ? null : existing.scheduleFrequency()
        : command.scheduleFrequency();
    String scheduleTime = command == null
        ? existing == null ? null : existing.scheduleTime()
        : QualityMonitorPolicy.trimToNull(command.scheduleTime());
    ScheduleWeekday weekday = command == null
        ? existing == null ? null : existing.scheduleWeekday()
        : command.scheduleWeekday();
    String cron = command == null
        ? existing == null ? null : existing.cronExpression()
        : QualityMonitorPolicy.trimToNull(command.cronExpression());

    if (runMode == RunMode.MANUAL) {
      frequency = null;
      scheduleTime = null;
      weekday = null;
      cron = null;
    } else {
      if (frequency == null) throw new IllegalArgumentException("调度触发必须选择调度周期");
      switch (frequency) {
        case DAILY -> {
          weekday = null;
          cron = null;
        }
        case WEEKLY -> {
          if (weekday == null) throw new IllegalArgumentException("每周调度必须选择执行日期");
          cron = null;
        }
        case CRON -> {
          scheduleTime = null;
          weekday = null;
        }
      }
      scheduleCalculator.cronExpression(frequency, scheduleTime, weekday, cron);
    }

    return new MonitorSettingsSpec(
        runMode,
        frequency,
        scheduleTime,
        weekday,
        cron,
        null,
        failureAction,
        notifyEnabled,
        notifyChannel,
        notifyTarget,
        alertLevel);
  }

  private static <T> T defaultValue(T value, T fallback) {
    return value == null ? fallback : value;
  }
}
