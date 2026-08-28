import type {
  HomeDataCenterPeriodKey,
  HomeDataCenterTabKey,
} from '../../types';

export const OVERVIEW_TABS: Array<{
  key: HomeDataCenterTabKey;
  label: string;
}> = [
  { key: 'overview', label: '运行总览' },
  { key: 'recent', label: '近期任务' },
  { key: 'schedule', label: '调度数据' },
];

export const PERIOD_OPTIONS: Array<{
  key: HomeDataCenterPeriodKey;
  label: string;
}> = [
  { key: 'yesterday', label: '昨天' },
  { key: '7d', label: '近7日' },
  { key: '30d', label: '近30日' },
];
