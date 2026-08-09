import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/http/response';

export type WorkflowFailureStrategy =
  | 'FAIL_FAST'
  | 'CONTINUE_INDEPENDENT_BRANCHES'
  | 'TERMINATE_ALL';
export type WorkflowTriggerRule =
  | 'ALL_SUCCESS'
  | 'ALL_DONE'
  | 'NONE_FAILED'
  | 'ONE_SUCCESS'
  | 'ALWAYS';
export type WorkflowNodeFailurePolicy =
  | 'FAIL_WORKFLOW'
  | 'BLOCK_BRANCH'
  | 'IGNORE_FAILURE';

export interface WorkflowTaskDefinition {
  id: string;
  name: string;
  type: string;
}

export interface WorkflowNodePayload {
  id: string;
  taskId: string;
  maxAttempts?: number;
  retryDelaySeconds?: number;
  dispatchTimeoutSeconds?: number;
  executionTimeoutSeconds?: number;
  inputMapping?: Record<string, string>;
  triggerRule?: WorkflowTriggerRule;
  failurePolicy?: WorkflowNodeFailurePolicy;
}

export interface WorkflowEdgePayload {
  source: string;
  target: string;
}

export interface WorkflowRunPayload {
  name: string;
  nodes: WorkflowNodePayload[];
  edges: WorkflowEdgePayload[];
  input?: Record<string, unknown>;
  workflowTimeoutSeconds?: number;
  failureStrategy?: WorkflowFailureStrategy;
}

export interface WorkflowAttempt {
  id: string;
  attemptNumber: number;
  status: string;
  failureReason?: string;
  errorMessage?: string;
  availableAt?: string;
  startedAt?: string;
  pausedAt?: string;
  pausedMillis: number;
  endedAt?: string;
}

export interface WorkflowNodeInstance {
  id: string;
  taskId: string;
  name: string;
  type: string;
  status: string;
  triggerRule: WorkflowTriggerRule;
  failurePolicy: WorkflowNodeFailurePolicy;
  errorMessage?: string;
  failureReason?: string;
  continuedAfterFailure: boolean;
  attemptCount: number;
  currentAttemptId?: string;
  currentAttemptNumber?: number;
  retryMaxAttempts: number;
  retryDelaySeconds: number;
  dispatchTimeoutSeconds: number;
  executionTimeoutSeconds: number;
  inputMapping: Record<string, string>;
  input: Record<string, unknown>;
  predecessorOutputs: Record<string, Record<string, unknown>>;
  output: Record<string, unknown>;
  attempts: WorkflowAttempt[];
}

export interface WorkflowInstance {
  id: string;
  definitionId: string;
  sourceExecutionId?: string;
  name: string;
  status: string;
  failureStrategy: WorkflowFailureStrategy;
  startedAt: string;
  runStartedAt?: string;
  endedAt?: string;
  workflowTimeoutSeconds: number;
  input: Record<string, unknown>;
  nodeCount: number;
  edgeCount: number;
  nodes: WorkflowNodeInstance[];
  workflowVersionId?: string;
  workflowVersionNo?: number;
  testRun: boolean;
}

interface WorkflowEventSubscription {
  onSnapshot: (instance: WorkflowInstance) => void;
  lastSignature: string;
  stopped: boolean;
  closeActive?: () => void;
}

const TERMINAL_STATUSES = new Set([
  'SUCCESS',
  'SUCCESS_WITH_WARNINGS',
  'FAILED',
  'WARNING',
  'CANCELED',
  'TIMED_OUT',
]);

const workflowEventSubscriptions = new Map<string, WorkflowEventSubscription>();

export const isWorkflowTerminal = (status?: string) =>
  Boolean(status && TERMINAL_STATUSES.has(status));

export const getWorkflowTasks = async () => {
  const response = await request<ApiResponse<WorkflowTaskDefinition[]>>('/api/v1/tasks');
  return response.data;
};

export const runWorkflow = async (payload: WorkflowRunPayload) => {
  const response = await request<ApiResponse<WorkflowInstance>>('/api/v1/workflows/run', {
    method: 'POST',
    data: payload,
  });
  return response.data;
};

const postInstanceAction = async (executionId: string, action: string) => {
  const response = await request<ApiResponse<WorkflowInstance>>(
    `/api/v1/workflows/instances/${encodeURIComponent(executionId)}/${action}`,
    { method: 'POST' },
  );
  resumeWorkflowEventsIfNeeded(executionId, response.data);
  return response.data;
};

export const activateWorkflowInstance = (executionId: string) => postInstanceAction(executionId, 'activate');
export const pauseWorkflowInstance = (executionId: string) => postInstanceAction(executionId, 'pause');
export const resumeWorkflowInstance = (executionId: string) => postInstanceAction(executionId, 'resume');
export const cancelWorkflowInstance = (executionId: string) => postInstanceAction(executionId, 'cancel');
export const retryWorkflowFailedNodes = (executionId: string) => postInstanceAction(executionId, 'retry-failed');
export const restartWorkflowInstance = (executionId: string) => postInstanceAction(executionId, 'restart');

export const continueWorkflowAfterFailure = async (executionId: string, nodeId: string) => {
  const response = await request<ApiResponse<WorkflowInstance>>(
    `/api/v1/workflows/instances/${encodeURIComponent(executionId)}/nodes/${encodeURIComponent(nodeId)}/continue`,
    { method: 'POST' },
  );
  resumeWorkflowEventsIfNeeded(executionId, response.data);
  return response.data;
};

export const retryWorkflowFailedNode = async (executionId: string, nodeId: string) => {
  const response = await request<ApiResponse<WorkflowInstance>>(
    `/api/v1/workflows/instances/${encodeURIComponent(executionId)}/nodes/${encodeURIComponent(nodeId)}/retry`,
    { method: 'POST' },
  );
  resumeWorkflowEventsIfNeeded(executionId, response.data);
  return response.data;
};

export const rerunWorkflowFromNode = async (executionId: string, nodeId: string) => {
  const response = await request<ApiResponse<WorkflowInstance>>(
    `/api/v1/workflows/instances/${encodeURIComponent(executionId)}/nodes/${encodeURIComponent(nodeId)}/rerun`,
    { method: 'POST' },
  );
  return response.data;
};

export const getWorkflowInstances = async () => {
  const response = await request<ApiResponse<WorkflowInstance[]>>('/api/v1/workflows/instances');
  return response.data;
};

export const getWorkflowInstance = async (executionId: string) => {
  const response = await request<ApiResponse<WorkflowInstance>>(
    `/api/v1/workflows/instances/${encodeURIComponent(executionId)}`,
  );
  return response.data;
};

const snapshotSignature = (instance: WorkflowInstance) => [
  instance.status,
  ...instance.nodes.map((node) => [
    node.id,
    node.status,
    node.currentAttemptId || '',
    node.attemptCount,
    node.failureReason || '',
    node.errorMessage || '',
  ].join(':')),
].join('|');

const openWorkflowEventSubscription = (
  executionId: string,
  subscription: WorkflowEventSubscription,
) => {
  if (subscription.stopped) return;
  subscription.closeActive?.();
  let activeClosed = false;
  let polling = false;
  let fallbackTimer: number | undefined;

  const deliver = (snapshot: WorkflowInstance) => {
    if (activeClosed || subscription.stopped) return;
    const signature = snapshotSignature(snapshot);
    if (signature === subscription.lastSignature) return;
    subscription.lastSignature = signature;
    subscription.onSnapshot(snapshot);
    if (isWorkflowTerminal(snapshot.status)) cleanupActive();
  };

  const poll = async () => {
    if (activeClosed || subscription.stopped || polling) return;
    polling = true;
    try {
      deliver(await getWorkflowInstance(executionId));
    } catch {
      // SSE/代理异常时的兜底查询失败不打断编辑器。
    } finally {
      polling = false;
    }
  };

  const stopFallbackPolling = () => {
    if (fallbackTimer === undefined) return;
    window.clearInterval(fallbackTimer);
    fallbackTimer = undefined;
  };

  const startFallbackPolling = () => {
    if (activeClosed || subscription.stopped || fallbackTimer !== undefined) return;
    fallbackTimer = window.setInterval(() => void poll(), 1000);
    void poll();
  };

  const source = new EventSource(`/api/v1/workflows/instances/${encodeURIComponent(executionId)}/events`);
  const handleWorkflowEvent = (event: Event) => {
    try {
      deliver(JSON.parse((event as MessageEvent<string>).data) as WorkflowInstance);
    } catch {
      // 单次异常事件不关闭连接。
    }
  };
  source.addEventListener('workflow', handleWorkflowEvent);
  source.onopen = () => stopFallbackPolling();
  source.onerror = () => {
    // EventSource 会自行重连；重连期间用低频 authenticated request 保证状态不会停住。
    startFallbackPolling();
  };

  function cleanupActive() {
    if (activeClosed) return;
    activeClosed = true;
    stopFallbackPolling();
    source.removeEventListener('workflow', handleWorkflowEvent);
    source.close();
    if (subscription.closeActive === cleanupActive) subscription.closeActive = undefined;
  }

  subscription.closeActive = cleanupActive;
  // 首帧主动读取一次，随后以 SSE 为主；不会再在健康连接下每 500ms 打接口。
  void poll();
};

function resumeWorkflowEventsIfNeeded(executionId: string, snapshot: WorkflowInstance) {
  if (snapshot.id !== executionId || isWorkflowTerminal(snapshot.status)) return;
  const subscription = workflowEventSubscriptions.get(executionId);
  if (!subscription || subscription.stopped || subscription.closeActive) return;
  openWorkflowEventSubscription(executionId, subscription);
}

/** SSE 为主；只有连接异常/重连期间才启用 1s 查询兜底。 */
export const subscribeWorkflowEvents = (
  executionId: string,
  onSnapshot: (instance: WorkflowInstance) => void,
) => {
  const existing = workflowEventSubscriptions.get(executionId);
  if (existing) {
    existing.stopped = true;
    existing.closeActive?.();
  }
  const subscription: WorkflowEventSubscription = {
    onSnapshot,
    lastSignature: '',
    stopped: false,
  };
  workflowEventSubscriptions.set(executionId, subscription);
  openWorkflowEventSubscription(executionId, subscription);
  return () => {
    subscription.stopped = true;
    subscription.closeActive?.();
    if (workflowEventSubscriptions.get(executionId) === subscription) {
      workflowEventSubscriptions.delete(executionId);
    }
  };
};
