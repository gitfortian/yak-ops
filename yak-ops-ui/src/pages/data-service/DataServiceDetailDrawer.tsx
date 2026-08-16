import {
  Button,
  Drawer,
  Empty,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import {
  Activity,
  Copy,
  FileText,
  Gauge,
  KeyRound,
  RefreshCw,
  ServerCog,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import DataServiceAccessModal from './DataServiceAccessModal';
import DataServiceDocsModal from './DataServiceDocsModal';
import DataServiceRuntimeModal from './DataServiceRuntimeModal';
import {
  DATA_SERVICE_NODE_SOURCE,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
  fetchDataServiceKeys,
  fetchDataServiceLogs,
  fetchDataServiceRuntime,
  republishDataService,
  type DataServiceApi,
  type DataServiceApiKey,
  type DataServiceCallLog,
  type DataServiceRuntimeStatus,
  type DataSourceOption,
} from './service';

interface DataServiceDetailDrawerProps {
  open: boolean;
  service?: DataServiceApi;
  dataSources: DataSourceOption[];
  onClose: () => void;
  onChanged: () => Promise<void> | void;
}

const formatTime = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 19) : '-';
const percent = (value?: number) => `${Math.round((value || 0) * 1000) / 10}%`;

const callerLabel = (record: DataServiceCallLog) => {
  if (record.callerType === 'CONSOLE') return '控制台测试';
  if (record.callerType === 'API_KEY') return record.apiKeyName || 'API Key';
  if (record.callerType === 'PUBLIC') return '公开调用';
  return '历史调用';
};

const latestActivity = (runtime?: DataServiceRuntimeStatus) => {
  const values = [runtime?.lastSuccessAt, runtime?.lastFailureAt].filter(Boolean) as string[];
  if (!values.length) return '-';
  return formatTime(values.sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0]);
};

const Metric = ({ label, value }: { label: string; value: string | number }) => (
  <div className="border-r border-[#edf0f2] px-4 last:border-r-0 first:pl-0">
    <div className="text-[11px] text-[#98a2b3]">{label}</div>
    <div className="mt-1 text-[18px] font-semibold text-[#1d2939]">{value}</div>
  </div>
);

const SectionTitle = ({ children }: { children: React.ReactNode }) => (
  <div className="mb-3 text-[12px] font-semibold text-[#344054]">{children}</div>
);

export default function DataServiceDetailDrawer({
  open,
  service,
  dataSources,
  onClose,
  onChanged,
}: DataServiceDetailDrawerProps) {
  const [activeTab, setActiveTab] = useState('overview');
  const [loading, setLoading] = useState(false);
  const [republishing, setRepublishing] = useState(false);
  const [runtime, setRuntime] = useState<DataServiceRuntimeStatus>();
  const [keys, setKeys] = useState<DataServiceApiKey[]>([]);
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [docsOpen, setDocsOpen] = useState(false);
  const [accessOpen, setAccessOpen] = useState(false);
  const [runtimeOpen, setRuntimeOpen] = useState(false);

  const sourceManaged = service?.sourceType === DATA_SERVICE_NODE_SOURCE;
  const legacySqlRelease = service?.sourceType === LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE;

  const dataSourceName = useMemo(() => {
    if (!service?.dataSourceId) return '-';
    return dataSources.find((item) => String(item.value) === String(service.dataSourceId))?.label
      || `#${service.dataSourceId}`;
  }, [dataSources, service?.dataSourceId]);

  const serviceLogs = useMemo(
    () => logs.filter((item) => item.apiId === service?.id).slice(0, 50),
    [logs, service?.id],
  );

  const activeKeys = useMemo(
    () => keys.filter((item) => item.enabled).length,
    [keys],
  );

  const loadDetail = useCallback(async () => {
    if (!service?.id) return;
    setLoading(true);
    try {
      const [runtimeResponse, keyResponse, logResponse] = await Promise.all([
        fetchDataServiceRuntime(service.id),
        fetchDataServiceKeys(service.id),
        fetchDataServiceLogs(),
      ]);
      setRuntime(runtimeResponse.data);
      setKeys(keyResponse.data || []);
      setLogs(logResponse.data || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API 运行信息失败');
    } finally {
      setLoading(false);
    }
  }, [service?.id]);

  useEffect(() => {
    if (!open || !service) return;
    setActiveTab('overview');
    void loadDetail();
  }, [loadDetail, open, service]);

  const copy = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      message.success('Endpoint 已复制');
    } catch {
      message.warning('复制失败，请手动复制');
    }
  };

  const syncLatestRevision = async () => {
    if (!service || !sourceManaged) return;
    setRepublishing(true);
    try {
      const response = await republishDataService(service.id);
      const revisionNo = response.data?.sourceRevisionNo;
      message.success(revisionNo ? `Runtime 已同步到 DS R${revisionNo}` : 'Runtime 已同步最新 DS Revision');
      await onChanged();
      await loadDetail();
    } catch (error: any) {
      message.error(error?.message || '同步最新 Data Service Revision 失败');
    } finally {
      setRepublishing(false);
    }
  };

  if (!service) return null;

  const sourceTypeLabel = sourceManaged
    ? 'Data Service Node'
    : legacySqlRelease
      ? 'Legacy SQL Release'
      : 'Legacy';
  const sourceRevisionLabel = sourceManaged
    ? `DS R${service.sourceRevisionNo || '-'}`
    : legacySqlRelease
      ? `SQL v${service.sourceRevisionNo || '-'}`
      : '-';

  const logColumns: TableColumnsType<DataServiceCallLog> = [
    {
      title: '调用方',
      key: 'caller',
      width: 150,
      render: (_, record) => (
        <div>
          <div className="text-[#475467]">{callerLabel(record)}</div>
          {record.apiKeyPrefix ? (
            <div className="mt-0.5 font-mono text-[10px] text-[#98a2b3]">{record.apiKeyPrefix}••••</div>
          ) : null}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'success',
      width: 76,
      render: (value: boolean) => value
        ? <Tag bordered={false}>成功</Tag>
        : <Tag bordered={false}>失败</Tag>,
    },
    { title: '耗时', dataIndex: 'durationMs', width: 88, render: (value) => `${value ?? 0} ms` },
    { title: '行数', dataIndex: 'rowCount', width: 70 },
    {
      title: '错误',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (value) => value
        ? <Tooltip title={value}><span className="text-[#b42318]">{value}</span></Tooltip>
        : <span className="text-black/20">-</span>,
    },
    { title: '时间', dataIndex: 'createTime', width: 150, render: formatTime },
  ];

  const overview = (
    <div className="space-y-6">
      <div>
        <SectionTitle>Endpoint</SectionTitle>
        <div className="border border-[#e5e7eb] bg-[#fafafa] px-4 py-3">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <Tag bordered={false}>GET</Tag>
                <span className="truncate font-mono text-[12px] text-[#344054]">{service.runtimePath}</span>
              </div>
              {service.description ? (
                <div className="mt-2 text-[12px] leading-5 text-[#667085]">{service.description}</div>
              ) : null}
            </div>
            <Space size={6}>
              <Button size="small" icon={<Copy size={14} />} onClick={() => void copy(service.runtimePath)}>复制</Button>
              <Button size="small" icon={<FileText size={14} />} onClick={() => setDocsOpen(true)}>
                OpenAPI / 调试
              </Button>
            </Space>
          </div>
        </div>
      </div>

      <div>
        <SectionTitle>运行概览</SectionTitle>
        <div className="grid grid-cols-4 border-y border-[#edf0f2] py-3">
          <Metric label="调用次数" value={runtime?.totalCalls || 0} />
          <Metric label="成功率" value={percent(runtime?.successRate)} />
          <Metric label="平均耗时" value={`${runtime?.averageDurationMs || 0} ms`} />
          <Metric label="最近调用" value={latestActivity(runtime)} />
        </div>
      </div>

      <div>
        <SectionTitle>来源</SectionTitle>
        <div className="grid grid-cols-3 border border-[#e5e7eb] text-[12px]">
          <div className="border-r border-[#edf0f2] px-4 py-3">
            <div className="text-[#98a2b3]">来源</div>
            <div className="mt-1 font-medium text-[#344054]">{sourceTypeLabel}</div>
          </div>
          <div className="border-r border-[#edf0f2] px-4 py-3">
            <div className="text-[#98a2b3]">版本</div>
            <div className="mt-1 font-medium text-[#344054]">{sourceRevisionLabel}</div>
          </div>
          <div className="px-4 py-3">
            <div className="text-[#98a2b3]">数据源</div>
            <div className="mt-1 font-medium text-[#344054]">{dataSourceName}</div>
          </div>
        </div>

        {sourceManaged ? (
          <div className="mt-3 flex items-center justify-between border border-[#e5e7eb] bg-[#fafafa] px-4 py-3 text-[12px] text-[#667085]">
            <div>
              Node #{service.sourceRef} · {service.maxRows} 行 · {service.timeoutSeconds}s · {service.parameterNames?.length || 0} 个参数
            </div>
            <Button
              size="small"
              icon={<RefreshCw size={13} />}
              loading={republishing}
              onClick={() => void syncLatestRevision()}
            >
              同步最新 Revision
            </Button>
          </div>
        ) : legacySqlRelease ? (
          <div className="mt-3 border border-[#fedf89] bg-[#fffaeb] px-4 py-3 text-[12px] leading-5 text-[#93370d]">
            历史 SQL Release 来源已冻结，现有 Runtime Snapshot 可以继续运行，但不再支持重新发布。
          </div>
        ) : null}
      </div>
    </div>
  );

  const access = (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-[13px] font-medium text-[#344054]">API Key</div>
          <div className="mt-1 text-[12px] text-[#667085]">管理访问模式、API Key 和每分钟限流。</div>
        </div>
        <Button icon={<KeyRound size={14} />} onClick={() => setAccessOpen(true)}>管理 API Key</Button>
      </div>
      <div className="grid grid-cols-3 border-y border-[#edf0f2] py-3">
        <Metric label="访问模式" value={service.authMode === 'API_KEY' ? 'API Key' : 'Public'} />
        <Metric label="API Keys" value={keys.length} />
        <Metric label="启用 Key" value={activeKeys} />
      </div>
    </div>
  );

  const runtimeTab = (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-[13px] font-medium text-[#344054]">Runtime</div>
          <div className="mt-1 text-[12px] text-[#667085]">轻量管理缓存、熔断和运行指标。</div>
        </div>
        <Space size={6}>
          <Button icon={<RefreshCw size={14} />} onClick={() => void loadDetail()}>刷新</Button>
          <Button icon={<Gauge size={14} />} onClick={() => setRuntimeOpen(true)}>Runtime 配置</Button>
        </Space>
      </div>

      <div className="grid grid-cols-4 border-y border-[#edf0f2] py-3">
        <Metric label="调用总数" value={runtime?.totalCalls || 0} />
        <Metric label="成功率" value={percent(runtime?.successRate)} />
        <Metric label="平均耗时" value={`${runtime?.averageDurationMs || 0} ms`} />
        <Metric label="P95" value={`${runtime?.p95DurationMs || 0} ms`} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="border border-[#e5e7eb] px-4 py-3">
          <div className="flex items-center gap-2 text-[12px] font-medium text-[#344054]">
            <Activity size={14} /> 结果缓存
          </div>
          <div className="mt-3 text-[12px] leading-6 text-[#667085]">
            <div>状态：{runtime?.cacheEnabled ? '启用' : '关闭'}</div>
            <div>命中率：{percent(runtime?.cacheHitRate)}</div>
            <div>当前条目：{runtime?.cacheEntries || 0}</div>
          </div>
        </div>
        <div className="border border-[#e5e7eb] px-4 py-3">
          <div className="flex items-center gap-2 text-[12px] font-medium text-[#344054]">
            <ServerCog size={14} /> 熔断器
          </div>
          <div className="mt-3 text-[12px] leading-6 text-[#667085]">
            <div>状态：{runtime?.circuitState || 'DISABLED'}</div>
            <div>拒绝次数：{runtime?.circuitRejected || 0}</div>
            <div>最近失败：{formatTime(runtime?.lastFailureAt)}</div>
          </div>
        </div>
      </div>
    </div>
  );

  const logTab = (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <div>
          <div className="text-[13px] font-medium text-[#344054]">调用记录</div>
          <div className="mt-1 text-[12px] text-[#667085]">展示当前 API 最近的调用结果、耗时和错误。</div>
        </div>
        <Button icon={<RefreshCw size={14} />} onClick={() => void loadDetail()}>刷新</Button>
      </div>

      {serviceLogs.length ? (
        <Table<DataServiceCallLog>
          rowKey="id"
          size="small"
          columns={logColumns}
          dataSource={serviceLogs}
          pagination={false}
          scroll={{ x: 720, y: 430 }}
        />
      ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无调用记录" />}
    </div>
  );

  return (
    <>
      <Drawer
        open={open}
        onClose={onClose}
        width={860}
        destroyOnHidden
        title={(
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="truncate text-[15px] font-semibold text-[#161823]">{service.name}</span>
              <Tag bordered={false}>GET</Tag>
              <Tag bordered={false}>{service.enabled ? '运行中' : '已停用'}</Tag>
              {sourceManaged ? <Tag bordered={false}>DS R{service.sourceRevisionNo || '-'}</Tag> : null}
            </div>
            <div className="mt-1 truncate font-mono text-[11px] font-normal text-[#98a2b3]">
              {service.runtimePath}
            </div>
          </div>
        )}
      >
        <Spin spinning={loading}>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            items={[
              { key: 'overview', label: '概览', children: overview },
              { key: 'access', label: 'API Key', children: access },
              { key: 'runtime', label: 'Runtime', children: runtimeTab },
              { key: 'logs', label: '调用记录', children: logTab },
            ]}
          />
        </Spin>
      </Drawer>

      <DataServiceDocsModal
        open={docsOpen}
        service={service}
        readOnly={sourceManaged}
        onCancel={() => setDocsOpen(false)}
      />

      <DataServiceAccessModal
        open={accessOpen}
        service={service}
        onCancel={() => setAccessOpen(false)}
        onChanged={async () => {
          await onChanged();
          await loadDetail();
        }}
      />

      <DataServiceRuntimeModal
        open={runtimeOpen}
        service={service}
        onCancel={() => {
          setRuntimeOpen(false);
          void loadDetail();
        }}
      />
    </>
  );
}
