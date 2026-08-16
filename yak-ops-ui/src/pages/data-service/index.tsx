import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import { Activity, Copy, FileText, Pencil, Plus, RefreshCw, Search, ShieldCheck, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import CreateDataServiceModal from './CreateDataServiceModal';
import DataServiceAccessModal from './DataServiceAccessModal';
import DataServiceDocsModal from './DataServiceDocsModal';
import DataServiceRuntimeModal from './DataServiceRuntimeModal';
import {
  deleteDataService,
  fetchDataServices,
  fetchDataSourceOptions,
  setDataServiceEnabled,
  updateDataService,
  type DataServiceApi,
  type DataServiceSavePayload,
  type DataSourceOption,
} from './service';

const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';

export default function DataServicePage() {
  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<DataServiceApi>();
  const [editorOpen, setEditorOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [docsTarget, setDocsTarget] = useState<DataServiceApi>();
  const [accessTarget, setAccessTarget] = useState<DataServiceApi>();
  const [runtimeTarget, setRuntimeTarget] = useState<DataServiceApi>();
  const [form] = Form.useForm<DataServiceSavePayload>();
  const sourceManaged = Boolean(editing?.sourceType);

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
      const refreshTarget = (current?: DataServiceApi) => current
        ? nextServices.find((item) => item.id === current.id) || current
        : undefined;
      setDocsTarget(refreshTarget);
      setAccessTarget(refreshTarget);
      setRuntimeTarget(refreshTarget);
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
      [item.name, item.path, item.runtimePath, item.description]
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
      dataSourceId: record.dataSourceId,
      sql: record.sql,
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
    const payload: DataServiceSavePayload = {
      ...values,
      dataSourceId: sourceManaged ? editing.dataSourceId : values.dataSourceId,
      sql: sourceManaged ? editing.sql : values.sql,
    };
    setSaving(true);
    try {
      await updateDataService(editing.id, payload);
      message.success('数据服务已更新');
      setEditorOpen(false);
      setEditing(undefined);
      await load();
    } catch (error: any) {
      message.error(error?.message || '保存数据服务失败');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: number) => {
    try {
      await deleteDataService(id);
      message.success('已删除');
      await load();
    } catch (error: any) {
      message.error(error?.message || '删除失败');
    }
  };

  const toggleEnabled = async (record: DataServiceApi, enabled: boolean) => {
    try {
      await setDataServiceEnabled(record.id, enabled);
      setServices((items) => items.map((item) =>
        item.id === record.id ? { ...item, enabled } : item));
      message.success(enabled ? '服务已启用' : '服务已停用');
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
      minWidth: 280,
      render: (_, record) => (
        <div className="py-1">
          <div className="flex items-center gap-2">
            <span className="font-medium text-[#161823]">{record.name}</span>
            <Tag bordered={false}>GET</Tag>
            {record.sourceType === 'DATA_DEVELOPMENT_RELEASE' ? (
              <Tag bordered={false}>数据开发 v{record.sourceRevisionNo || '-'}</Tag>
            ) : (
              <Tag bordered={false}>Legacy</Tag>
            )}
          </div>
          <div className="mt-1 flex items-center gap-1 text-xs text-black/45">
            <span className="font-mono">{record.runtimePath}</span>
            <Tooltip title="复制调用路径">
              <Button type="text" size="small" icon={<Copy size={13} />} onClick={() => void copyPath(record.runtimePath)} />
            </Tooltip>
          </div>
        </div>
      ),
    },
    {
      title: '数据源',
      dataIndex: 'dataSourceId',
      width: 190,
      render: (value) => dataSourceName(value),
    },
    {
      title: '参数',
      dataIndex: 'parameterNames',
      width: 180,
      render: (values: string[]) => values?.length
        ? <span className="text-black/65">{values.map((item) => `:${item}`).join(' · ')}</span>
        : <span className="text-black/35">无参数</span>,
    },
    {
      title: '访问控制',
      dataIndex: 'authMode',
      width: 120,
      render: (value) => value === 'API_KEY'
        ? <Tag bordered={false}>API Key</Tag>
        : <span className="text-black/40">公开</span>,
    },
    {
      title: '运行限制',
      width: 150,
      render: (_, record) => (
        <span className="text-black/55">{record.maxRows} 行 · {record.timeoutSeconds}s</span>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: formatTime,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (_, record) => (
        <Switch size="small" checked={record.enabled} onChange={(checked) => void toggleEnabled(record, checked)} />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <Space size={2}>
          <Tooltip title="API 文档 / 在线调试">
            <Button type="text" size="small" icon={<FileText size={15} />} onClick={() => setDocsTarget(record)} />
          </Tooltip>
          <Tooltip title="Runtime">
            <Button type="text" size="small" icon={<Activity size={15} />} onClick={() => setRuntimeTarget(record)} />
          </Tooltip>
          <Tooltip title="访问控制">
            <Button type="text" size="small" icon={<ShieldCheck size={15} />} onClick={() => setAccessTarget(record)} />
          </Tooltip>
          <Tooltip title="编辑">
            <Button type="text" size="small" icon={<Pencil size={15} />} onClick={() => openEdit(record)} />
          </Tooltip>
          <Popconfirm title="确认删除这个数据服务？" onConfirm={() => void remove(record.id)}>
            <Tooltip title="删除"><Button type="text" size="small" danger icon={<Trash2 size={15} />} /></Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="h-full bg-white px-6 py-5">
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <h1 className="m-0 text-xl font-semibold text-[#161823]">API 服务</h1>
          <p className="mb-0 mt-1 text-sm text-black/45">将数据开发中已发布的 SQL 转换为可调用、可鉴权、可缓存、可熔断、可文档化的 GET REST API。</p>
        </div>
        <Button type="primary" icon={<Plus size={16} />} onClick={() => setCreateOpen(true)}>新建 API</Button>
      </div>

      <div className="mb-3 flex items-center justify-between gap-3">
        <Input
          allowClear
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          prefix={<Search size={15} className="text-black/30" />}
          placeholder="搜索名称或路径"
          className="max-w-[320px]"
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
        scroll={{ x: 1360 }}
      />

      <CreateDataServiceModal
        open={createOpen}
        dataSources={dataSources}
        onCancel={() => setCreateOpen(false)}
        onCreated={load}
      />

      <DataServiceDocsModal
        open={Boolean(docsTarget)}
        service={docsTarget}
        onCancel={() => setDocsTarget(undefined)}
      />

      <DataServiceAccessModal
        open={Boolean(accessTarget)}
        service={accessTarget}
        onCancel={() => setAccessTarget(undefined)}
        onChanged={load}
      />

      <DataServiceRuntimeModal
        open={Boolean(runtimeTarget)}
        service={runtimeTarget}
        onCancel={() => setRuntimeTarget(undefined)}
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
        width={760}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" className="pt-3">
          {sourceManaged ? (
            <div className="mb-4 border border-[#e5e7eb] bg-[#fafafa] px-4 py-3 text-[12px] leading-5 text-[#667085]">
              <div className="font-medium text-[#344054]">来源：数据开发 SQL · v{editing?.sourceRevisionNo || '-'}</div>
              <div className="mt-1">数据源：{dataSourceName(editing?.dataSourceId)} · Source #{editing?.sourceRef || '-'}</div>
              <div className="mt-1">SQL 与数据源由上游发布版本管理；需要修改执行逻辑时，请回到数据开发发布新 Revision。</div>
            </div>
          ) : (
            <div className="mb-4 border border-[#e5e7eb] bg-[#fafafa] px-4 py-3 text-[12px] leading-5 text-[#667085]">
              这是旧版手工创建的数据服务，当前继续保留 SQL 与数据源编辑能力用于兼容。新的 API 请从数据开发已发布 SQL 创建。
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

          {!sourceManaged ? (
            <>
              <Form.Item
                name="dataSourceId"
                label="数据源"
                rules={[{ required: true, message: '请选择数据源' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择已有数据源"
                  options={dataSources.map((item) => ({ ...item, value: Number(item.value) || item.value }))}
                />
              </Form.Item>
              <Form.Item
                name="sql"
                label="SELECT SQL"
                extra="Legacy 兼容模式。新的 SQL 开发请统一在数据开发中完成。"
                rules={[{ required: true, message: '请输入 SQL' }]}
              >
                <Input.TextArea
                  rows={10}
                  spellCheck={false}
                  placeholder={'SELECT id, username\nFROM sys_user\nWHERE department = :department'}
                  style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace' }}
                />
              </Form.Item>
            </>
          ) : null}

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
