import type { ScheduleSummary, ScheduleTimeRule } from "@/service/schedule";

export const EMPTY_SCHEDULE: ScheduleTimeRule = { cronExpression: "", timeZone: "Asia/Shanghai" };

export const sameScheduleRule = (left: ScheduleTimeRule, right: ScheduleTimeRule): boolean =>
  left.cronExpression === right.cronExpression && left.timeZone === right.timeZone;

export const FIRE_OUTCOMES = {
  ADMITTED: "已提交",
  SKIPPED: "已跳过",
  REJECTED: "已拒绝",
  SUBMISSION_FAILED: "提交失败",
} as const;

const REASONS: Record<string, string> = {
  RESOURCE_BUSY: "目标资源占用",
  STALE_SCHEDULE: "调度已停止或变更",
  TARGET_UNPUBLISHED: "请先上线任务",
  TARGET_DELETED: "任务已删除",
  NOT_SCHEDULABLE: "该任务不支持周期调度",
  UNAUTHORIZED: "启用者权限已失效",
  DEFINITION_CHANGED: "任务版本已变更",
  BINDING_CHANGED: "数据源配置已变更",
  MISSED_WINDOW: "已错过触发窗口",
  INVALID_OCCURRENCE: "触发时间无效",
  PREPARATION_FAILED: "任务准备失败",
  REGISTRATION_FAILED: "调度注册失败",
  SCHEDULER_UNAVAILABLE: "调度服务不可用",
};

export const scheduleReasonText = (reason?: string | null): string =>
  reason ? REASONS[reason] || "调度状态异常" : "—";

export function registrationText(schedule: ScheduleSummary): string | null {
  if (schedule.registrationStatus === "ERROR")
    return scheduleReasonText(schedule.registrationReason);
  if (schedule.registrationStatus === "PENDING") return "应用中";
  if (schedule.registrationStatus !== "APPLIED") return "状态未知";
  if (schedule.enabled && schedule.exhausted) return "无后续触发";
  return null;
}

/** Explicit offsets preserve occurrence identity, including repeated DST wall times. */
export function formatScheduleTime(value?: string | null, timeZone = "UTC"): string {
  if (!value || !/(?:Z|[+-]\d{2}:\d{2})$/.test(value)) return "—";
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) return "—";
  try {
    return new Intl.DateTimeFormat("sv-SE", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hourCycle: "h23",
      timeZoneName: "shortOffset",
    }).format(time);
  } catch {
    return value;
  }
}
