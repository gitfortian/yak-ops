import HttpUtils from '@/utils/HttpUtils';
import { fetchAlarmRecords } from '../alarm/service';
import { fetchDataSourceSummary } from '../data-source/service';
import type {
  HomeAlarmOverview,
  HomeClientOverview,
  HomeDataSourceKey,
  HomeDataSourceOverview,
  HomeOverview,
} from './model';

const CLIENT_COUNT_API = '/api/v1/datax/executor/count';

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

const asFiniteNumber = (value: unknown): number | undefined => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return undefined;
  }
  return value;
};

const firstNumber = (
  payload: Record<string, unknown>,
  keys: string[],
): number | undefined => {
  for (const key of keys) {
    const value = asFiniteNumber(payload[key]);
    if (value !== undefined) {
      return value;
    }
  }
  return undefined;
};

export const normalizeClientCount = (
  payload: ClientCountPayload,
): HomeClientOverview | undefined => {
  if (typeof payload === 'number') {
    return Number.isFinite(payload) ? { total: payload } : undefined;
  }

  if (!payload || typeof payload !== 'object') {
    return undefined;
  }

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

  if (total === undefined) {
    return undefined;
  }

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

  if (!data) {
    throw new Error('客户端统计接口未返回有效数据');
  }

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

export async function fetchHomeOverview(): Promise<HomeOverview> {
  const [dataSourceResult, clientResult, alarmResult] = await Promise.allSettled([
    fetchHomeDataSourceOverview(),
    fetchHomeClientOverview(),
    fetchHomeAlarmOverview(),
  ]);

  const unavailable: HomeDataSourceKey[] = [];

  if (dataSourceResult.status === 'rejected') unavailable.push('dataSource');
  if (clientResult.status === 'rejected') unavailable.push('client');
  if (alarmResult.status === 'rejected') unavailable.push('alarm');

  return {
    dataSource:
      dataSourceResult.status === 'fulfilled' ? dataSourceResult.value : undefined,
    client: clientResult.status === 'fulfilled' ? clientResult.value : undefined,
    alarm: alarmResult.status === 'fulfilled' ? alarmResult.value : undefined,
    unavailable,
  };
}
