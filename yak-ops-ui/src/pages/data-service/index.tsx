import {
  Button,
  Dropdown,
  Empty,
  Input,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import {
  ArrowLeft,
  Copy,
  Eye,
  Flame,
  MoreHorizontal,
  RefreshCw,
  Search,
  Sparkles,
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

interface ApiCardProps {
  service: DataServiceApi;
  dataSourceName: string;
  calls?: number;
  hot?: boolean;
  onOpen: () => void;
}

const ApiCard = ({ service, dataSourceName, calls, hot, onOpen }: ApiCardProps) => (
  <button
    type="button"
    onClick={onOpen}
    className="group min-h-[148px] rounded-[6px] border border-[#e6e8eb] bg-white p-4 text-left transition-all hover:-translate-y-px hover:border-[#d9dde3] hover:shadow-[0_5px_18px_rgba(16,24,40,.06)]"
  >
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="truncate text-[14px] font-semibold text-[#161823]">{service.name}</span>
          <Tag bordered={false}>GET</Tag>
        </div>
        <div className="mt-1.5 line-clamp-2 min-h-[36px] text-[12px] leading-[18px] text-[#7b808a]">
          {service.description || '暂无能力说明'}
        </div>
      </div>
      {hot ? (
        <Flame size={16} strokeWidth={1.8} className="mt-0.5 shrink-0 text-[#98a2b3]" />
      ) : (
        <Sparkles size={16} strokeWidth={1.8} className="mt-0.5 shrink-0 text-[#98a2b3]" />
      )}
    </div>

    <div className="mt-4 flex items-center justify-between gap-3 border-t border-[#f0f1f3] pt-3 text-[11px] text-[#98a2b3]">
      <span className="min-w-0 flex-1 truncate font-mono" title={service.runtimePath}>
        {service.runtimePath}
      </span>
      {calls !== undefined ? (
        <span className="shrink-0">{calls} 次调用</span>
      ) : (
        <span className="max-w-[120px] shrink-0 truncate" title={dataSourceName}>{dataSourceName}</span>
      )}
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
        <div className="py-1.5">
          <div className="flex items-center gap-2">
            <span className="font-medium text-[#161823]">{record.name}</span>
            <Tag bordered={false}>GET</Tag>
          </div>
          <div className="mt-1 line-clamp-1 text-xs text-black/40">
            {record.description || '暂无说明'}
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
          <span className="truncate font-mono text-xs text-black/55">{value}</span>
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
              <div className="font-medium text-[#475467]">Data Service · DS R{record.sourceRevisionNo || '-'}</div>
              <div className="mt-1 text-[11px] text-black/35">{dataSourceName(record.dataSourceId)}</div>
            </div>
          );
        }
        if (record.sourceType === LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE) {
          return (
            <div>
              <div className="font-medium text-[#667085]">Legacy · SQL v{record.sourceRevisionNo || '-'}</div>
              <div className="mt-1 text-[11px] text-black/35">冻结来源 · {dataSourceName(record.dataSourceId)}</div>
            </div>
          );
        }
        return <span className="text-black/45">{dataSourceName(record.dataSourceId)}</span>;
      },
    },
    {
      title: '近期调用',
      key: 'calls',
      width: 100,
      render: (_, record) => <span className="text-[#475467]">{callsByApiId.get(record.id) || 0}</span>,
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
          <span className={enabled ? 'text-[#344054]' : 'text-black/35'}>
            {enabled ? '运行中' : '已停用'}
          </span>
        </div>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            icon={<Eye size={14} />}
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

  const searchBox = (compact = false) => (
    <Input.Search
      allowClear
      value={keyword}
      loading={loading}
      enterButton="搜索"
      size={compact ? 'middle' : 'large'}
      prefix={<Search size={compact ? 15 : 17} className="text-black/25" />}
      placeholder="搜索 API 名称、Endpoint、能力描述或数据源"
      onChange={(event) => {
        setKeyword(event.target.value);
        if (!event.target.value) setSubmittedKeyword('');
      }}
      onSearch={submitSearch}
      className={compact ? 'max-w-[620px]' : 'w-full max-w-[680px]'}
    />
  );

  const searching = Boolean(submittedKeyword.trim());

  return (
    <div className="h-full overflow-y-auto bg-white">
      {searching ? (
        <div className="px-6 py-5">
          <div className="flex items-center gap-3 border-b border-[#eef0f2] pb-5">
            <Button
              type="text"
              icon={<ArrowLeft size={16} />}
              onClick={resetSearch}
            >
              API 集市
            </Button>
            <div className="min-w-0 flex-1">{searchBox(true)}</div>
            <Button icon={<RefreshCw size={15} />} onClick={() => void load()}>刷新</Button>
          </div>

          <div className="mb-3 mt-5 flex items-center justify-between gap-3">
            <div>
              <h1 className="m-0 text-[18px] font-semibold text-[#161823]">搜索结果</h1>
              <div className="mt-1 text-[12px] text-[#98a2b3]">
                “{submittedKeyword}” · 找到 {searchResults.length} 个 API
              </div>
            </div>
          </div>

          <Table<DataServiceApi>
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={searchResults}
            columns={columns}
            pagination={false}
            scroll={{ x: 1080 }}
            locale={{ emptyText: '没有找到匹配的 API' }}
          />
        </div>
      ) : (
        <div className="mx-auto w-full max-w-[1240px] px-8 pb-12 pt-[9vh]">
          <section className="flex flex-col items-center text-center">
            <div className="text-[12px] font-medium tracking-[.18em] text-[#98a2b3]">YAK DATA SERVICE</div>
            <h1 className="mb-0 mt-3 text-[28px] font-semibold tracking-[-.02em] text-[#161823]">API 集市</h1>
            <p className="mb-0 mt-2 max-w-[560px] text-[13px] leading-6 text-[#7b808a]">
              发现已经上线的数据 API，搜索能力、查看契约，并快速进入调试与调用配置。
            </p>
            <div className="mt-7 flex w-full justify-center">{searchBox()}</div>
            <div className="mt-3 flex items-center gap-4 text-[11px] text-[#98a2b3]">
              <span>{services.length} 个 API</span>
              <span className="h-3 w-px bg-[#e4e7ec]" />
              <span>{runningServices.length} 个运行中</span>
              <span className="h-3 w-px bg-[#e4e7ec]" />
              <button type="button" onClick={() => void load()} className="hover:text-[#475467]">刷新集市</button>
            </div>
          </section>

          <section className="mt-14">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2 text-[14px] font-semibold text-[#30323b]">
                  <Sparkles size={15} strokeWidth={1.8} className="text-[#667085]" />
                  推荐 API
                </div>
                <div className="mt-1 text-[11px] text-[#98a2b3]">优先展示当前运行中的近期服务</div>
              </div>
            </div>
            {recommendedServices.length ? (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
                {recommendedServices.map((service) => (
                  <ApiCard
                    key={service.id}
                    service={service}
                    dataSourceName={dataSourceName(service.dataSourceId)}
                    onOpen={() => setDetailTarget(service)}
                  />
                ))}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已上线 API" />
            )}
          </section>

          <section className="mt-12">
            <div className="mb-4">
              <div className="flex items-center gap-2 text-[14px] font-semibold text-[#30323b]">
                <Flame size={15} strokeWidth={1.8} className="text-[#667085]" />
                热门调用
              </div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">基于最近调用记录统计，不额外引入计量服务</div>
            </div>
            {hotServices.length ? (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
                {hotServices.map((service) => (
                  <ApiCard
                    key={service.id}
                    hot
                    service={service}
                    dataSourceName={dataSourceName(service.dataSourceId)}
                    calls={callsByApiId.get(service.id) || 0}
                    onOpen={() => setDetailTarget(service)}
                  />
                ))}
              </div>
            ) : (
              <div className="rounded-[6px] border border-dashed border-[#e4e7ec] px-5 py-8 text-center text-[12px] text-[#98a2b3]">
                暂无调用数据，API 被调用后这里会自动形成热门排行
              </div>
            )}
          </section>
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
