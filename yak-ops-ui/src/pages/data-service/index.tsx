import {
  Button,
  Dropdown,
  Empty,
  Input,
  Modal,
  Space,
  Switch,
  Table,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import {
  ArrowLeft,
  Copy,
  MoreHorizontal,
  RefreshCw,
  Search,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import DataServiceDetailDrawer from './DataServiceDetailDrawer';
import {
  DATA_SERVICE_NODE_SOURCE,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
  deleteDataService,
  fetchDataServiceLogs,
  fetchDataServices,
  fetchDataSourceOptions,
  setDataServiceEnabled,
  type DataServiceApi,
  type DataServiceCallLog,
  type DataSourceOption,
} from './service';

const timeValue = (value?: string) => {
  if (!value) return 0;
  const result = new Date(value).getTime();
  return Number.isNaN(result) ? 0 : result;
};

interface ApiListItemProps {
  service: DataServiceApi;
  dataSourceName: string;
  calls?: number;
  rank?: number;
  onOpen: () => void;
}

const ApiMethod = () => (
  <span className="inline-flex h-5 items-center rounded-[4px] bg-[#f1f3f5] px-1.5 font-mono text-[10px] font-medium text-[#667085]">
    GET
  </span>
);

const ApiMarketplaceIllustration = () => (
  <svg
    viewBox="0 0 244 154"
    className="h-[128px] w-[205px]"
    fill="none"
    aria-hidden="true"
  >
    <path d="M32 77H77" stroke="#d9dee5" strokeWidth="1.5" strokeLinecap="round" />
    <path d="M102 50H137C157 50 157 75 177 75H211" stroke="#d9dee5" strokeWidth="1.5" strokeLinecap="round" />
    <path d="M102 104H137C157 104 157 82 177 82H211" stroke="#f2a6b7" strokeWidth="1.5" strokeLinecap="round" />
    <path d="M88 65V89" stroke="#e4e7ec" strokeWidth="1.25" strokeDasharray="3 5" />

    <rect x="20" y="59" width="36" height="36" rx="9" fill="#fff" stroke="#efc7d0" />
    <path d="M31 70L26.5 77L31 84" stroke="#fe2c55" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M45 70L49.5 77L45 84" stroke="#fe2c55" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M41 67L35 87" stroke="#8c94a1" strokeWidth="1.5" strokeLinecap="round" />

    <rect x="76" y="35" width="40" height="32" rx="8" fill="#fff" stroke="#dfe3e8" />
    <circle cx="88" cy="51" r="3.5" fill="#fff0f3" stroke="#fe2c55" strokeWidth="1.2" />
    <path d="M96 47H106M96 52H108M86 58H108" stroke="#a4abb5" strokeWidth="1.3" strokeLinecap="round" />

    <rect x="76" y="88" width="40" height="32" rx="8" fill="#fff" stroke="#dfe3e8" />
    <path d="M87 99H105M87 105H102M87 111H108" stroke="#a4abb5" strokeWidth="1.3" strokeLinecap="round" />
    <circle cx="107" cy="99" r="2.2" fill="#fe2c55" fillOpacity=".7" />

    <rect x="192" y="57" width="34" height="42" rx="9" fill="#fff" stroke="#efc7d0" />
    <rect x="200" y="67" width="18" height="4" rx="2" fill="#d9dee5" />
    <rect x="200" y="76" width="18" height="4" rx="2" fill="#f2a6b7" />
    <rect x="200" y="85" width="12" height="4" rx="2" fill="#d9dee5" />

    <circle cx="32" cy="77" r="2.5" fill="#fe2c55" />
    <circle cx="211" cy="75" r="2.5" fill="#667085" />
    <circle cx="211" cy="82" r="2.5" fill="#fe2c55" fillOpacity=".7" />
  </svg>
);

const ApiListItem = ({
  service,
  dataSourceName,
  calls,
  rank,
  onOpen,
}: ApiListItemProps) => (
  <button
    type="button"
    onClick={onOpen}
    className="group flex min-h-[82px] w-full items-center gap-3 rounded-lg border-0 bg-transparent px-3 py-3 text-left transition-colors hover:bg-white"
  >
    {rank ? (
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white font-mono text-[10px] font-medium text-[#98a2b3] shadow-[0_0_0_1px_rgba(16,24,40,.04)]">
        {String(rank).padStart(2, '0')}
      </span>
    ) : null}

    <div className="min-w-0 flex-1">
      <div className="flex items-center gap-2">
        <span className="truncate text-[13px] font-medium text-[#161823]">
          {service.name}
        </span>
        <ApiMethod />
        {!service.enabled ? (
          <span className="text-[10px] text-[#98a2b3]">已停用</span>
        ) : null}
      </div>
      <div className="mt-1 truncate text-[11px] leading-5 text-[#8a9099]">
        {service.description || '暂无描述'}
      </div>
      <div className="mt-0.5 truncate font-mono text-[10px] text-[#a3a8b0]" title={service.runtimePath}>
        {service.runtimePath}
      </div>
    </div>

    <div className="w-[118px] shrink-0 text-right">
      <div className="truncate text-[11px] font-medium text-[#667085]">
        {calls !== undefined ? `${calls} 次调用` : dataSourceName}
      </div>
      <div className="mt-1 text-[10px] text-[#b0b5bd]">
        {calls !== undefined ? dataSourceName : service.enabled ? '运行中' : '已停用'}
      </div>
    </div>
  </button>
);

export default function DataServicePage() {
  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [detailTarget, setDetailTarget] = useState<DataServiceApi>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [serviceResponse, dataSourceResponse, logResponse] = await Promise.all([
        fetchDataServices(),
        fetchDataSourceOptions(),
        fetchDataServiceLogs(),
      ]);
      const nextServices = serviceResponse.data || [];
      setServices(nextServices);
      setDataSources(dataSourceResponse.data || []);
      setLogs(logResponse.data || []);
      setDetailTarget((current) => current
        ? nextServices.find((item) => item.id === current.id)
        : undefined);
    } catch (error: any) {
      message.error(error?.message || '加载 API 集市失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const dataSourceName = useCallback((dataSourceId?: number) => {
    if (!dataSourceId) return '-';
    return dataSources.find((item) => String(item.value) === String(dataSourceId))?.label
      || `#${dataSourceId}`;
  }, [dataSources]);

  const callsByApiId = useMemo(() => {
    const result = new Map<number, number>();
    logs.forEach((item) => result.set(item.apiId, (result.get(item.apiId) || 0) + 1));
    return result;
  }, [logs]);

  const runningServices = useMemo(
    () => services.filter((item) => item.enabled),
    [services],
  );

  const recommendedServices = useMemo(() => {
    const source = runningServices.length ? runningServices : services;
    return [...source]
      .sort((left, right) => timeValue(right.updateTime || right.createTime)
        - timeValue(left.updateTime || left.createTime))
      .slice(0, 6);
  }, [runningServices, services]);

  const hotServices = useMemo(() => services
    .filter((item) => (callsByApiId.get(item.id) || 0) > 0)
    .sort((left, right) => (callsByApiId.get(right.id) || 0) - (callsByApiId.get(left.id) || 0))
    .slice(0, 6), [callsByApiId, services]);

  const searchResults = useMemo(() => {
    const value = submittedKeyword.trim().toLowerCase();
    if (!value) return [];
    return services.filter((item) =>
      [
        item.name,
        item.path,
        item.runtimePath,
        item.description,
        item.sourceRef,
        dataSourceName(item.dataSourceId),
      ]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(value)));
  }, [dataSourceName, services, submittedKeyword]);

  const remove = async (record: DataServiceApi) => {
    try {
      await deleteDataService(record.id);
      if (detailTarget?.id === record.id) setDetailTarget(undefined);
      message.success('已删除');
      await load();
    } catch (error: any) {
      message.error(error?.message || '删除失败');
    }
  };

  const confirmRemove = (record: DataServiceApi) => {
    Modal.confirm({
      title: '删除 API',
      content: `确认删除「${record.name}」？API Key、运行状态和相关文档也会一起移除。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => remove(record),
    });
  };

  const toggleEnabled = async (record: DataServiceApi, enabled: boolean) => {
    try {
      await setDataServiceEnabled(record.id, enabled);
      message.success(enabled ? 'API 已启用' : 'API 已停用');
      await load();
    } catch (error: any) {
      message.error(error?.message || '状态更新失败');
    }
  };

  const copyPath = async (path: string) => {
    try {
      await navigator.clipboard.writeText(path);
      message.success('Endpoint 已复制');
    } catch {
      message.warning('复制失败，请手动复制');
    }
  };

  const submitSearch = () => {
    const value = keyword.trim();
    if (!value) {
      setSubmittedKeyword('');
      return;
    }
    setSubmittedKeyword(value);
  };

  const resetSearch = () => {
    setKeyword('');
    setSubmittedKeyword('');
  };

  const columns: TableColumnsType<DataServiceApi> = [
    {
      title: 'API',
      dataIndex: 'name',
      minWidth: 230,
      render: (_, record) => (
        <div className="py-1">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setDetailTarget(record)}
              className="max-w-[220px] truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#344054] hover:text-[#161823]"
            >
              {record.name}
            </button>
            <ApiMethod />
          </div>
          <div className="mt-0.5 line-clamp-1 text-[11px] text-[#98a2b3]">
            {record.description || '暂无描述'}
          </div>
        </div>
      ),
    },
    {
      title: 'Endpoint',
      dataIndex: 'runtimePath',
      minWidth: 280,
      render: (value: string) => (
        <div className="flex items-center gap-1">
          <span className="truncate font-mono text-[11px] text-[#667085]">{value}</span>
          <Tooltip title="复制 Endpoint">
            <Button
              type="text"
              size="small"
              icon={<Copy size={13} />}
              onClick={() => void copyPath(value)}
            />
          </Tooltip>
        </div>
      ),
    },
    {
      title: '来源',
      key: 'source',
      width: 210,
      render: (_, record) => {
        if (record.sourceType === DATA_SERVICE_NODE_SOURCE) {
          return (
            <div>
              <div className="text-[12px] font-medium text-[#475467]">Data Service · DS R{record.sourceRevisionNo || '-'}</div>
              <div className="mt-0.5 text-[11px] text-[#98a2b3]">{dataSourceName(record.dataSourceId)}</div>
            </div>
          );
        }
        if (record.sourceType === LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE) {
          return (
            <div>
              <div className="text-[12px] font-medium text-[#667085]">Legacy · SQL v{record.sourceRevisionNo || '-'}</div>
              <div className="mt-0.5 text-[11px] text-[#98a2b3]">冻结来源 · {dataSourceName(record.dataSourceId)}</div>
            </div>
          );
        }
        return <span className="text-[12px] text-[#667085]">{dataSourceName(record.dataSourceId)}</span>;
      },
    },
    {
      title: '近期调用',
      key: 'calls',
      width: 100,
      render: (_, record) => <span className="text-[12px] text-[#475467]">{callsByApiId.get(record.id) || 0}</span>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 135,
      render: (enabled: boolean, record) => (
        <div className="flex items-center gap-2">
          <Switch
            size="small"
            checked={enabled}
            onChange={(next) => void toggleEnabled(record, next)}
          />
          <span className={enabled ? 'text-[12px] text-[#344054]' : 'text-[12px] text-[#98a2b3]'}>
            {enabled ? '运行中' : '已停用'}
          </span>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Space size={8}>
          <Button
            type="link"
            size="small"
            className="!px-0 !text-[12px] !text-[#475467]"
            onClick={() => setDetailTarget(record)}
          >
            查看
          </Button>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'delete', danger: true, icon: <Trash2 size={14} />, label: '删除 API' },
              ],
              onClick: ({ key }) => {
                if (key === 'delete') confirmRemove(record);
              },
            }}
          >
            <Button type="text" size="small" icon={<MoreHorizontal size={16} />} />
          </Dropdown>
        </Space>
      ),
    },
  ];

  const handleKeywordChange = (value: string) => {
    setKeyword(value);
    if (!value) setSubmittedKeyword('');
  };

  const searchControls = (compact = false) => compact ? (
    <div className="flex min-w-0 flex-1 items-center gap-2 rounded-lg bg-[#f6f7f8] p-1.5">
      <Input
        allowClear
        variant="borderless"
        value={keyword}
        prefix={<Search size={15} className="text-[#98a2b3]" />}
        placeholder="搜索 API 名称、Endpoint、描述或数据源"
        className="!h-8 !bg-transparent !px-2"
        onChange={(event) => handleKeywordChange(event.target.value)}
        onPressEnter={submitSearch}
      />
      <Button type="primary" loading={loading} className="!h-8 !px-4" onClick={submitSearch}>
        搜索
      </Button>
    </div>
  ) : (
    <div className="w-full max-w-[720px] rounded-xl bg-white p-2 shadow-[0_10px_30px_rgba(16,24,40,.045)] ring-1 ring-[#e6e9ee]">
      <div className="flex items-center gap-2">
        <Search size={17} className="ml-2 shrink-0 text-[#98a2b3]" />
        <Input
          allowClear
          variant="borderless"
          value={keyword}
          placeholder="搜索 API 名称、Endpoint、描述或数据源"
          className="!h-11 !bg-white !px-1 !text-[13px]"
          onChange={(event) => handleKeywordChange(event.target.value)}
          onPressEnter={submitSearch}
        />
        <Button type="primary" loading={loading} className="!h-9 !px-5" onClick={submitSearch}>
          搜索
        </Button>
      </div>
      <div className="flex items-center gap-4 px-3 pb-1 pt-1 text-[10px] text-[#a0a6af]">
        <span>API 名称</span>
        <span>Endpoint</span>
        <span>数据源</span>
      </div>
    </div>
  );

  const searching = Boolean(submittedKeyword.trim());

  return (
    <div className="min-h-[calc(100vh-64px)] bg-white">
      {searching ? (
        <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
          <div className="flex items-center justify-between gap-4">
            <div className="flex min-w-0 items-center gap-2">
              <Button
                type="text"
                icon={<ArrowLeft size={15} />}
                onClick={resetSearch}
                className="!-ml-2 !px-2"
              />
              <div className="min-w-0">
                <h1 className="m-0 text-[17px] font-semibold text-[#161823]">API 集市</h1>
                <div className="mt-1 truncate text-[12px] text-[#98a2b3]">
                  搜索 “{submittedKeyword}”
                </div>
              </div>
            </div>
            <Button
              type="text"
              icon={<RefreshCw size={14} />}
              loading={loading}
              onClick={() => void load()}
              className="bg-[#f5f6f7]"
            >
              刷新
            </Button>
          </div>

          <div className="mt-4 flex items-center justify-between gap-4">
            <div className="w-full max-w-[680px]">{searchControls(true)}</div>
            <span className="shrink-0 text-[12px] text-[#98a2b3]">共 {searchResults.length} 个结果</span>
          </div>

          <div className="min-h-0 flex-1 pt-4">
            <Table<DataServiceApi>
              rowKey="id"
              size="small"
              loading={loading}
              dataSource={searchResults}
              columns={columns}
              pagination={false}
              scroll={{ x: 1080, y: 'calc(100vh - 235px)' }}
              locale={{ emptyText: '没有找到匹配的 API' }}
            />
          </div>
        </div>
      ) : (
        <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white">
          <div
            className="relative overflow-hidden px-5 py-8"
            style={{
              background: 'linear-gradient(180deg, #f8fafc 0%, #fbfcfd 62%, #ffffff 100%)',
            }}
          >
            <div className="pointer-events-none absolute left-[4.5%] top-1/2 hidden -translate-y-1/2 opacity-90 lg:block">
              <ApiMarketplaceIllustration />
            </div>
            <div className="pointer-events-none absolute -left-14 bottom-[-92px] h-[190px] w-[190px] rounded-full bg-[rgba(254,44,85,.028)]" />
            <div className="pointer-events-none absolute left-[18%] top-[-90px] h-[160px] w-[160px] rounded-full border border-[rgba(254,44,85,.045)]" />

            <div className="relative mx-auto flex max-w-[820px] flex-col items-center">
              <h1 className="m-0 mb-4 text-[20px] font-semibold tracking-[-.01em] text-[#161823]">API 集市</h1>
              {searchControls()}
              <div className="mt-3 text-[11px] text-[#a0a6af]">
                {services.length} 个 API · {runningServices.length} 个运行中
              </div>
            </div>

            <Button
              type="text"
              icon={<RefreshCw size={14} />}
              loading={loading}
              onClick={() => void load()}
              className="!absolute !right-5 !top-4 !bg-white/75 !text-[#667085]"
            >
              刷新
            </Button>
          </div>

          <div className="px-5 pb-8 pt-3">
            <div className="grid min-h-0 grid-cols-1 gap-5 xl:grid-cols-2">
              <section className="min-w-0 rounded-xl bg-[#f7f8fa] p-3">
                <div className="flex items-center justify-between px-2 pb-2 pt-1">
                  <div>
                    <div className="text-[13px] font-semibold text-[#30323b]">推荐 API</div>
                    <div className="mt-1 text-[10px] text-[#a0a6af]">最近更新的可用服务</div>
                  </div>
                  <span className="text-[10px] text-[#a0a6af]">{recommendedServices.length} 个</span>
                </div>
                {recommendedServices.length ? (
                  <div className="space-y-1">
                    {recommendedServices.map((service) => (
                      <ApiListItem
                        key={service.id}
                        service={service}
                        dataSourceName={dataSourceName(service.dataSourceId)}
                        onOpen={() => setDetailTarget(service)}
                      />
                    ))}
                  </div>
                ) : (
                  <div className="flex h-[260px] items-center justify-center">
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已上线 API" />
                  </div>
                )}
              </section>

              <section className="min-w-0 rounded-xl bg-[#f7f8fa] p-3">
                <div className="flex items-center justify-between px-2 pb-2 pt-1">
                  <div>
                    <div className="text-[13px] font-semibold text-[#30323b]">热门调用</div>
                    <div className="mt-1 text-[10px] text-[#a0a6af]">按近期调用次数排序</div>
                  </div>
                  <span className="text-[10px] text-[#a0a6af]">Top {hotServices.length}</span>
                </div>
                {hotServices.length ? (
                  <div className="space-y-1">
                    {hotServices.map((service, index) => (
                      <ApiListItem
                        key={service.id}
                        rank={index + 1}
                        service={service}
                        dataSourceName={dataSourceName(service.dataSourceId)}
                        calls={callsByApiId.get(service.id) || 0}
                        onOpen={() => setDetailTarget(service)}
                      />
                    ))}
                  </div>
                ) : (
                  <div className="flex h-[260px] items-center justify-center">
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无调用记录" />
                  </div>
                )}
              </section>
            </div>
          </div>
        </div>
      )}

      <DataServiceDetailDrawer
        open={Boolean(detailTarget)}
        service={detailTarget}
        dataSources={dataSources}
        onClose={() => setDetailTarget(undefined)}
        onChanged={load}
      />
    </div>
  );
}
