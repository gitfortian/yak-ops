import {
  getWorkflowInstances,
  type WorkflowInstance,
} from '@/services/workflow';
import {
  listWorkflowSchedules,
  type WorkflowSchedule,
} from '@/services/workflow/schedules';
import HttpUtils from '@/utils/HttpUtils';
import dayjs from 'dayjs';
import { fetchAlarmRecords } from '../alarm/service';
import {
  batchJobInstanceApi,
  linkupJobDefinitionApi,
  type OfflineJobDefinitionVO,
  type OfflineJobExecutionVO,
} from '../batch-link-up/api';
import { fetchDataSourceSummary } from '../data-source/service';
import type {
  HomeAlarmOverview,
  HomeClientOverview,
  HomeDataSourceKey,
  HomeDataSourceOverview,
  HomeExecutionOverview,
  HomeOverview,
  HomeRunItem,
  HomeScheduleItem,
  HomeScheduleOverview,
} from './model';

const CLIENT_COUNT_API = '/api/v1/datax/executor/count';
const HOME_BATCH_INSTANCE_LIMIT = 200;
const HOME_BATCH_DEFINITION_LIMIT = 200;
const HOME_RECENT_RUN_LIMIT = 6;
const HOME_UPCOMING_SCHEDULE_LIMIT = 6;

const SUCCESS_STATUSES = new Set([
  'SUCCESS',
  'SUCCEEDED',
  'SUCCESS_WITH_WARNINGS',
  'COMPLETED',
  'FINISHED',
]);
const FAILED_STATUSES = new Set(['FAILED', 'ERROR', 'TIMED_OUT', 'TIMEOUT']);
const RUNNING_STATUSES = new Set([
  'RUNNING',
  'STARTING',
  'SUBMITTED',
  'DISPATCHING',
  'QUEUED',
  'PENDING',
  'WAITING',
  'RETRYING',
  'PAUSED',
]);

type ClientCountPayload =
  | number
  | {
      total?: number;
      count?: number;
      executorCount?: number;
      online?: number;
      onlineCount?: number;
      active?: number;
      activeCount?: number;
      offline?: number;
      offlineCount?: number;
    };

interface BatchDefinitionSnapshot {
  definitions: OfflineJobDefinitionVO[];
  total: number;
}

const asFiniteNumber = (value: unknown): number | undefined => {
  if (typeof value !== 'number' || !Number.isFinite(value)) return undefined;
  return value;
};

const firstNumber = (
  payload: Record<string, unknown>,
  keys: string[],
): number | undefined => {
  for (const key of keys) {
    const value = asFiniteNumber(payload[key]);
    if (value !== undefined) return value;
  }
  return undefined;
};

const normalizeStatus = (status?: string) => status?.trim().toUpperCase() || '';

const isToday = (value?: string) => {
  if (!value) return false;
  const time = dayjs(value);
  return time.isValid() && time.isSame(dayjs(), 'day');
};

const timeValue = (value?: string) => {
  if (!value) return 0;
  const time = dayjs(value);
  return time.isValid() ? time.valueOf() : 0;
};

const durationBetween = (startedAt?: string, endedAt?: string) => {
  if (!startedAt || !endedAt) return undefined;
  const start = dayjs(startedAt);
  const end = dayjs(endedAt);
  if (!start.isValid() || !end.isValid()) return undefined;
  const duration = end.diff(start);
  return duration >= 0 ? duration : undefined;
};

export const normalizeClientCount = (
  payload: ClientCountPayload,
): HomeClientOverview | undefined => {
  if (typeof payload === 'number') {
    return Number.isFinite(payload) ? { total: payload } : undefined;
  }
  if (!payload || typeof payload !== 'object') return undefined;

  const record = payload as Record<string, unknown>;
  const online = firstNumber(record, [
    'online',
    'onlineCount',
    'active',
    'activeCount',
  ]);
  const offline = firstNumber(record, ['offline', 'offlineCount']);
  const total =
    firstNumber(record, ['total', 'count', 'executorCount']) ??
    (online !== undefined && offline !== undefined ? online + offline : undefined);

  if (total === undefined) return undefined;
  return {
    total,
    ...(online === undefined ? {} : { online }),
    ...(offline === undefined ? {} : { offline }),
  };
};

const fetchHomeDataSourceOverview = async (): Promise<HomeDataSourceOverview> => {
  const response = await fetchDataSourceSummary();
  const data = response?.data;
  if (!data || typeof data.total !== 'number') {
    throw new Error('数据源汇总接口未返回有效数据');
  }
  return data;
};

const fetchHomeClientOverview = async (): Promise<HomeClientOverview> => {
  const response = await HttpUtils.get<ClientCountPayload>(CLIENT_COUNT_API);
  const data = normalizeClientCount(response?.data);
  if (!data) throw new Error('客户端统计接口未返回有效数据');
  return data;
};

const fetchHomeAlarmOverview = async (): Promise<HomeAlarmOverview> => {
  const response = await fetchAlarmRecords({ pageNo: 1, pageSize: 3 });
  const data = response?.data;
  if (!data || typeof data.total !== 'number' || !Array.isArray(data.list)) {
    throw new Error('告警记录接口未返回有效数据');
  }
  return {
    total: data.total,
    recent: data.list.map((item) => ({
      id: item.id,
      jobName: item.jobName,
      status: item.newStatus,
      severity: item.severity,
      time: item.sentTime || item.createTime,
    })),
  };
};

const fetchBatchDefinitionSnapshot = async (): Promise<BatchDefinitionSnapshot> => {
  const response = await linkupJobDefinitionApi.page({
    current: 1,
    pageSize: HOME_BATCH_DEFINITION_LIMIT,
  });
  const definitions = response?.data?.bizData;
  const total = response?.data?.pagination?.total;
  if (!Array.isArray(definitions) || typeof total !== 'number') {
    throw new Error('离线同步任务定义接口未返回有效数据');
  }
  return { definitions, total };
};

const toBatchRunItem = (
  execution: OfflineJobExecutionVO,
  definitionMap: Map<string, OfflineJobDefinitionVO>,
): HomeRunItem => {
  const definition = definitionMap.get(String(execution.jobDefinitionId));
  return {
    id: execution.id,
    type: 'batch',
    name: definition?.jobName || `离线同步 #${execution.jobDefinitionId}`,
    status: execution.status,
    startedAt: execution.startTime || execution.createTime,
    endedAt: execution.endTime,
    durationMillis: execution.durationMillis,
    path: `/sync/batch-link-up/${execution.jobDefinitionId}/detail`,
  };
};

const toWorkflowRunItem = (instance: WorkflowInstance): HomeRunItem => {
  const startedAt = instance.runStartedAt || instance.startedAt;
  return {
    id: instance.id,
    type: 'workflow',
    name: instance.name || `工作流 #${instance.definitionId}`,
    status: instance.status,
    startedAt,
    endedAt: instance.endedAt,
    durationMillis: durationBetween(startedAt, instance.endedAt),
    path: '/workflow/instances',
  };
};

export const buildExecutionOverview = (
  batchExecutions: OfflineJobExecutionVO[],
  workflowInstances: WorkflowInstance[],
  definitions: OfflineJobDefinitionVO[],
  batchTotal: number,
): HomeExecutionOverview => {
  const definitionMap = new Map<string, OfflineJobDefinitionVO>(
    definitions
      .filter((item) => item.id !== undefined && item.id !== null)
      .map((item): [string, OfflineJobDefinitionVO] => [String(item.id), item]),
  );
  const runs = [
    ...batchExecutions.map((item) => toBatchRunItem(item, definitionMap)),
    ...workflowInstances.map(toWorkflowRunItem),
  ];
  const todayRuns = runs.filter((item) => isToday(item.startedAt));

  return {
    todayTotal: todayRuns.length,
    running: runs.filter((item) =>
      RUNNING_STATUSES.has(normalizeStatus(item.status)),
    ).length,
    success: todayRuns.filter((item) =>
      SUCCESS_STATUSES.has(normalizeStatus(item.status)),
    ).length,
    failed: todayRuns.filter((item) =>
      FAILED_STATUSES.has(normalizeStatus(item.status)),
    ).length,
    recent: runs
      .slice()
      .sort((left, right) => timeValue(right.startedAt) - timeValue(left.startedAt))
      .slice(0, HOME_RECENT_RUN_LIMIT),
    batchObserved: batchExecutions.length,
    workflowObserved: workflowInstances.length,
    limited: batchTotal > batchExecutions.length,
  };
};

const fetchHomeExecutionOverview = async (
  batchDefinitionPromise: Promise<BatchDefinitionSnapshot>,
): Promise<HomeExecutionOverview> => {
  const definitionPromise = batchDefinitionPromise
    .then((snapshot) => snapshot.definitions)
    .catch(() => [] as OfflineJobDefinitionVO[]);

  const [batchResponse, workflowInstances, definitions] = await Promise.all([
    batchJobInstanceApi.page({ current: 1, pageSize: HOME_BATCH_INSTANCE_LIMIT }),
    getWorkflowInstances(),
    definitionPromise,
  ]);

  const batchExecutions = batchResponse?.data?.bizData;
  const batchTotal = batchResponse?.data?.pagination?.total;
  if (!Array.isArray(batchExecutions) || typeof batchTotal !== 'number') {
    throw new Error('离线同步运行实例接口未返回有效数据');
  }
  if (!Array.isArray(workflowInstances)) {
    throw new Error('工作流实例接口未返回有效数据');
  }

  return buildExecutionOverview(
    batchExecutions,
    workflowInstances,
    definitions,
    batchTotal,
  );
};

const isBatchSchedule = (definition: OfflineJobDefinitionVO) =>
  Boolean(
    definition.cronExpression ||
      definition.scheduleStatus ||
      definition.nextScheduleTime ||
      definition.lastScheduleTime,
  );

const toBatchScheduleItem = (
  definition: OfflineJobDefinitionVO,
): HomeScheduleItem | undefined => {
  if (
    definition.id === undefined ||
    definition.id === null ||
    definition.scheduleStatus !== 'NORMAL' ||
    !definition.nextScheduleTime
  ) {
    return undefined;
  }
  return {
    id: definition.id,
    type: 'batch',
    name: definition.jobName || `离线同步 #${definition.id}`,
    status: definition.scheduleStatus,
    cronExpression: definition.cronExpression,
    nextRunAt: definition.nextScheduleTime,
    lastRunAt: definition.lastScheduleTime,
    path: `/sync/batch-link-up/${definition.id}/detail`,
  };
};

const toWorkflowScheduleItem = (
  schedule: WorkflowSchedule,
): HomeScheduleItem | undefined => {
  if (schedule.status !== 'ONLINE' || !schedule.nextFireTime) return undefined;
  return {
    id: schedule.id,
    type: 'workflow',
    name: schedule.name || `工作流调度 #${schedule.id}`,
    status: schedule.status,
    cronExpression: schedule.cronExpression,
    nextRunAt: schedule.nextFireTime,
    lastRunAt: schedule.lastFireTime,
    timezone: schedule.timezone,
    path: '/workflow/schedules',
  };
};

const isFutureSchedule = (item: HomeScheduleItem, now: dayjs.Dayjs) => {
  const time = dayjs(item.nextRunAt);
  return time.isValid() && time.isAfter(now);
};

export const buildScheduleOverview = (
  definitions: OfflineJobDefinitionVO[],
  batchTotal: number,
  workflowSchedules: WorkflowSchedule[],
): HomeScheduleOverview => {
  const batchSchedules = definitions.filter(isBatchSchedule);
  const enabledBatch = batchSchedules.filter(
    (item) => item.scheduleStatus === 'NORMAL',
  ).length;
  const enabledWorkflow = workflowSchedules.filter(
    (item) => item.status === 'ONLINE',
  ).length;
  const total = batchSchedules.length + workflowSchedules.length;
  const enabled = enabledBatch + enabledWorkflow;
  const now = dayjs();
  const next24Hours = now.add(24, 'hour');

  const futureSchedules = [
    ...batchSchedules
      .map(toBatchScheduleItem)
      .filter((item): item is HomeScheduleItem => Boolean(item)),
    ...workflowSchedules
      .map(toWorkflowScheduleItem)
      .filter((item): item is HomeScheduleItem => Boolean(item)),
  ]
    .filter((item) => isFutureSchedule(item, now))
    .sort((left, right) => timeValue(left.nextRunAt) - timeValue(right.nextRunAt));

  return {
    total,
    enabled,
    paused: Math.max(total - enabled, 0),
    today: futureSchedules.filter((item) => isToday(item.nextRunAt)).length,
    next24h: futureSchedules.filter((item) => {
      const time = dayjs(item.nextRunAt);
      return time.isValid() && !time.isAfter(next24Hours);
    }).length,
    upcoming: futureSchedules.slice(0, HOME_UPCOMING_SCHEDULE_LIMIT),
    batchObserved: batchSchedules.length,
    workflowObserved: workflowSchedules.length,
    limited: batchTotal > definitions.length,
  };
};

const fetchHomeScheduleOverview = async (
  batchDefinitionPromise: Promise<BatchDefinitionSnapshot>,
): Promise<HomeScheduleOverview> => {
  const [batchSnapshot, workflowSchedules] = await Promise.all([
    batchDefinitionPromise,
    listWorkflowSchedules(),
  ]);
  if (!Array.isArray(workflowSchedules)) {
    throw new Error('工作流调度接口未返回有效数据');
  }
  return buildScheduleOverview(
    batchSnapshot.definitions,
    batchSnapshot.total,
    workflowSchedules,
  );
};

export async function fetchHomeOverview(): Promise<HomeOverview> {
  const batchDefinitionPromise = fetchBatchDefinitionSnapshot();
  const [
    dataSourceResult,
    clientResult,
    alarmResult,
    executionResult,
    scheduleResult,
  ] = await Promise.allSettled([
    fetchHomeDataSourceOverview(),
    fetchHomeClientOverview(),
    fetchHomeAlarmOverview(),
    fetchHomeExecutionOverview(batchDefinitionPromise),
    fetchHomeScheduleOverview(batchDefinitionPromise),
  ]);

  const unavailable: HomeDataSourceKey[] = [];
  if (dataSourceResult.status === 'rejected') unavailable.push('dataSource');
  if (clientResult.status === 'rejected') unavailable.push('client');
  if (alarmResult.status === 'rejected') unavailable.push('alarm');
  if (executionResult.status === 'rejected') unavailable.push('execution');
  if (scheduleResult.status === 'rejected') unavailable.push('schedule');

  return {
    dataSource:
      dataSourceResult.status === 'fulfilled' ? dataSourceResult.value : undefined,
    client: clientResult.status === 'fulfilled' ? clientResult.value : undefined,
    alarm: alarmResult.status === 'fulfilled' ? alarmResult.value : undefined,
    execution:
      executionResult.status === 'fulfilled' ? executionResult.value : undefined,
    schedule:
      scheduleResult.status === 'fulfilled' ? scheduleResult.value : undefined,
    unavailable,
  };
}
