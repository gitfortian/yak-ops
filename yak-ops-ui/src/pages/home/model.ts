export type HomeDataSourceKey =
  | 'dataSource'
  | 'client'
  | 'alarm'
  | 'execution'
  | 'schedule';

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

export type HomeRunType = 'batch' | 'workflow';

export interface HomeRunItem {
  id: string | number;
  type: HomeRunType;
  name: string;
  status?: string;
  startedAt?: string;
  endedAt?: string;
  durationMillis?: number;
  path: string;
}

export interface HomeExecutionOverview {
  todayTotal: number;
  running: number;
  success: number;
  failed: number;
  recent: HomeRunItem[];
  batchObserved: number;
  workflowObserved: number;
  limited: boolean;
}

export type HomeScheduleType = 'batch' | 'workflow';

export interface HomeScheduleItem {
  id: string | number;
  type: HomeScheduleType;
  name: string;
  status: string;
  cronExpression?: string;
  nextRunAt: string;
  lastRunAt?: string;
  timezone?: string;
  path: string;
}

export interface HomeScheduleOverview {
  total: number;
  enabled: number;
  paused: number;
  today: number;
  next24h: number;
  upcoming: HomeScheduleItem[];
  batchObserved: number;
  workflowObserved: number;
  limited: boolean;
}

export interface HomeOverview {
  dataSource?: HomeDataSourceOverview;
  client?: HomeClientOverview;
  alarm?: HomeAlarmOverview;
  execution?: HomeExecutionOverview;
  schedule?: HomeScheduleOverview;
  unavailable: HomeDataSourceKey[];
}
