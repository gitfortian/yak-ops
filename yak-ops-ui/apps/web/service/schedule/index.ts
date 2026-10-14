import HttpUtils from "@/service/http/HttpUtils";
import { BizError, handleAuthenticationFailure } from "@/service/http/request";
import type {
  ScheduleFireQuery,
  ScheduleFireRecord,
  SchedulePage,
  SchedulePreviewRecord,
  ScheduleRecord,
  ScheduleSummary,
  ScheduleTimeRule,
} from "./types";

export type * from "./types";
const API = "/api/v1/schedules";

// Schedule failures are rendered beside their controls. Authentication keeps the transport's handling.
async function scheduleRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

export async function listTaskSchedules(
  targetIds: string[],
  signal?: AbortSignal,
): Promise<ScheduleSummary[]> {
  if (targetIds.length === 0) return [];
  if (targetIds.length > 200) throw new Error("Schedule batch exceeds 200 targets");
  const page = await scheduleRequest(
    HttpUtils.postData<SchedulePage<ScheduleSummary>>(
      `${API}/page`,
      { targetType: "TASK", targetIds, pageNo: 1, pageSize: 200 },
      { signal, skipErrorHandler: true },
    ),
  );
  if (page.pagination.total > page.bizData.length) {
    throw new Error("Schedule batch is incomplete");
  }
  return page.bizData;
}

export const getSchedule = (id: string, signal?: AbortSignal): Promise<ScheduleRecord> =>
  scheduleRequest(
    HttpUtils.getData<ScheduleRecord>(`${API}/${encodeURIComponent(id)}`, {
      signal,
      skipErrorHandler: true,
    }),
  );

export async function getTaskSchedule(
  targetId: string,
  signal?: AbortSignal,
): Promise<ScheduleRecord | null> {
  const records = await listTaskSchedules([targetId], signal);
  if (records.length === 0) return null;
  if (records.length !== 1 || records[0].targetId !== targetId)
    throw new Error("Schedule target mismatch");
  return getSchedule(records[0].id, signal);
}

export const createSchedule = (
  targetId: string,
  rule: ScheduleTimeRule,
  signal?: AbortSignal,
): Promise<ScheduleRecord> =>
  scheduleRequest(
    HttpUtils.postData<ScheduleRecord>(
      API,
      {
        targetType: "TASK",
        targetId,
        cronExpression: rule.cronExpression,
        timeZone: rule.timeZone,
      },
      { signal, skipErrorHandler: true },
    ),
  );

export const updateSchedule = (
  id: string,
  revision: number,
  rule: ScheduleTimeRule,
  signal?: AbortSignal,
): Promise<ScheduleRecord> =>
  scheduleRequest(
    HttpUtils.putData<ScheduleRecord>(
      `${API}/${encodeURIComponent(id)}`,
      { revision, cronExpression: rule.cronExpression, timeZone: rule.timeZone },
      { signal, skipErrorHandler: true },
    ),
  );

export const deleteSchedule = (
  id: string,
  revision: number,
  signal?: AbortSignal,
): Promise<boolean> =>
  scheduleRequest(
    HttpUtils.deleteData<boolean>(
      `${API}/${encodeURIComponent(id)}?revision=${revision}`,
      undefined,
      { signal, skipErrorHandler: true },
    ),
  );

export const setScheduleEnabled = (
  id: string,
  revision: number,
  enabled: boolean,
  signal?: AbortSignal,
): Promise<ScheduleRecord> =>
  scheduleRequest(
    HttpUtils.postData<ScheduleRecord>(
      `${API}/${encodeURIComponent(id)}/${enabled ? "enable" : "disable"}`,
      { revision },
      { signal, skipErrorHandler: true },
    ),
  );

export const previewSchedule = (
  rule: ScheduleTimeRule,
  signal?: AbortSignal,
): Promise<SchedulePreviewRecord> =>
  scheduleRequest(
    HttpUtils.postData<SchedulePreviewRecord>(
      `${API}/preview`,
      { cronExpression: rule.cronExpression, timeZone: rule.timeZone },
      { signal, skipErrorHandler: true },
    ),
  );

export const listScheduleFires = (
  id: string,
  query: ScheduleFireQuery,
  signal?: AbortSignal,
): Promise<SchedulePage<ScheduleFireRecord>> =>
  scheduleRequest(
    HttpUtils.postData<SchedulePage<ScheduleFireRecord>>(
      `${API}/${encodeURIComponent(id)}/fires/page`,
      query,
      { signal, skipErrorHandler: true },
    ),
  );

/** Only known public codes are mapped; storage and Quartz diagnostics never become UI copy. */
export function scheduleErrorMessage(error: unknown): string {
  if (error instanceof BizError) {
    const messages: Record<number, string> = {
      18001: "Cron 或时区无效",
      18002: "调度已变更，请重新加载",
      18003: "请先停止调度",
      18004: "该任务已配置调度，请重新加载",
      18005: "请先上线任务",
      18006: "该任务不支持周期调度",
      18007: "没有后续触发时间",
      18008: "没有调度操作权限",
      401: "登录状态已失效",
      2001: "登录状态已失效",
    };
    if (error.code && messages[error.code]) return messages[error.code];
  }
  return "调度请求未完成，请重新加载确认结果";
}

export const isInvalidScheduleRule = (error: unknown): boolean =>
  error instanceof BizError && error.code === 18001;
