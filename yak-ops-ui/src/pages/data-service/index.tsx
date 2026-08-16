import {
  Button,
  Dropdown,
  Form,
  Input,
  InputNumber,
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
  Pencil,
  Plus,
  Power,
  RefreshCw,
  Search,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import CreateDataServiceModal from './CreateDataServiceModal';
import DataServiceDetailDrawer from './DataServiceDetailDrawer';
import {
  deleteDataService,
  fetchDataServices,
  fetchDataSourceOptions,
  setDataServiceEnabled,
  updateDataService,
  type DataServiceApi,
  type DataServiceUpdatePayload,
  type DataSourceOption,
} from './service';

const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';

export default function DataServicePage() {
  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [detailTarget, setDetailTarget] = useState<DataServiceApi>();
  const [editing, setEditing] = useState<DataServiceApi>();
  const [editorOpen, setEditorOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<DataServiceUpdatePayload>();

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
    return dataSources.find((item) => String(item.value) === String(dataSourceId))?.label || `#${dataSourceId}`;
  }, [dataSources]);

  const openEdit = (record: DataServiceApi) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      name: record.name,
      path: record.path,
      maxRows: record.maxRows,
      timeoutSeconds: record.timeoutSeconds,
      enabled: record.enabled,
      description: record.description,
    });
    setEditorOpen(true);
  };

  const saveEdit = async () => {
    if (!editing) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      await updateDataService(editing.id, values);
      message.success('数据服务配置已更新');
      setEditorOpen(false);
      setEditing(undefined);
      await load();
    } catch (error: any) {
      message.error(error?.message || '保存数据服务失败');
    } finally {
      setSaving(false);
    }
  };

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
      title: '删除 API 服务',
      content: `确认删除「${record.name}」？API Key、Runtime 状态和相关文档也会随服务一起移除。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => remove(record),
    });
  };

  const toggleEnabled = async (record: DataServiceApi) => {
    const enabled = !record.enabled;
    try {
      await setDataServiceEnabled(record.id, enabled);
      message.success(enabled ? '服务已启用' : '服务已停用');
      await load();
    } catch (error: any) {
      message.error(error?.message || '状态更新失败');
    }
  };

  const copyPath = async (path: string) => {
    try {
      await navigator.clipboard.writeText(path);
      message.success('调用路径已复制');
    } catch {
      message.warning('复制失败，请手动复制');
    }
  };

  const columns: TableColumnsType<DataServiceApi> = [
    {
      title: 'API 服务',
      dataIndex: 'name',
      minWidth: 240,
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
      minWidth: 290,
      render: (value: string) => (
        <div className="flex items-center gap-1">
          <span className="truncate font-mono text-xs text-black/55">{value}</span>
          <Tooltip title="复制 Endpoint">
            <Button type="text" size="small" icon={<Copy size={13} />} onClick={() => void copyPath(value)} />
          </Tooltip>
        </div>
      ),
    },
    {
      title: '来源',
      key: 'source',
      width: 190,
      render: (_, record) => record.sourceType === 'DATA_DEVELOPMENT_RELEASE' ? (
        <div>
          <div className="font-medium text-[#475467]">数据开发 · SQL v{record.sourceRevisionNo || '-'}</div>
          <div className="mt-1 text-[11px] text-black/35">{dataSourceName(record.dataSourceId)} · Source #{record.sourceRef}</div>
        </div>
      ) : (
        <div>
          <Tag bordered={false}>Legacy</Tag>
          <div className="mt-1 text-[11px] text-black/35">{dataSourceName(record.dataSourceId)}</div>
        </div>
      ),
    },
    {
      title: '访问控制',
      dataIndex: 'authMode',
      width: 110,
      render: (value) => value === 'API_KEY'
        ? <span className="text-black/65">API Key</span>
        : <span className="text-black/40">Public</span>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value: boolean) => value
        ? <Tag bordered={false}>运行中</Tag>
        : <Tag bordered={false}>已停用</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: formatTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
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
                { key: 'edit', icon: <Pencil size={14} />, label: '编辑服务配置' },
                {
                  key: 'toggle',
                  icon: <Power size={14} />,
                  label: record.enabled ? '停用服务' : '启用服务',
                },
                { type: 'divider' },
                { key: 'delete', danger: true, icon: <Trash2 size={14} />, label: '删除服务' },
              ],
              onClick: ({ key }) => {
                if (key === 'edit') openEdit(record);
                if (key === 'toggle') void toggleEnabled(record);
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
          <p className="mb-0 mt-1 text-sm text-black/45">管理由数据开发发布的 API Endpoint、访问控制、运行状态、文档和调用审计。</p>
        </div>
        <Button type="primary" icon={<Plus size={16} />} onClick={() => setCreateOpen(true)}>新建 API</Button>
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
        scroll={{ x: 1220 }}
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
        onEdit={openEdit}
        onChanged={load}
      />

      <Modal
        title="编辑 API 服务"
        open={editorOpen}
        onCancel={() => {
          setEditorOpen(false);
          setEditing(undefined);
        }}
        onOk={() => void saveEdit()}
        okText="保存"
        confirmLoading={saving}
        width={680}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" className="pt-3">
          {editing?.sourceType === 'DATA_DEVELOPMENT_RELEASE' ? (
            <div className="mb-4 border border-[#e5e7eb] bg-[#fafafa] px-4 py-3 text-[12px] leading-5 text-[#667085]">
              <div className="font-medium text-[#344054]">来源：数据开发 SQL · v{editing.sourceRevisionNo || '-'}</div>
              <div className="mt-1">数据源：{dataSourceName(editing.dataSourceId)} · Source #{editing.sourceRef || '-'}</div>
              <div className="mt-1">SQL 与数据源属于上游 Runtime Snapshot；修改执行逻辑请回到数据开发发布新的 ONLINE Revision，再显式更新 API。</div>
            </div>
          ) : (
            <div className="mb-4 border border-[#fecdca] bg-[#fffbfa] px-4 py-3 text-[12px] leading-5 text-[#b42318]">
              <div className="font-medium">Legacy 手工数据服务</div>
              <div className="mt-1 text-[#667085]">
                当前 SQL 与数据源快照已冻结并继续用于 Runtime。Data Service 不再提供 SQL 编辑能力；如需修改查询逻辑，请在数据开发中创建并发布 SQL，再创建新的 API 服务。
              </div>
              <div className="mt-1 text-[#667085]">当前数据源：{dataSourceName(editing?.dataSourceId)}</div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item name="name" label="服务名称" rules={[{ required: true, message: '请输入服务名称' }]}>
              <Input placeholder="例如：用户查询 API" />
            </Form.Item>
            <Form.Item name="path" label="服务路径" rules={[{ required: true, message: '请输入服务路径' }]}>
              <Input addonBefore="GET" placeholder="/users" />
            </Form.Item>
          </div>

          <div className="grid grid-cols-3 gap-x-4">
            <Form.Item name="maxRows" label="最大返回行数" rules={[{ required: true }]}>
              <InputNumber min={1} max={10000} className="w-full" />
            </Form.Item>
            <Form.Item name="timeoutSeconds" label="超时时间（秒）" rules={[{ required: true }]}>
              <InputNumber min={1} max={3600} className="w-full" />
            </Form.Item>
            <Form.Item name="enabled" label="发布状态" valuePropName="checked">
              <Switch checkedChildren="启用" unCheckedChildren="停用" />
            </Form.Item>
          </div>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} maxLength={500} placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
