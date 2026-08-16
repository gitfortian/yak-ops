import {
  Button,
  Dropdown,
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
  Copy,
  Eye,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Search,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import CreateDataServiceModal from './CreateDataServiceModal';
import DataServiceDetailDrawer from './DataServiceDetailDrawer';
import {
  DATA_SERVICE_NODE_SOURCE,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
  deleteDataService,
  fetchDataServices,
  fetchDataSourceOptions,
  setDataServiceEnabled,
  type DataServiceApi,
  type DataSourceOption,
} from './service';

export default function DataServicePage() {
  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [detailTarget, setDetailTarget] = useState<DataServiceApi>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [serviceResponse, dataSourceResponse] = await Promise.all([
        fetchDataServices(),
        fetchDataSourceOptions(),
      ]);
      const nextServices = serviceResponse.data || [];
      setServices(nextServices);
      setDataSources(dataSourceResponse.data || []);
      setDetailTarget((current) => current
        ? nextServices.find((item) => item.id === current.id)
        : undefined);
    } catch (error: any) {
      message.error(error?.message || '加载数据服务失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    if (!value) return services;
    return services.filter((item) =>
      [item.name, item.path, item.runtimePath, item.description, item.sourceRef]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(value)));
  }, [keyword, services]);

  const dataSourceName = useCallback((dataSourceId?: number) => {
    if (!dataSourceId) return '-';
    return dataSources.find((item) => String(item.value) === String(dataSourceId))?.label
      || `#${dataSourceId}`;
  }, [dataSources]);

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
      content: `确认删除「${record.name}」？API Key、Runtime 状态和相关文档也会一起移除。`,
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
      minWidth: 300,
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
      width: 220,
      render: (_, record) => {
        if (record.sourceType === DATA_SERVICE_NODE_SOURCE) {
          return (
            <div>
              <div className="font-medium text-[#475467]">Data Service · DS R{record.sourceRevisionNo || '-'}</div>
              <div className="mt-1 text-[11px] text-black/35">
                {dataSourceName(record.dataSourceId)} · Node #{record.sourceRef}
              </div>
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
        return (
          <div>
            <Tag bordered={false}>Legacy</Tag>
            <div className="mt-1 text-[11px] text-black/35">{dataSourceName(record.dataSourceId)}</div>
          </div>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 145,
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

  return (
    <div className="h-full bg-white px-6 py-5">
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <h1 className="m-0 text-xl font-semibold text-[#161823]">API 服务</h1>
          <p className="mb-0 mt-1 text-sm text-black/45">
            运行已发布的 Data Service，管理启停、API Key、Runtime、OpenAPI 和调用记录。
          </p>
        </div>
        <Button type="primary" icon={<Plus size={16} />} onClick={() => setCreateOpen(true)}>
          部署 API
        </Button>
      </div>

      <div className="mb-3 flex items-center justify-between gap-3">
        <Input
          allowClear
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          prefix={<Search size={15} className="text-black/30" />}
          placeholder="搜索 API、Endpoint 或来源"
          className="max-w-[340px]"
        />
        <Button icon={<RefreshCw size={15} />} onClick={() => void load()}>刷新</Button>
      </div>

      <Table<DataServiceApi>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={filtered}
        columns={columns}
        pagination={false}
        scroll={{ x: 1050 }}
      />

      <CreateDataServiceModal
        open={createOpen}
        dataSources={dataSources}
        onCancel={() => setCreateOpen(false)}
        onCreated={load}
      />

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
