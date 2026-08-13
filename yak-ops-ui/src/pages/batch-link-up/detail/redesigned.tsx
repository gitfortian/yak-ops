import {
  ArrowLeftOutlined,
  CopyOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  ReloadOutlined,
  SyncOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { history, useLocation, useParams } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  Empty,
  message,
  Select,
  Spin,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { API_SUCCESS_CODE } from '@/services/http/response';
import { BRAND_THEME } from '@/styles/brand';

import {
  batchJobInstanceApi,
  linkupJobDefinitionApi,
  linkupJobInstanceApi,
  type OfflineJobDefinitionVO,
} from '../api';

type InstanceRecord = Record<string, any>;
type DetailTabKey = 'log' | 'config' | 'metrics' | 'sync' | 'structure';

interface TableMetricRecord extends Record<string, any> {
  __key: string;
}

interface SqlRecord {
  key: string;
  title: string;
  tableName?: string;
  sql: string;
}

const RUNNING_STATUS = new Set([
  'INITIALIZING',
  'CREATED',
  'SUBMITTED',
  'QUEUED',
  'PENDING',
  'SCHEDULED',
  'RUNNING',
  'FAILING',
  'CANCELING',
]);

const SUCCESS_STATUS = new Set([
  'FINISHED',
  'COMPLETED',
  'SUCCESS',
  'SUCCEEDED',
]);

const FAILED_STATUS = new Set([
  'FAILED',
  'ERROR',
  'CANCELED',
  'CANCELLED',
  'KILLED',
  'STOPPED',
]);

const firstValue = <T,>(...values: T[]): T | undefined =>
  values.find(
    (value) => value !== undefined && value !== null && String(value) !== '',
  );

const normalizeStatus = (value?: unknown) =>
  String(value || 'UNKNOWN').trim().toUpperCase();

const getInstanceStatus = (record?: InstanceRecord | null) =>
  normalizeStatus(firstValue(record?.jobStatus, record?.status));

const statusMeta = (status?: unknown) => {
  const normalized = normalizeStatus(status);

  if (RUNNING_STATUS.has(normalized)) {
    return { label: '运行中', color: 'processing' as const };
  }
  if (SUCCESS_STATUS.has(normalized)) {
    return { label: '已完成', color: 'default' as const };
  }
  if (FAILED_STATUS.has(normalized)) {
    return {
      label: normalized === 'STOPPED' ? '已停止' : '失败',
      color: 'error' as const,
    };
  }
  if (normalized === 'PAUSED') {
    return { label: '已暂停', color: 'warning' as const };
  }
  return {
    label: normalized === 'UNKNOWN' ? '未知' : normalized,
    color: 'default' as const,
  };
};

const toNumber = (value: unknown) => {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
};

const formatNumber = (value: unknown) => toNumber(value).toLocaleString();

const formatDateTime = (value?: unknown) => {
  if (!value) return '-';
  const parsed = dayjs(String(value));
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : String(value);
};

const formatDuration = (value?: unknown) => {
  const milliseconds = toNumber(value);
  if (!milliseconds) return '-';
  if (milliseconds < 1000) return `${milliseconds} ms`;

  const seconds = Math.floor(milliseconds / 1000);
  if (seconds < 60) return `${seconds} 秒`;

  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  if (minutes < 60) return `${minutes} 分 ${restSeconds} 秒`;

  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  return `${hours} 小时 ${restMinutes} 分`;
};

const formatBytes = (value?: unknown) => {
  const bytes = toNumber(value);
  if (!bytes) return '0 B';

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1,
  );
  const result = bytes / 1024 ** index;
  return `${result >= 100 ? result.toFixed(0) : result.toFixed(2)} ${units[index]}`;
};

const normalizePayload = (response: any) => response?.data ?? response;

const normalizeInstanceList = (response: any): InstanceRecord[] => {
  const data = normalizePayload(response);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.bizData)) return data.bizData;
  if (Array.isArray(data?.records)) return data.records;
  if (Array.isArray(data?.list)) return data.list;
  return [];
};

const normalizeTableMetrics = (response: any): TableMetricRecord[] => {
  const data = normalizePayload(response);
  const list = Array.isArray(data)
    ? data
    : Array.isArray(data?.bizData)
      ? data.bizData
      : Array.isArray(data?.records)
        ? data.records
        : Array.isArray(data?.list)
          ? data.list
          : [];

  return list.map((item: InstanceRecord, index: number) => ({
    ...item,
    __key: String(
      firstValue(
        item?.id,
        item?.tableId,
        `${firstValue(item?.sourceTable, item?.sourceTableName, 'source')}-${firstValue(
          item?.sinkTable,
          item?.targetTable,
          item?.sinkTableName,
          'sink',
        )}-${index}`,
      ),
    ),
  }));
};

const stringifyConfig = (value: unknown) => {
  if (value === undefined || value === null || value === '') return '';
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
};

const formatLogContent = (response: any) => {
  const data = normalizePayload(response);
  if (!data) return '';
  if (typeof data === 'string') return data;
  if (Array.isArray(data?.logs)) return formatLogContent(data.logs);

  if (Array.isArray(data)) {
    return data
      .map((item) => {
        if (typeof item === 'string') return item;
        const header = [
          item?.node ? `# Node: ${item.node}` : '',
          item?.logName ? `# File: ${item.logName}` : '',
          item?.logLink ? `# Link: ${item.logLink}` : '',
        ]
          .filter(Boolean)
          .join('\n');
        const content = firstValue(
          item?.content,
          item?.logContent,
          item?.log,
          item?.message,
          item?.data,
        );
        return [header, content ? String(content) : JSON.stringify(item, null, 2)]
          .filter(Boolean)
          .join('\n\n');
      })
      .filter(Boolean)
      .join('\n\n');
  }

  const content = firstValue(
    data?.content,
    data?.logContent,
    data?.log,
    data?.message,
  );
  return content ? String(content) : JSON.stringify(data, null, 2);
};

const copyText = async (value: unknown, successText = '已复制') => {
  const text = String(value ?? '');
  if (!text) return;

  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    }
    message.success(successText);
  } catch {
    message.error('复制失败，请手动复制');
  }
};

const DetailField = ({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: ReactNode;
  mono?: boolean;
}) => (
  <div className="min-w-0 rounded-md bg-[#f7f7f8] px-4 py-3">
    <div className="text-[11px] leading-4 text-[#8a8f98]">{label}</div>
    <div
      className={[
        'mt-1.5 min-h-5 break-words text-[13px] font-medium leading-5 text-[#30343b]',
        mono ? 'font-mono text-[12px]' : '',
      ].join(' ')}
    >
      {value ?? '-'}
    </div>
  </div>
);

const MetricTile = ({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
}) => (
  <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
    <div className="text-[12px] leading-4 text-[#7c828c]">{label}</div>
    <div className="mt-2 text-[20px] font-semibold leading-7 tracking-[-0.02em] text-[#161823]">
      {value}
    </div>
    {hint ? <div className="mt-1 text-[11px] text-[#9aa0aa]">{hint}</div> : null}
  </div>
);

const SectionCard = ({
  title,
  extra,
  children,
  className = '',
}: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section
    className={`min-w-0 rounded-lg border border-solid border-[#eceef1] bg-white ${className}`}
  >
    <div className="flex min-h-[52px] items-center justify-between gap-4 border-0 border-b border-solid border-[#f0f1f3] px-5">
      <div className="text-[15px] font-semibold text-[#161823]">{title}</div>
      {extra}
    </div>
    {children}
  </section>
);

export default function BatchLinkUpExecutionDetailPage() {
  const routeParams = useParams<{ id?: string }>();
  const location = useLocation();
  const taskId = routeParams.id ? decodeURIComponent(routeParams.id) : '';

  const queryParams = useMemo(
    () => new URLSearchParams(location.search),
    [location.search],
  );
  const requestedInstanceId = queryParams.get('instanceId') || '';
  const requestedTab = queryParams.get('tab');
  const initialTab: DetailTabKey =
    requestedTab === 'config' ||
    requestedTab === 'metrics' ||
    requestedTab === 'sync' ||
    requestedTab === 'structure'
      ? requestedTab
      : 'log';

  const [definition, setDefinition] = useState<OfflineJobDefinitionVO | null>(null);
  const [instances, setInstances] = useState<InstanceRecord[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState('');
  const [instanceDetail, setInstanceDetail] = useState<InstanceRecord | null>(null);
  const [tableMetrics, setTableMetrics] = useState<TableMetricRecord[]>([]);
  const [logContent, setLogContent] = useState('');

  const [pageLoading, setPageLoading] = useState(true);
  const [instanceLoading, setInstanceLoading] = useState(false);
  const [logLoading, setLogLoading] = useState(false);
  const [metricsLoading, setMetricsLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<DetailTabKey>(initialTab);

  const updateRouteState = useCallback(
    (instanceId?: string, tab?: DetailTabKey) => {
      if (!taskId) return;
      const params = new URLSearchParams();
      if (instanceId) params.set('instanceId', instanceId);
      if (tab && tab !== 'log') params.set('tab', tab);
      const search = params.toString();
      history.replace(
        `/sync/batch-link-up/${encodeURIComponent(taskId)}/detail${
          search ? `?${search}` : ''
        }`,
      );
    },
    [taskId],
  );

  const loadPage = useCallback(async () => {
    if (!taskId) return;

    setPageLoading(true);
    try {
      const [definitionResponse, instanceResponse] = await Promise.all([
        linkupJobDefinitionApi.selectById(taskId),
        linkupJobInstanceApi.page({
          pageNum: 1,
          pageSize: 100,
          jobDefinitionId: taskId,
        }),
      ]);

      if (
        definitionResponse?.code !== API_SUCCESS_CODE ||
        !definitionResponse?.data
      ) {
        throw new Error(definitionResponse?.message || '获取离线同步任务失败');
      }

      setDefinition(definitionResponse.data);
      const nextInstances = normalizeInstanceList(instanceResponse);
      setInstances(nextInstances);

      const nextSelectedId = String(
        requestedInstanceId ||
          selectedInstanceId ||
          firstValue(nextInstances[0]?.id, nextInstances[0]?.instanceId) ||
          '',
      );
      setSelectedInstanceId(nextSelectedId);

      if (nextSelectedId && nextSelectedId !== requestedInstanceId) {
        updateRouteState(nextSelectedId, activeTab);
      }
    } catch (error: any) {
      message.error(error?.message || '获取离线同步详情失败');
      setDefinition(null);
      setInstances([]);
      setSelectedInstanceId('');
    } finally {
      setPageLoading(false);
    }
  }, [
    activeTab,
    requestedInstanceId,
    selectedInstanceId,
    taskId,
    updateRouteState,
  ]);

  useEffect(() => {
    if (!taskId) {
      history.replace('/sync/batch-link-up');
      return;
    }
    void loadPage();
    // 仅在任务 ID 变化时加载任务和实例，实例切换在前端完成。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId]);

  useEffect(() => {
    if (requestedInstanceId && requestedInstanceId !== selectedInstanceId) {
      setSelectedInstanceId(requestedInstanceId);
    }
  }, [requestedInstanceId, selectedInstanceId]);

  useEffect(() => {
    if (
      requestedTab === 'log' ||
      requestedTab === 'config' ||
      requestedTab === 'metrics' ||
      requestedTab === 'sync' ||
      requestedTab === 'structure'
    ) {
      setActiveTab(requestedTab);
    }
  }, [requestedTab]);

  const loadInstanceDetail = useCallback(async () => {
    if (!selectedInstanceId) {
      setInstanceDetail(null);
      return;
    }

    setInstanceLoading(true);
    try {
      const response = await linkupJobInstanceApi.selectById(selectedInstanceId);
      if (response?.code === API_SUCCESS_CODE && response?.data) {
        const detail = response.data;
        setInstanceDetail(detail);
        setInstances((previous) => {
          const detailId = String(firstValue(detail?.id, detail?.instanceId) || '');
          if (!detailId) return previous;
          const exists = previous.some(
            (item) =>
              String(firstValue(item?.id, item?.instanceId) || '') === detailId,
          );
          return exists ? previous : [detail, ...previous];
        });
        return;
      }

      const fallback = instances.find(
        (item) =>
          String(firstValue(item?.id, item?.instanceId)) === selectedInstanceId,
      );
      setInstanceDetail(fallback || null);
    } catch {
      const fallback = instances.find(
        (item) =>
          String(firstValue(item?.id, item?.instanceId)) === selectedInstanceId,
      );
      setInstanceDetail(fallback || null);
    } finally {
      setInstanceLoading(false);
    }
  }, [instances, selectedInstanceId]);

  useEffect(() => {
    void loadInstanceDetail();
  }, [loadInstanceDetail]);

  const loadLog = useCallback(async () => {
    if (!selectedInstanceId) {
      setLogContent('');
      return;
    }

    setLogLoading(true);
    try {
      const response = await linkupJobInstanceApi.getLog(selectedInstanceId);
      if (response?.code !== API_SUCCESS_CODE) {
        setLogContent(response?.message || '日志加载失败');
        return;
      }
      setLogContent(formatLogContent(response) || '当前实例暂无运行日志');
    } catch (error: any) {
      setLogContent(error?.message || '日志加载失败');
    } finally {
      setLogLoading(false);
    }
  }, [selectedInstanceId]);

  useEffect(() => {
    if (activeTab === 'log') void loadLog();
  }, [activeTab, loadLog]);

  const loadTableMetrics = useCallback(async () => {
    if (!selectedInstanceId) {
      setTableMetrics([]);
      return;
    }

    setMetricsLoading(true);
    try {
      const response = await batchJobInstanceApi.tableMetrics(selectedInstanceId);
      setTableMetrics(normalizeTableMetrics(response));
    } catch {
      setTableMetrics([]);
    } finally {
      setMetricsLoading(false);
    }
  }, [selectedInstanceId]);

  useEffect(() => {
    void loadTableMetrics();
  }, [loadTableMetrics]);

  const handleSelectInstance = (id: string) => {
    if (!id || id === selectedInstanceId) return;
    const selected = instances.find(
      (item) => String(firstValue(item?.id, item?.instanceId) || '') === id,
    );
    setSelectedInstanceId(id);
    setInstanceDetail(selected || null);
    setLogContent('');
    setTableMetrics([]);
    updateRouteState(id, activeTab);
  };

  const handleTabChange = (key: string) => {
    const nextKey = key as DetailTabKey;
    setActiveTab(nextKey);
    updateRouteState(selectedInstanceId, nextKey);
  };

  const currentInstance = useMemo(() => {
    if (instanceDetail) return instanceDetail;
    return (
      instances.find(
        (item) =>
          String(firstValue(item?.id, item?.instanceId)) === selectedInstanceId,
      ) || null
    );
  }, [instanceDetail, instances, selectedInstanceId]);

  const mergedInstance = useMemo<InstanceRecord>(
    () => ({
      ...((definition || {}) as InstanceRecord),
      ...(currentInstance || {}),
    }),
    [currentInstance, definition],
  );

  const sourceType = firstValue(mergedInstance?.sourceType, definition?.sourceType);
  const sinkType = firstValue(mergedInstance?.sinkType, definition?.sinkType);
  const sourceTable = firstValue(
    mergedInstance?.sourceTable,
    mergedInstance?.sourceTableName,
    definition?.sourceTable,
  );
  const sinkTable = firstValue(
    mergedInstance?.sinkTable,
    mergedInstance?.targetTable,
    mergedInstance?.sinkTableName,
    definition?.sinkTable,
  );

  const readRows = firstValue(
    mergedInstance?.readRowCount,
    mergedInstance?.sourceRecordCount,
    0,
  );
  const writeRows = firstValue(
    mergedInstance?.writeRowCount,
    mergedInstance?.sinkSuccessRecordCount,
    0,
  );
  const durationMillis = firstValue(
    mergedInstance?.durationMillis,
    mergedInstance?.duration,
    0,
  );
  const qps = firstValue(
    mergedInstance?.qps,
    mergedInstance?.writeQps,
    mergedInstance?.readQps,
    0,
  );

  const runtimeConfig = stringifyConfig(
    firstValue(
      currentInstance?.runtimeConfig,
      currentInstance?.jobConfig,
      currentInstance?.config,
      definition?.jobDefinitionInfo,
    ),
  );

  const tableRows = useMemo<TableMetricRecord[]>(() => {
    if (tableMetrics.length > 0) return tableMetrics;
    if (!sourceTable && !sinkTable) return [];

    return [
      {
        __key: 'instance-summary',
        sourceTable,
        sinkTable,
        readRowCount: readRows,
        writeRowCount: writeRows,
        readQps: mergedInstance?.readQps,
        writeQps: mergedInstance?.writeQps || qps,
        status: getInstanceStatus(mergedInstance),
      },
    ];
  }, [
    mergedInstance,
    qps,
    readRows,
    sinkTable,
    sourceTable,
    tableMetrics,
    writeRows,
  ]);

  const sqlRecords = useMemo<SqlRecord[]>(() => {
    const records: SqlRecord[] = [];
    const used = new Set<string>();

    const append = (title: string, sql: unknown, tableName?: unknown) => {
      if (!sql || typeof sql !== 'string' || used.has(sql)) return;
      used.add(sql);
      records.push({
        key: `${title}-${records.length}`,
        title,
        tableName: tableName ? String(tableName) : undefined,
        sql,
      });
    };

    append(
      '实例建表语句',
      firstValue(
        currentInstance?.createTableSql,
        currentInstance?.targetCreateTableSql,
        currentInstance?.migrationSql,
        currentInstance?.schemaSql,
        currentInstance?.ddl,
      ),
      sinkTable,
    );

    tableRows.forEach((item, index) => {
      append(
        `表结构迁移 ${index + 1}`,
        firstValue(
          item?.createTableSql,
          item?.targetCreateTableSql,
          item?.migrationSql,
          item?.schemaSql,
          item?.ddl,
        ),
        firstValue(item?.sinkTable, item?.targetTable, item?.sourceTable),
      );
    });

    return records;
  }, [currentInstance, sinkTable, tableRows]);

  const instanceOptions = useMemo(
    () =>
      instances.map((item) => {
        const id = String(firstValue(item?.id, item?.instanceId) || '');
        const meta = statusMeta(getInstanceStatus(item));
        const runTime = formatDateTime(firstValue(item?.startTime, item?.createTime));
        return {
          value: id,
          label: `实例 #${id || '-'} · ${meta.label} · ${runTime}`,
        };
      }),
    [instances],
  );

  const tableColumns = useMemo<ColumnsType<TableMetricRecord>>(
    () => [
      {
        title: '来源表',
        dataIndex: 'sourceTable',
        minWidth: 190,
        render: (_value, record) => (
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#30343b]">
              {firstValue(record?.sourceTable, record?.sourceTableName, '-')}
            </div>
            <div className="mt-0.5 text-[11px] text-[#9aa0aa]">来源</div>
          </div>
        ),
      },
      {
        title: '目标表',
        dataIndex: 'sinkTable',
        minWidth: 190,
        render: (_value, record) => (
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#30343b]">
              {firstValue(
                record?.sinkTable,
                record?.targetTable,
                record?.sinkTableName,
                '-',
              )}
            </div>
            <div className="mt-0.5 text-[11px] text-[#9aa0aa]">目标</div>
          </div>
        ),
      },
      {
        title: '读取行数',
        dataIndex: 'readRowCount',
        width: 120,
        align: 'right',
        render: (_value, record) =>
          formatNumber(firstValue(record?.readRowCount, record?.sourceRecordCount, 0)),
      },
      {
        title: '写入行数',
        dataIndex: 'writeRowCount',
        width: 120,
        align: 'right',
        render: (_value, record) =>
          formatNumber(
            firstValue(record?.writeRowCount, record?.sinkSuccessRecordCount, 0),
          ),
      },
      {
        title: '读取 QPS',
        dataIndex: 'readQps',
        width: 105,
        align: 'right',
        render: (value) => formatNumber(value),
      },
      {
        title: '写入 QPS',
        dataIndex: 'writeQps',
        width: 105,
        align: 'right',
        render: (value) => formatNumber(value),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value) => {
          const meta = statusMeta(value);
          return (
            <Tag color={meta.color} className="!m-0">
              {meta.label}
            </Tag>
          );
        },
      },
    ],
    [],
  );

  if (pageLoading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Spin size="large" />
      </div>
    );
  }

  if (!definition) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Empty description="未找到离线同步任务">
          <Button onClick={() => history.push('/sync/batch-link-up')}>
            返回任务列表
          </Button>
        </Empty>
      </div>
    );
  }

  const currentStatus = statusMeta(getInstanceStatus(mergedInstance));
  const errorMessage = firstValue(
    currentInstance?.errorMessage,
    currentInstance?.lastErrorMessage,
    definition?.lastErrorMessage,
  );

  const metricSummary = (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
      <MetricTile label="读取行数" value={formatNumber(readRows)} />
      <MetricTile label="写入行数" value={formatNumber(writeRows)} />
      <MetricTile label="平均 QPS" value={formatNumber(qps)} />
      <MetricTile
        label="读取数据量"
        value={formatBytes(currentInstance?.sourceReadBytes)}
      />
      <MetricTile
        label="写入数据量"
        value={formatBytes(currentInstance?.sinkWrittenBytes)}
      />
      <MetricTile label="运行耗时" value={formatDuration(durationMillis)} />
    </div>
  );

  const tabItems = [
    {
      key: 'log',
      label: '运行日志',
      children: (
        <div className="grid gap-3 xl:grid-cols-[minmax(0,1.7fr)_minmax(320px,0.8fr)]">
          <SectionCard
            title="运行日志"
            extra={
              <Button
                size="small"
                type="text"
                icon={<ReloadOutlined />}
                loading={logLoading}
                className="!text-[#667085]"
                onClick={() => void loadLog()}
              >
                刷新
              </Button>
            }
          >
            <div className="px-5 pb-5 pt-4">
              <div className="mb-3 text-[12px] leading-5 text-[#8a8f98]">
                当前实例由 Yak Ops 聚合的 Link-Up 运行日志
              </div>
              <div className="overflow-hidden rounded-md bg-[#181a1f]">
                {logLoading ? (
                  <div className="flex min-h-[360px] items-center justify-center text-white/60">
                    <Spin size="small" />
                  </div>
                ) : (
                  <pre className="m-0 max-h-[560px] min-h-[360px] overflow-auto whitespace-pre-wrap break-words p-4 font-mono text-[12px] leading-5 text-[#d6d9df]">
                    {logContent || '当前实例暂无运行日志'}
                  </pre>
                )}
              </div>
            </div>
          </SectionCard>

          <SectionCard title="执行概览">
            <div className="p-5">{metricSummary}</div>
          </SectionCard>
        </div>
      ),
    },
    {
      key: 'config',
      label: '运行配置',
      children: (
        <SectionCard title="运行配置">
          <div className="p-5">
            {runtimeConfig ? (
              <pre className="m-0 max-h-[620px] min-h-[360px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f7f7f8] p-4 font-mono text-[12px] leading-5 text-[#30343b]">
                {runtimeConfig}
              </pre>
            ) : (
              <div className="flex min-h-[320px] items-center justify-center">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="当前实例未返回运行配置"
                />
              </div>
            )}
          </div>
        </SectionCard>
      ),
    },
    {
      key: 'metrics',
      label: '执行指标',
      children: (
        <div className="grid gap-3 xl:grid-cols-2">
          <SectionCard title="数据吞吐">
            <div className="grid grid-cols-1 gap-3 p-5 sm:grid-cols-3">
              <MetricTile label="读取行数" value={formatNumber(readRows)} />
              <MetricTile label="写入行数" value={formatNumber(writeRows)} />
              <MetricTile label="平均 QPS" value={formatNumber(qps)} />
            </div>
          </SectionCard>
          <SectionCard title="运行资源">
            <div className="grid grid-cols-1 gap-3 p-5 sm:grid-cols-3">
              <MetricTile
                label="读取数据量"
                value={formatBytes(currentInstance?.sourceReadBytes)}
              />
              <MetricTile
                label="写入数据量"
                value={formatBytes(currentInstance?.sinkWrittenBytes)}
              />
              <MetricTile label="运行耗时" value={formatDuration(durationMillis)} />
            </div>
          </SectionCard>
        </div>
      ),
    },
    {
      key: 'sync',
      label: '同步情况',
      children: (
        <SectionCard
          title={
            <span className="flex items-center gap-2">
              <TableOutlined className="text-[#7c828c]" />
              表级同步结果
            </span>
          }
          extra={<span className="text-[12px] text-[#8a8f98]">{tableRows.length} 张表</span>}
        >
          <div className="p-5">
            <Table<TableMetricRecord>
              rowKey="__key"
              size="small"
              loading={metricsLoading}
              columns={tableColumns}
              dataSource={tableRows}
              pagination={false}
              scroll={{ x: 920 }}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="当前实例暂无表级同步指标"
                  />
                ),
              }}
              className="[&_.ant-table-container]:!rounded-md [&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid [&_.ant-table-container]:!border-[#eceef1] [&_.ant-table-thead>tr>th]:!h-10 [&_.ant-table-thead>tr>th]:!bg-[#f7f7f8] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-tbody>tr>td]:!py-3 [&_.ant-table-tbody>tr>td]:!text-[12px]"
            />
          </div>
        </SectionCard>
      ),
    },
    {
      key: 'structure',
      label: '结构迁移',
      children: (
        <SectionCard title="结构迁移">
          <div className="p-5">
            {sqlRecords.length > 0 ? (
              <div className="space-y-3">
                {sqlRecords.map((item) => (
                  <div
                    key={item.key}
                    className="overflow-hidden rounded-md border border-solid border-[#eceef1]"
                  >
                    <div className="flex min-h-11 items-center justify-between gap-3 bg-[#f7f7f8] px-4">
                      <div className="min-w-0">
                        <span className="text-[12px] font-medium text-[#30343b]">
                          {item.title}
                        </span>
                        {item.tableName ? (
                          <span className="ml-2 text-[11px] text-[#9aa0aa]">
                            {item.tableName}
                          </span>
                        ) : null}
                      </div>
                      <Button
                        type="text"
                        size="small"
                        icon={<CopyOutlined />}
                        className="!text-[#667085]"
                        onClick={() => void copyText(item.sql, '建表语句已复制')}
                      >
                        复制
                      </Button>
                    </div>
                    <pre className="m-0 max-h-[420px] overflow-auto whitespace-pre-wrap break-words bg-[#181a1f] p-4 font-mono text-[12px] leading-5 text-[#d6d9df]">
                      {item.sql}
                    </pre>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex min-h-[300px] flex-col items-center justify-center rounded-md bg-[#f7f7f8] px-6 text-center">
                <FileTextOutlined className="text-[28px] text-[#c0c4cc]" />
                <div className="mt-3 text-[13px] font-medium text-[#667085]">
                  暂无结构迁移语句
                </div>
                <div className="mt-1 max-w-[560px] text-[12px] leading-5 text-[#9aa0aa]">
                  当前 Link-Up 实例接口尚未返回目标表建表语句。接口补充 createTableSql、ddl 或 migrationSql 后，本页会自动展示并支持复制。
                </div>
              </div>
            )}
          </div>
        </SectionCard>
      ),
    },
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f7f8] text-[#161823]">
        <div className="flex min-h-[56px] items-center gap-2 border-0 border-b border-solid border-[#eceef1] bg-white px-5">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            className="!h-8 !w-8 !min-w-0 !p-0 !text-[#30343b]"
            onClick={() => history.push('/sync/batch-link-up')}
          />
          <h1 className="m-0 text-[16px] font-semibold text-[#161823]">离线同步详情</h1>
        </div>

        <div className="mx-auto w-full max-w-[1800px] px-4 pb-8 pt-3 lg:px-5">
          <section className="rounded-lg border border-solid border-[#eceef1] bg-white">
            <div className="flex flex-col gap-5 px-5 py-5 lg:px-6">
              <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-start">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="m-0 truncate text-[20px] font-semibold leading-7 text-[#161823]">
                      {definition.jobName || '离线同步任务'}
                    </h2>
                    <Tag bordered={false} className="!m-0 !bg-[#f2f3f5] !text-[#667085]">
                      {definition.mode || 'BATCH'}
                    </Tag>
                    {currentInstance ? (
                      <Tag color={currentStatus.color} className="!m-0">
                        {currentStatus.label}
                      </Tag>
                    ) : null}
                  </div>

                  <div className="mt-1.5 flex items-center gap-1 text-[12px] text-[#8a8f98]">
                    <span className="truncate">任务定义 ID：{definition.id || taskId}</span>
                    <Tooltip title="复制任务定义 ID">
                      <Button
                        type="text"
                        size="small"
                        icon={<CopyOutlined />}
                        className="!h-5 !w-5 !min-w-0 !p-0 !text-[#9aa0aa]"
                        onClick={() =>
                          void copyText(definition.id || taskId, '任务定义 ID 已复制')
                        }
                      />
                    </Tooltip>
                  </div>
                </div>

                <div className="flex flex-wrap items-end gap-3 lg:justify-end">
                  <div className="min-w-[280px]">
                    <div className="mb-1.5 flex items-center justify-between text-[11px] text-[#8a8f98]">
                      <span>执行实例</span>
                      <span>共 {instances.length} 次运行</span>
                    </div>
                    <Select
                      showSearch
                      allowClear={false}
                      value={selectedInstanceId || undefined}
                      options={instanceOptions}
                      optionFilterProp="label"
                      placeholder="请选择执行实例"
                      className="w-full"
                      onChange={handleSelectInstance}
                      notFoundContent="暂无执行实例"
                    />
                  </div>
                  <Tooltip title="刷新实例">
                    <Button
                      icon={<ReloadOutlined />}
                      onClick={() => void loadPage()}
                    />
                  </Tooltip>
                  <div className="min-w-[90px] pb-0.5 text-right">
                    <div className="text-[11px] text-[#8a8f98]">运行耗时</div>
                    <div className="mt-1 text-[16px] font-semibold text-[#30343b]">
                      {formatDuration(durationMillis)}
                    </div>
                  </div>
                </div>
              </div>

              {currentInstance ? (
                <>
                  <div className="flex min-w-0 items-center gap-3 rounded-md bg-[#f7f7f8] px-4 py-3.5">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-white text-[#667085]">
                      <DatabaseOutlined />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-[13px] font-medium text-[#30343b]">
                        <span className="truncate">
                          {firstValue(
                            definition?.sourceDatasourceName,
                            sourceType,
                            '来源数据源',
                          )}
                          {sourceTable ? ` / ${sourceTable}` : ''}
                        </span>
                        <SyncOutlined className="shrink-0 text-[#9aa0aa]" />
                        <span className="truncate">
                          {firstValue(
                            definition?.sinkDatasourceName,
                            sinkType,
                            '目标数据源',
                          )}
                          {sinkTable ? ` / ${sinkTable}` : ''}
                        </span>
                      </div>
                      <div className="mt-0.5 text-[11px] text-[#9aa0aa]">
                        {sourceType || '-'} → {sinkType || '-'}
                      </div>
                    </div>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    <DetailField
                      label="引擎任务 ID"
                      mono
                      value={firstValue(
                        currentInstance?.engineJobId,
                        currentInstance?.externalExecutionId,
                        '-',
                      )}
                    />
                    <DetailField
                      label="执行节点"
                      value={firstValue(
                        currentInstance?.workerNodeName,
                        currentInstance?.engineNodeId,
                        currentInstance?.workerInstanceId,
                        '-',
                      )}
                    />
                    <DetailField
                      label="开始时间"
                      value={formatDateTime(currentInstance?.startTime)}
                    />
                    <DetailField
                      label="结束时间"
                      value={formatDateTime(currentInstance?.endTime)}
                    />
                  </div>

                  {errorMessage ? (
                    <div className="rounded-md bg-[#fff5f5] px-4 py-3 text-[12px] leading-5 text-[#d92d20]">
                      <span className="font-medium">错误信息：</span>
                      {String(errorMessage)}
                    </div>
                  ) : null}
                </>
              ) : null}
            </div>
          </section>

          <div className="mt-3 rounded-lg border border-solid border-[#eceef1] bg-white px-5">
            <Tabs
              activeKey={activeTab}
              onChange={handleTabChange}
              items={tabItems.map(({ key, label }) => ({ key, label }))}
              className="[&_.ant-tabs-nav]:!mb-0 [&_.ant-tabs-nav]:!min-h-[48px] [&_.ant-tabs-tab]:!py-3.5"
            />
          </div>

          <div className="mt-3">
            {!selectedInstanceId || !currentInstance ? (
              <SectionCard title="执行详情">
                <div className="flex min-h-[360px] items-center justify-center">
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="暂无执行实例，请先运行一次离线同步任务"
                  />
                </div>
              </SectionCard>
            ) : instanceLoading ? (
              <SectionCard title="执行详情">
                <div className="flex min-h-[360px] items-center justify-center">
                  <Spin />
                </div>
              </SectionCard>
            ) : (
              tabItems.find((item) => item.key === activeTab)?.children
            )}
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
