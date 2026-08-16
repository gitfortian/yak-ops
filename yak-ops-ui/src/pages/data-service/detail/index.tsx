import { history, useParams } from '@umijs/max';
import { Button, Empty, Space, Spin, Table, Tag, Tooltip, message, type TableColumnsType } from 'antd';
import { ArrowLeft, Copy, FileText, Gauge, KeyRound, RefreshCw, ServerCog } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import DataServiceAccessModal from '../DataServiceAccessModal';
import DataServiceDocsModal from '../DataServiceDocsModal';
import DataServiceRuntimeModal from '../DataServiceRuntimeModal';
import {
  DATA_SERVICE_NODE_SOURCE,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
  fetchDataServiceKeys,
  fetchDataServiceLogs,
  fetchDataServiceRuntime,
  fetchDataServices,
  fetchDataSourceOptions,
  republishDataService,
  type DataServiceApi,
  type DataServiceApiKey,
  type DataServiceCallLog,
  type DataServiceRuntimeStatus,
  type DataSourceOption,
} from '../service';

const SECTION_ITEMS = [
  { key: 'basic', label: '基本信息' },
  { key: 'access', label: 'API Key' },
  { key: 'runtime', label: 'Runtime' },
  { key: 'logs', label: '调用记录' },
] as const;

type SectionKey = (typeof SECTION_ITEMS)[number]['key'];

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
  <div className="min-w-0 px-4 py-3 first:pl-0">
    <div className="text-[11px] text-[#98a2b3]">{label}</div>
    <div className="mt-1 truncate text-[18px] font-semibold text-[#1d2939]">{value}</div>
  </div>
);

const InfoItem = ({ label, children }: { label: string; children: ReactNode }) => (
  <div className="min-w-0 border-b border-[#f0f1f2] py-3 last:border-b-0">
    <div className="grid grid-cols-[112px_minmax(0,1fr)] items-start gap-4 text-[12px]">
      <span className="text-[#98a2b3]">{label}</span>
      <div className="min-w-0 text-[#344054]">{children}</div>
    </div>
  </div>
);

const SectionCard = ({
  id,
  title,
  action,
  children,
}: {
  id: SectionKey;
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) => (
  <section id={`api-detail-${id}`} className="scroll-mt-6 rounded-xl border border-[#eaecf0] bg-white">
    <div className="flex min-h-[52px] items-center justify-between gap-3 border-b border-[#f0f1f2] px-5">
      <div className="text-[13px] font-semibold text-[#30323b]">{title}</div>
      {action}
    </div>
    <div className="px-5 py-4">{children}</div>
  </section>
);

function SectionNavigator({ activeKey, onSelect }: { activeKey: SectionKey; onSelect: (key: SectionKey) => void }) {
  return (
    <nav className="rounded-xl bg-white px-3 py-4" aria-label="API 详情快速定位">
      <div className="mb-3 px-2 text-[12px] font-semibold text-[#344054]">快速定位</div>
      <div className="relative">
        <span aria-hidden className="absolute bottom-4 left-[13px] top-4 w-px bg-[#e4e7ec]" />
        <div className="space-y-1">
          {SECTION_ITEMS.map((item) => {
            const active = item.key === activeKey;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => onSelect(item.key)}
                className={[
                  'group relative flex w-full items-center gap-3 rounded-lg border-0 px-2 py-2 text-left transition-colors',
                  active ? 'bg-[rgba(254,44,85,.07)]' : 'bg-transparent hover:bg-[#f7f8fa]',
                ].join(' ')}
              >
                <span
                  className={[
                    'relative z-10 h-[11px] w-[11px] shrink-0 rounded-full border transition-all',
                    active
                      ? 'border-[var(--yak-brand-color)] bg-[var(--yak-brand-color)] shadow-[0_0_0_3px_rgba(254,44,85,.10)]'
                      : 'border-[#d0d5dd] bg-[#98a2b3]',
                  ].join(' ')}
                />
                <span className={active
                  ? 'text-[12px] font-semibold text-[var(--yak-brand-color)]'
                  : 'text-[12px] text-[#667085] group-hover:text-[#344054]'}>
                  {item.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
}

export default function DataServiceDetailPage() {
  const params = useParams<{ id?: string }>();
  const apiId = Number(params.id || 0);
  const pageRootRef = useRef<HTMLDivElement>(null);

  const [service, setService] = useState<DataServiceApi>();
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [runtime, setRuntime] = useState<DataServiceRuntimeStatus>();
  const [keys, setKeys] = useState<DataServiceApiKey[]>([]);
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [activeSection, setActiveSection] = useState<SectionKey>('basic');
  const [docsOpen, setDocsOpen] = useState(false);
  const [accessOpen, setAccessOpen] = useState(false);
  const [runtimeOpen, setRuntimeOpen] = useState(false);

  const load = useCallback(async (quiet = false) => {
    if (!Number.isFinite(apiId) || apiId <= 0) {
      setLoading(false);
      return;
    }
    quiet ? setRefreshing(true) : setLoading(true);
    try {
      const [servicesResponse, dataSourceResponse, runtimeResponse, keyResponse, logResponse] = await Promise.all([
        fetchDataServices(),
        fetchDataSourceOptions(),
        fetchDataServiceRuntime(apiId),
        fetchDataServiceKeys(apiId),
        fetchDataServiceLogs(),
      ]);
      setService((servicesResponse.data || []).find((item) => Number(item.id) === apiId));
      setDataSources(dataSourceResponse.data || []);
      setRuntime(runtimeResponse.data);
      setKeys(keyResponse.data || []);
      setLogs(logResponse.data || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API 详情失败');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [apiId]);

  useEffect(() => { void load(); }, [load]);

  const sourceManaged = service?.sourceType === DATA_SERVICE_NODE_SOURCE;
  const legacySqlRelease = service?.sourceType === LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE;

  const dataSourceName = useMemo(() => {
    if (!service?.dataSourceId) return '-';
    return dataSources.find((item) => String(item.value) === String(service.dataSourceId))?.label
      || `#${service.dataSourceId}`;
  }, [dataSources, service?.dataSourceId]);

  const serviceLogs = useMemo(
    () => logs.filter((item) => Number(item.apiId) === apiId).slice(0, 50),
    [apiId, logs],
  );
  const activeKeys = useMemo(() => keys.filter((item) => item.enabled).length, [keys]);

  const updateActiveSection = useCallback(() => {
    const container = pageRootRef.current;
    if (!container) return;
    const threshold = container.getBoundingClientRect().top + 150;
    let next: SectionKey = SECTION_ITEMS[0].key;
    SECTION_ITEMS.forEach((item) => {
      const element = document.getElementById(`api-detail-${item.key}`);
      if (element && element.getBoundingClientRect().top <= threshold) next = item.key;
    });
    setActiveSection((current) => current === next ? current : next);
  }, []);

  useEffect(() => {
    const container = pageRootRef.current;
    if (!container || loading) return undefined;
    const onScroll = () => window.requestAnimationFrame(updateActiveSection);
    container.addEventListener('scroll', onScroll, { passive: true });
    updateActiveSection();
    return () => container.removeEventListener('scroll', onScroll);
  }, [loading, updateActiveSection]);

  const locate = (key: SectionKey) => {
    const container = pageRootRef.current;
    const element = document.getElementById(`api-detail-${key}`);
    if (!container || !element) return;
    const top = container.scrollTop
      + element.getBoundingClientRect().top
      - container.getBoundingClientRect().top
      - 24;
    setActiveSection(key);
    container.scrollTo({ top: Math.max(top, 0), behavior: 'smooth' });
  };

  const copyEndpoint = async () => {
    if (!service?.runtimePath) return;
    try {
      await navigator.clipboard.writeText(service.runtimePath);
      message.success('Endpoint 已复制');
    } catch {
      message.warning('复制失败，请手动复制');
    }
  };

  const updateOnline = async () => {
    if (!service || !sourceManaged) return;
    setUpdating(true);
    try {
      const response = await republishDataService(service.id);
      const revisionNo = response.data?.sourceRevisionNo;
      message.success(revisionNo ? `已更新上线到 DS R${revisionNo}` : '已更新上线');
      await load(true);
    } catch (error: any) {
      message.error(error?.message || '更新上线失败');
    } finally {
      setUpdating(false);
    }
  };

  const logColumns: TableColumnsType<DataServiceCallLog> = [
    {
      title: '调用方',
      key: 'caller',
      width: 150,
      render: (_, record) => (
        <div>
          <div className="text-[12px] text-[#475467]">{callerLabel(record)}</div>
          {record.apiKeyPrefix ? <div className="mt-0.5 font-mono text-[10px] text-[#98a2b3]">{record.apiKeyPrefix}••••</div> : null}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'success',
      width: 76,
      render: (value: boolean) => <span className={value ? 'text-[#475467]' : 'text-[var(--yak-brand-color)]'}>{value ? '成功' : '失败'}</span>,
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

  if (loading) {
    return <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]"><Spin size="large" /></div>;
  }

  if (!service) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到 API">
          <Button onClick={() => history.push('/data-service')}>返回 API 集市</Button>
        </Empty>
      </div>
    );
  }

  const sourceTypeLabel = sourceManaged ? 'Data Service Node' : legacySqlRelease ? 'Legacy SQL Release' : 'Legacy';
  const sourceRevisionLabel = sourceManaged
    ? `DS R${service.sourceRevisionNo || '-'}`
    : legacySqlRelease ? `SQL v${service.sourceRevisionNo || '-'}` : '-';

  return (
    <>
      <div className="h-[calc(100vh-64px)] overflow-hidden bg-[#f7f8fa] text-[#161823]">
        <div ref={pageRootRef} className="h-full overflow-y-auto overscroll-contain scroll-smooth">
          <div className="mx-auto grid w-full max-w-[1280px] grid-cols-1 gap-6 px-6 pb-8 pt-6 max-xl:max-w-[1040px] xl:grid-cols-[minmax(0,1fr)_160px]">
            <div className="min-w-0 space-y-4">
              <header className="rounded-xl border border-[#eaecf0] bg-white px-5 py-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <button
                      type="button"
                      onClick={() => history.push('/data-service')}
                      className="mb-3 inline-flex items-center gap-1 border-0 bg-transparent p-0 text-[12px] text-[#667085] hover:text-[#344054]"
                    >
                      <ArrowLeft size={14} /> API 集市
                    </button>
                    <div className="flex min-w-0 items-center gap-2">
                      <h1 className="m-0 truncate text-[18px] font-semibold text-[#161823]">{service.name}</h1>
                      <Tag bordered={false}>GET</Tag>
                      <Tag bordered={false}>{service.enabled ? '运行中' : '已停用'}</Tag>
                      {sourceManaged ? <Tag bordered={false}>DS R{service.sourceRevisionNo || '-'}</Tag> : null}
                    </div>
                    <div className="mt-2 truncate font-mono text-[11px] text-[#98a2b3]">{service.runtimePath}</div>
                  </div>

                  <Space size={8} wrap>
                    <Button icon={<Copy size={14} />} onClick={() => void copyEndpoint()}>复制 Endpoint</Button>
                    <Button icon={<FileText size={14} />} onClick={() => setDocsOpen(true)}>OpenAPI / 调试</Button>
                    <Button icon={<RefreshCw size={14} />} loading={refreshing} onClick={() => void load(true)}>刷新</Button>
                  </Space>
                </div>
              </header>

              <SectionCard
                id="basic"
                title="基本信息"
                action={sourceManaged ? (
                  <Button size="small" loading={updating} onClick={() => void updateOnline()}>更新上线</Button>
                ) : undefined}
              >
                <div className="grid grid-cols-4 divide-x divide-[#edf0f2] border-b border-[#edf0f2] pb-3">
                  <Metric label="调用次数" value={runtime?.totalCalls || 0} />
                  <Metric label="成功率" value={percent(runtime?.successRate)} />
                  <Metric label="平均耗时" value={`${runtime?.averageDurationMs || 0} ms`} />
                  <Metric label="最近调用" value={latestActivity(runtime)} />
                </div>

                <div className="mt-3 grid grid-cols-1 gap-x-8 lg:grid-cols-2">
                  <div>
                    <InfoItem label="Endpoint"><span className="break-all font-mono">{service.runtimePath}</span></InfoItem>
                    <InfoItem label="数据源">{dataSourceName}</InfoItem>
                    <InfoItem label="来源">{sourceTypeLabel}</InfoItem>
                    <InfoItem label="版本">{sourceRevisionLabel}</InfoItem>
                  </div>
                  <div>
                    <InfoItem label="最大返回行数">{service.maxRows || '-'}</InfoItem>
                    <InfoItem label="超时时间">{service.timeoutSeconds ? `${service.timeoutSeconds}s` : '-'}</InfoItem>
                    <InfoItem label="请求参数">{service.parameterNames?.length || 0} 个</InfoItem>
                    <InfoItem label="描述">{service.description || '-'}</InfoItem>
                  </div>
                </div>

                {legacySqlRelease ? (
                  <div className="mt-3 rounded-lg bg-[#fffaeb] px-3 py-2 text-[11px] text-[#93370d]">历史来源已冻结</div>
                ) : null}
              </SectionCard>

              <SectionCard
                id="access"
                title="API Key"
                action={<Button size="small" icon={<KeyRound size={13} />} onClick={() => setAccessOpen(true)}>管理 API Key</Button>}
              >
                <div className="grid grid-cols-3 divide-x divide-[#edf0f2]">
                  <Metric label="访问模式" value={service.authMode === 'API_KEY' ? 'API Key' : 'Public'} />
                  <Metric label="API Keys" value={keys.length} />
                  <Metric label="启用 Key" value={activeKeys} />
                </div>
              </SectionCard>

              <SectionCard
                id="runtime"
                title="Runtime"
                action={<Button size="small" icon={<Gauge size={13} />} onClick={() => setRuntimeOpen(true)}>Runtime 配置</Button>}
              >
                <div className="grid grid-cols-4 divide-x divide-[#edf0f2] border-b border-[#edf0f2] pb-3">
                  <Metric label="调用总数" value={runtime?.totalCalls || 0} />
                  <Metric label="成功率" value={percent(runtime?.successRate)} />
                  <Metric label="平均耗时" value={`${runtime?.averageDurationMs || 0} ms`} />
                  <Metric label="P95" value={`${runtime?.p95DurationMs || 0} ms`} />
                </div>
                <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
                  <div className="rounded-lg border border-[#eaecf0] bg-[#fafafa] px-4 py-3">
                    <div className="flex items-center gap-2 text-[12px] font-medium text-[#344054]"><RefreshCw size={13} /> 结果缓存</div>
                    <div className="mt-3 grid grid-cols-3 gap-3 text-[11px] text-[#667085]">
                      <div><div className="text-[#98a2b3]">状态</div><div className="mt-1">{runtime?.cacheEnabled ? '启用' : '关闭'}</div></div>
                      <div><div className="text-[#98a2b3]">命中率</div><div className="mt-1">{percent(runtime?.cacheHitRate)}</div></div>
                      <div><div className="text-[#98a2b3]">条目</div><div className="mt-1">{runtime?.cacheEntries || 0}</div></div>
                    </div>
                  </div>
                  <div className="rounded-lg border border-[#eaecf0] bg-[#fafafa] px-4 py-3">
                    <div className="flex items-center gap-2 text-[12px] font-medium text-[#344054]"><ServerCog size={13} /> 熔断器</div>
                    <div className="mt-3 grid grid-cols-3 gap-3 text-[11px] text-[#667085]">
                      <div><div className="text-[#98a2b3]">状态</div><div className="mt-1">{runtime?.circuitState || 'DISABLED'}</div></div>
                      <div><div className="text-[#98a2b3]">拒绝</div><div className="mt-1">{runtime?.circuitRejected || 0}</div></div>
                      <div><div className="text-[#98a2b3]">最近失败</div><div className="mt-1 truncate">{formatTime(runtime?.lastFailureAt)}</div></div>
                    </div>
                  </div>
                </div>
              </SectionCard>

              <SectionCard
                id="logs"
                title="调用记录"
                action={<Button size="small" icon={<RefreshCw size={13} />} loading={refreshing} onClick={() => void load(true)}>刷新</Button>}
              >
                {serviceLogs.length ? (
                  <Table<DataServiceCallLog>
                    rowKey="id"
                    size="small"
                    columns={logColumns}
                    dataSource={serviceLogs}
                    pagination={false}
                    scroll={{ x: 760 }}
                  />
                ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无调用记录" />}
              </SectionCard>
            </div>

            <aside className="hidden xl:block">
              <div className="sticky top-6">
                <SectionNavigator activeKey={activeSection} onSelect={locate} />
              </div>
            </aside>
          </div>
        </div>
      </div>

      <DataServiceDocsModal open={docsOpen} service={service} readOnly={sourceManaged} onCancel={() => setDocsOpen(false)} />
      <DataServiceAccessModal
        open={accessOpen}
        service={service}
        onCancel={() => setAccessOpen(false)}
        onChanged={async () => { await load(true); }}
      />
      <DataServiceRuntimeModal
        open={runtimeOpen}
        service={service}
        onCancel={() => { setRuntimeOpen(false); void load(true); }}
      />
    </>
  );
}
