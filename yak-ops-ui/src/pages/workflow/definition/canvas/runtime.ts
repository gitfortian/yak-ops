import type { WorkflowNodeInstance } from '@/services/workflow';
import type { WorkflowNodeRuntimeState } from './types';

export const WORKFLOW_ACTIVE_NODE_STATUSES = new Set([
  'WAITING',
  'READY',
  'SUBMITTED',
  'RUNNING',
  'PAUSING',
  'PAUSED',
  'RESUMING',
]);

export const WORKFLOW_TERMINAL_NODE_STATUSES = new Set([
  'SUCCESS',
  'SUCCESS_WITH_WARNINGS',
  'FAILED',
  'WARNING',
  'CANCELED',
  'TIMED_OUT',
  'SKIPPED',
]);

export const isWorkflowNodeActive = (status?: string) =>
  Boolean(status && WORKFLOW_ACTIVE_NODE_STATUSES.has(status));

export const isWorkflowNodeSuccessful = (status?: string) =>
  status === 'SUCCESS' || status === 'SUCCESS_WITH_WARNINGS';

const timestamp = (value?: string) => {
  if (!value) return undefined;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : undefined;
};

export const workflowNodeRuntimeState = (
  node: WorkflowNodeInstance,
): WorkflowNodeRuntimeState => {
  const attempt = node.attempts[node.attempts.length - 1];
  const startedAt = attempt?.startedAt;
  const endedAt = attempt?.endedAt;
  const startMillis = timestamp(startedAt);
  const endMillis = timestamp(endedAt);

  return {
    status: node.status,
    errorMessage: node.errorMessage || attempt?.errorMessage,
    failureReason: node.failureReason || attempt?.failureReason,
    attemptCount: node.attemptCount,
    currentAttemptNumber: node.currentAttemptNumber,
    startedAt,
    endedAt,
    elapsedMillis:
      startMillis !== undefined && endMillis !== undefined
        ? Math.max(0, endMillis - startMillis)
        : undefined,
  };
};

export const runtimeStatusLabel = (status?: string) => {
  switch (status) {
    case 'WAITING':
    case 'READY':
      return '等待中';
    case 'SUBMITTED':
      return '提交中';
    case 'RUNNING':
      return '运行中';
    case 'PAUSING':
      return '暂停中';
    case 'PAUSED':
      return '已暂停';
    case 'RESUMING':
      return '恢复中';
    case 'SUCCESS':
      return '成功';
    case 'SUCCESS_WITH_WARNINGS':
      return '完成';
    case 'WARNING':
      return '告警';
    case 'FAILED':
      return '失败';
    case 'TIMED_OUT':
      return '已超时';
    case 'CANCELED':
      return '已取消';
    case 'SKIPPED':
      return '已跳过';
    default:
      return status || '';
  }
};

export const formatRuntimeDuration = (elapsedMillis?: number) => {
  if (elapsedMillis === undefined) return undefined;
  if (elapsedMillis < 1000) return `${elapsedMillis}ms`;
  if (elapsedMillis < 60_000) return `${(elapsedMillis / 1000).toFixed(elapsedMillis < 10_000 ? 1 : 0)}s`;
  const minutes = Math.floor(elapsedMillis / 60_000);
  const seconds = Math.round((elapsedMillis % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
};
