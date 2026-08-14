import type { ApiResponse } from '@/services/http/response';
import { request } from '@umijs/max';

export type WorkflowScheduleStatus = 'ONLINE' | 'OFFLINE';
export type WorkflowScheduleExecutionStrategy =
  | 'PARALLEL'
  | 'SERIAL_WAIT'
  | 'SERIAL_DISCARD';
export type WorkflowScheduleMisfireStrategy = 'SKIP' | 'FIRE_ONCE';
export type WorkflowScheduleTriggerStatus =
  | 'RECEIVED'
  | 'WAITING'
  | 'LAUNCHING'
  | 'REACTIVATING'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELED'
  | 'SKIPPED';
export type WorkflowBackfillStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'PARTIAL_SUCCESS'
  | 'FAILED'
  | 'CANCELED';
export type WorkflowBackfillExecutionStrategy = 'SERIAL_WAIT' | 'PARALLEL';
export type WorkflowBackfillOperationType = 'BACKFILL' | 'BUSINESS_DATE_RERUN';

export interface WorkflowSchedule {
  id: string;
  workflowId: string;
  name: string;
  triggerType: 'CRON';
  cronExpression: string;
  timezone: string;
  startTime?: string;
  endTime?: string;
  status: WorkflowScheduleStatus;
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  input: Record<string, unknown>;
  lastFireTime?: string;
  nextFireTime?: string;
  createTime: string;
  updateTime: string;
}

export interface WorkflowScheduleTrigger {
  id: string;
  scheduleId: string;
  workflowId: string;
  backfillId?: string;
  triggerId: string;
  triggerSource:
    | 'CRON'
    | 'MANUAL'
    | 'MISFIRE_RECOVERY'
    | 'BACKFILL'
    | 'BUSINESS_DATE_RERUN'
    | string;
  businessDate?: string;
  plannedFireTime: string;
  actualFireTime: string;
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  status: WorkflowScheduleTriggerStatus;
  workflowExecutionId?: string;
  executionStatus?: string;
  message?: string;
  errorMessage?: string;
  launchedAt?: string;
  completedAt?: string;
  createTime: string;
  updateTime: string;
}

export interface WorkflowBackfillPayload {
  scheduleId: string;
  name?: string;
  startBusinessDate: string;
  endBusinessDate: string;
  executionStrategy: WorkflowBackfillExecutionStrategy;
  input: Record<string, unknown>;
}

export interface WorkflowBackfillPreviewOccurrence {
  businessDate: string;
  scheduleInstant: string;
  scheduleTime: string;
}

export interface WorkflowBackfillPreview {
  scheduleId: string;
  cronExpression: string;
  timezone: string;
  startBusinessDate: string;
  endBusinessDate: string;
  totalCount: number;
  truncated: boolean;
  occurrences: WorkflowBackfillPreviewOccurrence[];
}

export interface WorkflowBackfill {
  id: string;
  workflowId: string;
  workflowVersionId: string;
  workflowVersionNo: number;
  scheduleId: string;
  scheduleName: string;
  name: string;
  status: WorkflowBackfillStatus;
  operationType: WorkflowBackfillOperationType;
  sourceExecutionId?: string;
  startBusinessDate: string;
  endBusinessDate: string;
  cronExpression: string;
  timezone: string;
  executionStrategy: WorkflowBackfillExecutionStrategy;
  input: Record<string, unknown>;
  totalCount: number;
  waitingCount: number;
  runningCount: number;
  succeededCount: number;
  failedCount: number;
  canceledCount: number;
  skippedCount: number;
  createTime: string;
  updateTime: string;
}

export interface WorkflowSchedulePayload {
  name: string;
  cronExpression: string;
  timezone: string;
  startTime?: string;
  endTime?: string;
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  input: Record<string, unknown>;
}

export interface WorkflowScheduleTriggerQuery {
  scheduleId?: string;
  workflowId?: string;
  backfillId?: string;
  status?: WorkflowScheduleTriggerStatus;
  limit?: number;
}

export interface WorkflowBackfillQuery {
  workflowId?: string;
  scheduleId?: string;
  status?: WorkflowBackfillStatus;
}

export const listWorkflowSchedules = async (workflowId?: string) => {
  const response = await request<ApiResponse<WorkflowSchedule[]>>('/api/v1/workflows/schedules', {
    params: workflowId ? { workflowId } : undefined,
  });
  return response.data || [];
};

export const getWorkflowSchedule = async (id: string) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}`,
  );
  return response.data;
};

export const createWorkflowSchedule = async (
  workflowId: string,
  payload: WorkflowSchedulePayload,
) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/${encodeURIComponent(workflowId)}/schedules`,
    { method: 'POST', data: payload },
  );
  return response.data;
};

export const updateWorkflowSchedule = async (
  id: string,
  payload: WorkflowSchedulePayload,
) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}`,
    { method: 'PUT', data: payload },
  );
  return response.data;
};

export const onlineWorkflowSchedule = async (id: string) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}/online`,
    { method: 'POST' },
  );
  return response.data;
};

export const offlineWorkflowSchedule = async (id: string) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}/offline`,
    { method: 'POST' },
  );
  return response.data;
};

export const runWorkflowScheduleNow = async (id: string) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}/run`,
    { method: 'POST' },
  );
  return response.data;
};

export const removeWorkflowSchedule = async (id: string) => {
  await request<ApiResponse<void>>(`/api/v1/workflows/schedules/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
};

export const listWorkflowScheduleTriggers = async (query: WorkflowScheduleTriggerQuery = {}) => {
  const response = await request<ApiResponse<WorkflowScheduleTrigger[]>>(
    '/api/v1/workflows/schedules/triggers',
    { params: query },
  );
  return response.data || [];
};

export const previewWorkflowBackfill = async (
  scheduleId: string,
  payload: Pick<WorkflowBackfillPayload, 'startBusinessDate' | 'endBusinessDate'>,
) => {
  const response = await request<ApiResponse<WorkflowBackfillPreview>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(scheduleId)}/backfills/preview`,
    { method: 'POST', data: payload },
  );
  return response.data;
};

export const createWorkflowBackfill = async (
  scheduleId: string,
  payload: WorkflowBackfillPayload,
) => {
  const response = await request<ApiResponse<WorkflowBackfill>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(scheduleId)}/backfills`,
    { method: 'POST', data: payload },
  );
  return response.data;
};

export const listWorkflowBackfills = async (query: WorkflowBackfillQuery = {}) => {
  const response = await request<ApiResponse<WorkflowBackfill[]>>('/api/v1/workflows/backfills', {
    params: query,
  });
  return response.data || [];
};

export const getWorkflowBackfill = async (id: string) => {
  const response = await request<ApiResponse<WorkflowBackfill>>(
    `/api/v1/workflows/backfills/${encodeURIComponent(id)}`,
  );
  return response.data;
};

export const cancelWorkflowBackfill = async (id: string) => {
  const response = await request<ApiResponse<WorkflowBackfill>>(
    `/api/v1/workflows/backfills/${encodeURIComponent(id)}/cancel`,
    { method: 'POST' },
  );
  return response.data;
};
