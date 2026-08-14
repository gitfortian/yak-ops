import type { DashboardDocument } from './model';

/** Dashboard V1 shell with no fake dataset IDs. Real Dataset assets are attached after loading. */
export const DEFAULT_DASHBOARD: DashboardDocument = {
  version: 1,
  id: 'dashboard-local',
  name: '未命名仪表盘',
  activeDatasetId: '',
  widgets: [],
};
