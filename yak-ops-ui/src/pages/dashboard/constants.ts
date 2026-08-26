import type {
  DashboardStatusFilter,
  DashboardTimeRange,
} from './types';

export const DASHBOARD_DEFAULT_PAGE_SIZE = 10;
export const DASHBOARD_PAGE_SIZE_OPTIONS = [10, 20, 50];

export const DASHBOARD_STATUS_FILTERS: Array<{
  key: DashboardStatusFilter;
  label: string;
}> = [
  { key: 'all', label: '全部' },
  { key: 'published', label: '已发布' },
  { key: 'draft', label: '有草稿' },
  { key: 'unpublished', label: '未发布' },
];

export const DASHBOARD_TIME_RANGE_OPTIONS: Array<{
  value: DashboardTimeRange;
  label: string;
}> = [
  { value: 'all', label: '所有时间' },
  { value: '7d', label: '最近 7 天' },
  { value: '30d', label: '最近 30 天' },
];
