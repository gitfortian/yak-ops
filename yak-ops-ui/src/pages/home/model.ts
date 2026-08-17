export type HomeDataSourceKey = 'dataSource' | 'client' | 'alarm';

export interface HomeDataSourceOverview {
  total: number;
  connected: number;
  disconnected: number;
  unknown: number;
  environmentCount: number;
}

export interface HomeClientOverview {
  total: number;
  online?: number;
  offline?: number;
}

export interface HomeAlarmItem {
  id?: number;
  jobName?: string;
  status?: string;
  severity?: string;
  time?: string;
}

export interface HomeAlarmOverview {
  total: number;
  recent: HomeAlarmItem[];
}

export interface HomeOverview {
  dataSource?: HomeDataSourceOverview;
  client?: HomeClientOverview;
  alarm?: HomeAlarmOverview;
  unavailable: HomeDataSourceKey[];
}
