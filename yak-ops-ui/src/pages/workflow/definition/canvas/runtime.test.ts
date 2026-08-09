import type { WorkflowNodeInstance } from '@/services/workflow';
import {
  formatRuntimeDuration,
  isWorkflowNodeActive,
  runtimeStatusLabel,
  workflowNodeRuntimeState,
} from './runtime';

const node = (overrides: Partial<WorkflowNodeInstance> = {}): WorkflowNodeInstance => ({
  id: 'node-a',
  taskId: 'task-a',
  name: '同步订单',
  type: 'SYNC',
  status: 'SUCCESS',
  triggerRule: 'ALL_SUCCESS',
  failurePolicy: 'FAIL_WORKFLOW',
  continuedAfterFailure: false,
  attemptCount: 1,
  retryMaxAttempts: 1,
  retryDelaySeconds: 0,
  dispatchTimeoutSeconds: 0,
  executionTimeoutSeconds: 0,
  inputMapping: {},
  input: {},
  predecessorOutputs: {},
  output: {},
  attempts: [],
  ...overrides,
});

describe('workflow runtime canvas mapping', () => {
  it('maps the latest attempt and final elapsed time into transient node state', () => {
    const state = workflowNodeRuntimeState(node({
      status: 'SUCCESS',
      attemptCount: 2,
      currentAttemptNumber: 2,
      attempts: [
        {
          id: 'attempt-1',
          attemptNumber: 1,
          status: 'FAILED',
          pausedMillis: 0,
        },
        {
          id: 'attempt-2',
          attemptNumber: 2,
          status: 'SUCCESS',
          pausedMillis: 0,
          startedAt: '2026-08-09T06:00:00.000Z',
          endedAt: '2026-08-09T06:00:01.250Z',
        },
      ],
    }));

    expect(state.status).toBe('SUCCESS');
    expect(state.attemptCount).toBe(2);
    expect(state.currentAttemptNumber).toBe(2);
    expect(state.elapsedMillis).toBe(1250);
    expect(formatRuntimeDuration(state.elapsedMillis)).toBe('1.3s');
  });

  it('treats scheduling/running/pause states as active canvas states', () => {
    expect(isWorkflowNodeActive('WAITING')).toBe(true);
    expect(isWorkflowNodeActive('SUBMITTED')).toBe(true);
    expect(isWorkflowNodeActive('RUNNING')).toBe(true);
    expect(isWorkflowNodeActive('PAUSED')).toBe(true);
    expect(isWorkflowNodeActive('SUCCESS')).toBe(false);
    expect(isWorkflowNodeActive('FAILED')).toBe(false);
  });

  it('provides stable labels for streamed statuses', () => {
    expect(runtimeStatusLabel('RUNNING')).toBe('运行中');
    expect(runtimeStatusLabel('SUCCESS')).toBe('成功');
    expect(runtimeStatusLabel('FAILED')).toBe('失败');
    expect(runtimeStatusLabel('TIMED_OUT')).toBe('已超时');
  });
});
