import type {
  WorkflowDefinition,
  WorkflowDefinitionStatus,
} from '@/services/workflow/definitions';

export type WorkflowFilterKey = 'ALL' | WorkflowDefinitionStatus;
export type WorkflowViewMode = 'grid' | 'list';

export interface WorkflowSummary {
  total: number;
  online: number;
  draftChanged: number;
  activeExecutions: number;
}

export const WORKFLOW_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const WORKFLOW_STATUS_TABS: Array<{
  key: WorkflowFilterKey;
  label: string;
}> = [
  { key: 'ALL', label: '全部' },
  { key: 'ONLINE', label: '已上线' },
  { key: 'DRAFT', label: '草稿' },
  { key: 'OFFLINE', label: '已下线' },
];

const ACTIVE_RUNTIME_STATUSES = new Set([
  'CREATED',
  'WAITING',
  'READY',
  'SUBMITTED',
  'RUNNING',
  'PAUSING',
  'PAUSED',
  'RESUMING',
]);

const RUNNING_RUNTIME_STATUSES = new Set([
  'CREATED',
  'WAITING',
  'READY',
  'SUBMITTED',
  'RUNNING',
]);

const RUNTIME_LABELS: Record<string, string> = {
  CREATED: '已创建',
  WAITING: '等待中',
  READY: '就绪',
  SUBMITTED: '待执行',
  RUNNING: '运行中',
  PAUSING: '暂停中',
  PAUSED: '已暂停',
  RESUMING: '恢复中',
  SUCCESS: '成功',
  SUCCESS_WITH_WARNINGS: '完成（有告警）',
  FAILED: '失败',
  WARNING: '告警',
  CANCELED: '已取消',
  TIMED_OUT: '已超时',
};

const FAILURE_STRATEGY_LABELS: Record<string, string> = {
  FAIL_FAST: '快速失败',
  CONTINUE_INDEPENDENT_BRANCHES: '继续独立分支',
  TERMINATE_ALL: '终止全部',
};

export const DEFINITION_STATUS_META: Record<
  WorkflowDefinitionStatus,
  { label: string; textClassName: string; backgroundClassName: string }
> = {
  DRAFT: {
    label: '草稿',
    textClassName: 'text-[#667085]',
    backgroundClassName: 'bg-[#f1f3f5]',
  },
  ONLINE: {
    label: '已上线',
    textClassName: 'text-[#e5254e]',
    backgroundClassName: 'bg-[#fff1f4]',
  },
  OFFLINE: {
    label: '已下线',
    textClassName: 'text-[#667085]',
    backgroundClassName: 'bg-[#f1f3f5]',
  },
};

export const isActiveRuntime = (status?: string) =>
  Boolean(status && ACTIVE_RUNTIME_STATUSES.has(status));

export const isRunningRuntime = (status?: string) =>
  Boolean(status && RUNNING_RUNTIME_STATUSES.has(status));

export const runtimeStatusMeta = (status?: string) => {
  if (!status) {
    return {
      label: '尚未运行',
      dotClassName: 'bg-[#a6abb4]',
      textClassName: 'text-[#777d88]',
      backgroundClassName: 'bg-[#f3f4f6]',
    };
  }

  if (status === 'FAILED' || status === 'TIMED_OUT') {
    return {
      label: RUNTIME_LABELS[status] || status,
      dotClassName: 'bg-[#e45863]',
      textClassName: 'text-[#c74350]',
      backgroundClassName: 'bg-[#fff1f2]',
    };
  }

  if (status === 'WARNING' || status === 'SUCCESS_WITH_WARNINGS') {
    return {
      label: RUNTIME_LABELS[status] || status,
      dotClassName: 'bg-[#e39b35]',
      textClassName: 'text-[#b77a22]',
      backgroundClassName: 'bg-[#fff7e9]',
    };
  }

  if (isActiveRuntime(status)) {
    return {
      label: RUNTIME_LABELS[status] || status,
      dotClassName: 'bg-[#fe2c55]',
      textClassName: 'text-[#e5254e]',
      backgroundClassName: 'bg-[#fff1f4]',
    };
  }

  if (status === 'SUCCESS') {
    return {
      label: '成功',
      dotClassName: 'bg-[#38a169]',
      textClassName: 'text-[#36845d]',
      backgroundClassName: 'bg-[#edf8f1]',
    };
  }

  return {
    label: RUNTIME_LABELS[status] || status,
    dotClassName: 'bg-[#8f96a3]',
    textClassName: 'text-[#667085]',
    backgroundClassName: 'bg-[#f3f4f6]',
  };
};

export const getRuntimeHint = (
  runtimeStatus: string | undefined,
  definitionStatus: WorkflowDefinitionStatus,
) => {
  if (!runtimeStatus) {
    return definitionStatus === 'ONLINE'
      ? '可运行当前生效版本'
      : '发布并上线后可正式运行';
  }

  switch (runtimeStatus) {
    case 'RUNNING':
      return '当前存在运行中的执行实例';
    case 'PAUSING':
      return '正在暂停当前执行';
    case 'PAUSED':
      return '执行已暂停，可恢复后继续';
    case 'RESUMING':
      return '正在恢复当前执行';
    case 'CREATED':
    case 'WAITING':
    case 'READY':
    case 'SUBMITTED':
      return '执行已提交，等待运行';
    case 'SUCCESS':
      return '最近一次执行已成功完成';
    case 'SUCCESS_WITH_WARNINGS':
    case 'WARNING':
      return '最近一次执行完成，但存在告警';
    case 'FAILED':
      return '最近一次执行失败';
    case 'TIMED_OUT':
      return '最近一次执行超时';
    case 'CANCELED':
      return '最近一次执行已取消';
    default:
      return '可前往执行实例查看详细状态';
  }
};

export const getPublishActionLabel = (record: WorkflowDefinition) => {
  if (record.status === 'ONLINE') return '下线工作流';
  if (
    record.status === 'OFFLINE' &&
    record.activeVersionNo &&
    !record.draftChanged
  ) {
    return '重新上线';
  }
  if (record.activeVersionNo && record.draftChanged) {
    return '发布更新并上线';
  }
  return '发布并上线';
};

export const formatWorkflowTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
};

export const formatWorkflowDuration = (seconds?: number) => {
  if (!seconds || seconds <= 0) return '未设置';
  if (seconds < 60) return `${seconds} 秒`;
  if (seconds % 3600 === 0) return `${seconds / 3600} 小时`;
  if (seconds % 60 === 0) return `${seconds / 60} 分钟`;
  return `${seconds} 秒`;
};

export const failureStrategyLabel = (value: string) =>
  FAILURE_STRATEGY_LABELS[value] || value;

export const buildWorkflowSummary = (
  definitions: WorkflowDefinition[],
): WorkflowSummary => ({
  total: definitions.length,
  online: definitions.filter((item) => item.status === 'ONLINE').length,
  draftChanged: definitions.filter((item) => item.draftChanged).length,
  activeExecutions: definitions.filter((item) =>
    isActiveRuntime(item.latestExecutionStatus),
  ).length,
});

export const WORKFLOW_PAGE_ANIMATION = {
  fadeUp: {
    hidden: { opacity: 0, y: 18 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.45,
        ease: [0.22, 1, 0.36, 1],
      },
    },
  },
  sectionStagger: {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: 0.08,
        delayChildren: 0.06,
      },
    },
  },
  cardStagger: {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: 0.06,
      },
    },
  },
};
