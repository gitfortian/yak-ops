import type { HomeDataCenterOverview } from '@/services/home';

import type {
  HomeDataCenterPeriodKey,
  HomeOverviewMetric,
} from '../types';

const pad2 = (value: number) => String(value).padStart(2, '0');

export const formatDate = (date: Date) =>
  `${date.getFullYear()}.${pad2(date.getMonth() + 1)}.${pad2(date.getDate())}`;

export const formatIsoDate = (value?: string) => {
  if (!value) return '-';
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value.replaceAll('-', '.')
    : formatDate(date);
};

export function buildPeriod(
  periodKey: HomeDataCenterPeriodKey,
  reference = new Date(),
) {
  const today = new Date(
    reference.getFullYear(),
    reference.getMonth(),
    reference.getDate(),
  );
  const end = new Date(today);
  end.setDate(today.getDate() - 1);
  const count =
    periodKey === '30d' ? 30 : periodKey === 'yesterday' ? 1 : 7;
  const start = new Date(end);
  start.setDate(end.getDate() - (count - 1));
  return { start, end };
}

export const formatCount = (value: number) => {
  if (value >= 100000000) {
    return `${(value / 100000000).toFixed(1).replace(/\.0$/, '')}亿`;
  }
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1).replace(/\.0$/, '')}万`;
  }
  return String(value);
};

export const formatDuration = (millis?: number) => {
  const seconds = Math.max(0, Math.round((millis || 0) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
};

export const formatCardDuration = (millis?: number) => {
  const totalSeconds = Math.max(0, Math.round((millis || 0) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  return `${pad2(minutes)}:${pad2(totalSeconds % 60)}`;
};

const signedNumber = (value: number) => `${value > 0 ? '+' : ''}${value}`;
const signedDuration = (value: number) =>
  `${value > 0 ? '+' : value < 0 ? '-' : ''}${formatDuration(Math.abs(value))}`;
const signedRate = (value: number) =>
  `${value > 0 ? '+' : ''}${Number(value || 0).toFixed(1)}%`;

const compareLabelFor = (periodKey: HomeDataCenterPeriodKey) =>
  periodKey === 'yesterday'
    ? '较前1日'
    : periodKey === '30d'
      ? '较前30日'
      : '较前7日';

const positiveWhenUp = (value: number): HomeOverviewMetric['tone'] =>
  value > 0 ? 'positive' : value < 0 ? 'negative' : 'neutral';

const positiveWhenDown = (value: number): HomeOverviewMetric['tone'] =>
  value < 0 ? 'positive' : value > 0 ? 'negative' : 'neutral';

export const toOverviewMetrics = (
  overview: HomeDataCenterOverview | undefined,
  periodKey: HomeDataCenterPeriodKey,
): HomeOverviewMetric[] => {
  const metrics = overview?.metrics;
  const compare = overview?.compare;
  const compareLabel = compareLabelFor(periodKey);
  return [
    {
      label: '成功任务',
      value: String(metrics?.successCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.successCount ?? 0),
      tone: positiveWhenUp(compare?.successCount ?? 0),
    },
    {
      label: '运行中',
      value: String(metrics?.runningCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.runningCount ?? 0),
      tone: positiveWhenDown(compare?.runningCount ?? 0),
    },
    {
      label: '失败任务',
      value: String(metrics?.failedCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.failedCount ?? 0),
      tone: positiveWhenDown(compare?.failedCount ?? 0),
    },
    {
      label: '调度次数',
      value: String(metrics?.scheduleCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.scheduleCount ?? 0),
      tone: 'neutral',
    },
    {
      label: '处理记录',
      value: formatCount(metrics?.processedRecords ?? 0),
      compareLabel,
      compareValue: signedRate(compare?.processedRecordsRate ?? 0),
      tone: positiveWhenUp(compare?.processedRecordsRate ?? 0),
    },
    {
      label: '平均耗时',
      value: formatDuration(metrics?.avgDurationMs ?? 0),
      compareLabel,
      compareValue: signedDuration(compare?.avgDurationMs ?? 0),
      tone: positiveWhenDown(compare?.avgDurationMs ?? 0),
    },
  ];
};

export const taskTypeLabel = (taskType?: string) => {
  if (taskType === 'OFFLINE_SYNC') return '离线同步';
  if (taskType === 'WORKFLOW') return '工作流';
  if (taskType === 'DATA_QUALITY') return '数据质量';
  return '任务';
};

export const statusLabel = (status?: string) => {
  const normalized = status?.toUpperCase();
  if (
    [
      'SUCCEEDED',
      'SUCCESS',
      'SUCCESS_WITH_WARNINGS',
      'COMPLETED',
      'FINISHED',
      'WARNING',
    ].includes(normalized || '')
  ) {
    return '成功';
  }
  if (['FAILED', 'ERROR', 'TIMED_OUT', 'LOST'].includes(normalized || '')) {
    return '失败';
  }
  if (
    [
      'CREATED',
      'SUBMITTED',
      'QUEUED',
      'RUNNING',
      'PAUSING',
      'PAUSED',
      'RESUMING',
    ].includes(normalized || '')
  ) {
    return '运行中';
  }
  if (['CANCELED', 'CANCELLED'].includes(normalized || '')) return '已取消';
  return status || '-';
};

export const statusClassName = (status?: string) => {
  const label = statusLabel(status);
  if (label === '成功') return 'text-[#20a464]';
  if (label === '失败') return 'text-[#f04c5a]';
  return 'text-[#7b8089]';
};

export const formatRunTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16);
  const now = new Date();
  const time = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  if (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  ) {
    return `今日 ${time}`;
  }
  return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${time}`;
};
