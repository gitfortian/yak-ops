import type { ApiResponse } from '@/services/http/response';
import { request } from '@umijs/max';

export type WorkflowScheduleStatus = 'ONLINE' | 'OFFLINE';
export type WorkflowScheduleExecutionStrategy =
  | 'PARALLEL'
  | 'SERIAL_WAIT'
  | 'SERIAL_DISCARD';
export type WorkflowScheduleMisfireStrategy = 'SKIP' | 'FIRE_ONCE';

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

export const listWorkflowSchedules = async (params?: {
  workflowId?: string;
  status?: WorkflowScheduleStatus;
}) => {
  const response = await request<ApiResponse<WorkflowSchedule[]>>(
    '/api/v1/workflows/schedules',
    { params },
  );
  return response.data || [];
};

export const createWorkflowSchedule = async (
  workflowId: string,
  payload: WorkflowSchedulePayload,
) => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    '/api/v1/workflows/schedules',
    { method: 'POST', data: { workflowId, ...payload } },
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

const scheduleAction = async (id: string, action: 'online' | 'offline') => {
  const response = await request<ApiResponse<WorkflowSchedule>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}/${action}`,
    { method: 'POST' },
  );
  return response.data;
};

export const onlineWorkflowSchedule = (id: string) => scheduleAction(id, 'online');
export const offlineWorkflowSchedule = (id: string) => scheduleAction(id, 'offline');

export const deleteWorkflowSchedule = async (id: string) => {
  await request<ApiResponse<boolean>>(
    `/api/v1/workflows/schedules/${encodeURIComponent(id)}`,
    { method: 'DELETE' },
  );
};
