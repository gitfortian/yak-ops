import type { ThemeConfig } from 'antd';

import type {
  RealtimeFilterState,
  RealtimePaginationState,
} from './types';

export const REALTIME_SYNC_INITIAL_FILTERS: RealtimeFilterState = {
  stateGroup: 'ALL',
};

export const REALTIME_SYNC_DEFAULT_PAGINATION: RealtimePaginationState = {
  current: 1,
  pageSize: 20,
  total: 0,
};

export const REALTIME_SYNC_PAGE_SIZE_OPTIONS = [10, 20, 50];

export const REALTIME_SYNC_STATUS_TABS = [
  { label: '全部任务', value: 'ALL' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已停止', value: 'STOPPED' },
  { label: '异常', value: 'ABNORMAL' },
] as const;

export const REALTIME_SYNC_RELEASE_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
] as const;

export const REALTIME_OBSERVED_STATE_LABELS: Record<string, string> = {
  STOPPED: '已停止',
  STARTING: '启动中',
  RUNNING: '运行中',
  STOPPING: '停止中',
  FAILED: '失败',
  UNKNOWN: '未知',
  CONFLICT: '冲突',
};

export const REALTIME_RELEASE_STATE_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
};

export const REALTIME_SYNC_FALLBACK_POLL_INTERVAL = 5000;
export const REALTIME_SYNC_START_POLL_INTERVAL = 2000;
export const REALTIME_SYNC_START_POLL_ATTEMPTS = 15;

export const REALTIME_SYNC_PAGE_THEME: ThemeConfig = {
  token: {
    borderRadius: 10,
    colorBorder: '#f0f0f0',
    colorBgContainer: '#ffffff',
  },
  components: {
    Button: { borderRadius: 8 },
    Input: { activeShadow: 'none' },
    Select: { activeOutlineColor: 'transparent' },
  },
};
