import type {
  QualityOverviewDimension,
  QualityOverviewView,
} from '@/services/data-quality';
import dayjs, { type Dayjs } from 'dayjs';

export type OverviewPeriodKey = 'yesterday' | '7d' | '30d';
export type OverviewSectionKind = 'quality' | 'issue';

export interface OverviewDateRange {
  startDate: string;
  endDate: string;
}

export interface OverviewMetricItem {
  label: string;
  value: string;
  tooltip?: string;
}

export const resolvePresetRange = (
  period: OverviewPeriodKey,
): OverviewDateRange => {
  const end = dayjs().subtract(1, 'day');
  const days = period === 'yesterday' ? 1 : period === '7d' ? 7 : 30;
  return {
    startDate: end.subtract(days - 1, 'day').format('YYYY-MM-DD'),
    endDate: end.format('YYYY-MM-DD'),
  };
};

export const rangeKey = (range: OverviewDateRange) =>
  `${range.startDate}:${range.endDate}`;

export const toPickerRange = (range: OverviewDateRange): [Dayjs, Dayjs] => [
  dayjs(range.startDate),
  dayjs(range.endDate),
];

export const formatRangeLabel = (range: OverviewDateRange) =>
  `${dayjs(range.startDate).format('MM.DD')}-${dayjs(range.endDate).format('MM.DD')}`;

export const formatPeriodText = (range: OverviewDateRange) =>
  `统计周期：${range.startDate} 至 ${range.endDate}`;

export const formatRate = (value?: number) =>
  value === undefined || value === null ? '--' : `${value.toFixed(1)}%`;

export const formatDuration = (value?: number) => {
  if (value === undefined || value === null) return '--';
  const milliseconds = Math.round(value);
  if (milliseconds < 1000) return `${milliseconds} ms`;
  if (milliseconds < 60_000) return `${(milliseconds / 1000).toFixed(1)} s`;
  return `${(milliseconds / 60_000).toFixed(1)} min`;
};

export const formatCount = (value?: number) =>
  Math.max(0, Number(value || 0)).toLocaleString();

export const findDimension = (
  dimensions: QualityOverviewDimension[] | undefined,
  aliases: readonly string[],
) => dimensions?.find((item) => aliases.includes(item.dimension));

export const buildMetrics = (
  section: OverviewSectionKind,
  tabKey: string,
  overview?: QualityOverviewView,
): OverviewMetricItem[] => {
  const summary = overview?.summary;
  if (section === 'issue') {
    if (tabKey === 'dimension') {
      const dimensions = overview?.dimensions ?? [];
      const value = (aliases: string[]) =>
        formatCount(findDimension(dimensions, aliases)?.issues);
      return [
        { label: '完整性问题', value: value(['完整性']) },
        { label: '唯一性问题', value: value(['唯一性']) },
        { label: '有效性问题', value: value(['有效性']) },
        { label: '准确性问题', value: value(['准确性']) },
        { label: '时效性问题', value: value(['时效性', '及时性']) },
        { label: '自定义问题', value: value(['自定义']) },
      ];
    }
    return [
      { label: '问题执行', value: formatCount(summary?.issueExecutionCount) },
      { label: '未通过规则', value: formatCount(summary?.failedRuleCount) },
      { label: '异常规则', value: formatCount(summary?.errorRuleCount) },
      { label: '涉及监控', value: formatCount(summary?.affectedMonitorCount) },
      { label: '涉及数据表', value: formatCount(summary?.affectedTableCount) },
      { label: '涉及字段', value: formatCount(summary?.affectedColumnCount) },
      {
        label: '问题率',
        value: formatRate(summary?.issueRate),
        tooltip: '未通过规则与异常规则之和 / 已执行规则数',
      },
      { label: '问题规则', value: formatCount(summary?.issueRuleCount) },
    ];
  }

  if (tabKey === 'monitor') {
    return [
      { label: '活跃监控', value: formatCount(summary?.activeMonitorCount) },
      { label: '执行次数', value: formatCount(summary?.executionCount) },
      { label: '问题监控', value: formatCount(summary?.affectedMonitorCount) },
      { label: '问题执行', value: formatCount(summary?.issueExecutionCount) },
      { label: '问题数据表', value: formatCount(summary?.affectedTableCount) },
      { label: '问题字段', value: formatCount(summary?.affectedColumnCount) },
      { label: '平均耗时', value: formatDuration(summary?.averageDurationMs) },
      {
        label: '最近运行',
        value: summary?.latestExecutionAt
          ? dayjs(summary.latestExecutionAt).format('MM-DD HH:mm')
          : '--',
      },
    ];
  }

  if (tabKey === 'rule') {
    return [
      { label: '执行规则', value: formatCount(summary?.executedRuleCount) },
      { label: '通过规则', value: formatCount(summary?.passedRuleCount) },
      { label: '未通过规则', value: formatCount(summary?.failedRuleCount) },
      { label: '异常规则', value: formatCount(summary?.errorRuleCount) },
      { label: '问题规则', value: formatCount(summary?.issueRuleCount) },
      {
        label: '通过率',
        value: formatRate(summary?.passRate),
        tooltip: '通过规则数 / 已执行规则数',
      },
      {
        label: '问题率',
        value: formatRate(summary?.issueRate),
        tooltip: '未通过规则与异常规则之和 / 已执行规则数',
      },
      { label: '活跃监控', value: formatCount(summary?.activeMonitorCount) },
    ];
  }

  return [
    { label: '执行次数', value: formatCount(summary?.executionCount) },
    { label: '活跃监控', value: formatCount(summary?.activeMonitorCount) },
    { label: '执行规则', value: formatCount(summary?.executedRuleCount) },
    { label: '通过规则', value: formatCount(summary?.passedRuleCount) },
    { label: '未通过规则', value: formatCount(summary?.failedRuleCount) },
    { label: '异常规则', value: formatCount(summary?.errorRuleCount) },
    {
      label: '通过率',
      value: formatRate(summary?.passRate),
      tooltip: '通过规则数 / 已执行规则数',
    },
    { label: '平均耗时', value: formatDuration(summary?.averageDurationMs) },
    { label: '问题执行', value: formatCount(summary?.issueExecutionCount) },
  ];
};
