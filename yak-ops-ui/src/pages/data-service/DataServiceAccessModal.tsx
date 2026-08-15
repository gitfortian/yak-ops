import {
  Button,
  DatePicker,
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
import dayjs, { type Dayjs } from 'dayjs';
import { Copy, KeyRound, Pencil, RefreshCw, RotateCw, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  createDataServiceKey,
  deleteDataServiceKey,
  fetchDataServiceKeys,
  rotateDataServiceKey,
  setDataServiceAuthMode,
  setDataServiceKeyEnabled,
  updateDataServiceKey,
  type CreatedDataServiceApiKey,
  type DataServiceApi,
  type DataServiceApiKey,
  type DataServiceAuthMode,
} from './service';

interface DataServiceAccessModalProps {
  open: boolean;
  service?: DataServiceApi;
  onCancel: () => void;
  onChanged: () => void | Promise<void>;
}

interface KeyFormValues {
  name: string;
  rateLimitPerMinute: number;
  expiresAt?: Dayjs | null;
}

const formatTime = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 19) : '-';

const DataServiceAccessModal = ({
  open,
  service,
  onCancel,
  onChanged,
}: DataServiceAccessModalProps) => {
  const [keys, setKeys] = useState<DataServiceApiKey[]>([]);
  const [loading, setLoading] = useState(false);
  const [authSaving, setAuthSaving] = useState(false);
  const [keyEditorOpen, setKeyEditorOpen] = useState(false);
  const [editingKey, setEditingKey] = useState<DataServiceApiKey>();
  const [keySaving, setKeySaving] = useState(false);
  const [secretResult, setSecretResult] = useState<CreatedDataServiceApiKey>();
  const [rotatingId, setRotatingId] = useState<number>();
  const [form] = Form.useForm<KeyFormValues>();

  const loadKeys = useCallback(async () => {
    if (!service) return;
    setLoading(true);
    try {
      const response = await fetchDataServiceKeys(service.id);
      setKeys(response.data || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API Key 失败');
      setKeys([]);
    } finally {
      setLoading(false);
    }
  }, [service]);

  useEffect(() => {
    if (!open || !service) return;
    void loadKeys();
  }, [loadKeys, open, service]);

  const validKeyCount = useMemo(() => keys.filter((key) => (
    key.enabled && (!key.expiresAt || dayjs(key.expiresAt).isAfter(dayjs()))
  )).length, [keys]);

  const changeAuthMode = async (mode: DataServiceAuthMode) => {
    if (!service || mode === service.authMode) return;
    if (mode === 'API_KEY' && validKeyCount === 0) {
      message.warning('请先创建至少一个有效 API Key，再启用鉴权');
      return;
    }
    setAuthSaving(true);
    try {
      await setDataServiceAuthMode(service.id, mode);
      message.success(mode === 'API_KEY' ? '已启用 API Key 鉴权' : '已切换为公开访问');
      await onChanged();
    } catch (error: any) {
      message.error(error?.message || '更新访问控制失败');
    } finally {
      setAuthSaving(false);
    }
  };

  const openCreate = () => {
    setEditingKey(undefined);
    form.resetFields();
    form.setFieldsValue({ rateLimitPerMinute: 60 });
    setKeyEditorOpen(true);
  };

  const openEdit = (key: DataServiceApiKey) => {
    setEditingKey(key);
    form.setFieldsValue({
      name: key.name,
      rateLimitPerMinute: key.rateLimitPerMinute,
      expiresAt: key.expiresAt ? dayjs(key.expiresAt) : null,
    });
    setKeyEditorOpen(true);
  };

  const saveKey = async () => {
    if (!service) return;
    const values = await form.validateFields();
    setKeySaving(true);
    try {
      const expiresAt = values.expiresAt?.format('YYYY-MM-DDTHH:mm:ss') || null;
      if (editingKey) {
        await updateDataServiceKey(service.id, editingKey.id, {
          name: values.name.trim(),
          rateLimitPerMinute: values.rateLimitPerMinute,
          expiresAt,
          expiresAtSet: true,
        });
        message.success('API Key 配置已更新');
      } else {
        const response = await createDataServiceKey(service.id, {
          name: values.name.trim(),
          rateLimitPerMinute: values.rateLimitPerMinute,
          expiresAt,
        });
        setSecretResult(response.data);
        message.success('API Key 已创建');
      }
      setKeyEditorOpen(false);
      await loadKeys();
    } catch (error: any) {
      message.error(error?.message || '保存 API Key 失败');
    } finally {
      setKeySaving(false);
    }
  };

  const toggleKey = async (key: DataServiceApiKey, enabled: boolean) => {
    if (!service) return;
    try {
      await setDataServiceKeyEnabled(service.id, key.id, enabled);
      message.success(enabled ? 'API Key 已启用' : 'API Key 已停用');
      await loadKeys();
    } catch (error: any) {
      message.error(error?.message || '更新 API Key 状态失败');
    }
  };

  const rotateKey = async (key: DataServiceApiKey) => {
    if (!service) return;
    setRotatingId(key.id);
    try {
      const response = await rotateDataServiceKey(service.id, key.id);
      setSecretResult(response.data);
      message.success('API Key 已轮换，旧 Key 立即失效');
      await loadKeys();
    } catch (error: any) {
      message.error(error?.message || '轮换 API Key 失败');
    } finally {
      setRotatingId(undefined);
    }
  };

  const removeKey = async (key: DataServiceApiKey) => {
    if (!service) return;
    try {
      await deleteDataServiceKey(service.id, key.id);
      message.success('API Key 已删除');
      await loadKeys();
    } catch (error: any) {
      message.error(error?.message || '删除 API Key 失败');
    }
  };

  const copySecret = async () => {
    if (!secretResult?.secret) return;
    try {
      await navigator.clipboard.writeText(secretResult.secret);
      message.success('API Key 已复制');
    } catch {
      message.warning('复制失败，请手动复制');
    }
  };

  const columns: TableColumnsType<DataServiceApiKey> = [
    {
      title: '调用方',
      dataIndex: 'name',
      minWidth: 190,
      render: (_, record) => (
        <div className="py-1">
          <div className="font-medium text-[#161823]">{record.name}</div>
          <div className="mt-1 font-mono text-[11px] text-black/40">{record.keyPrefix}••••</div>
        </div>
      ),
    },
    {
      title: '限流',
      dataIndex: 'rateLimitPerMinute',
      width: 120,
      render: (value) => <span className="text-black/60">{value} 次/分钟</span>,
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      width: 170,
      render: (value) => value ? formatTime(value) : <span className="text-black/35">永不过期</span>,
    },
    {
      title: '最后调用',
      dataIndex: 'lastUsedAt',
      width: 170,
      render: formatTime,
    },
    {
      title: '状态',
      width: 90,
      render: (_, record) => {
        const expired = Boolean(record.expiresAt && dayjs(record.expiresAt).isBefore(dayjs()));
        if (expired) return <Tag bordered={false}>已过期</Tag>;
        return (
          <Switch
            size="small"
            checked={record.enabled}
            onChange={(checked) => void toggleKey(record, checked)}
          />
        );
      },
    },
    {
      title: '操作',
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Space size={0}>
          <Tooltip title="编辑">
            <Button type="text" size="small" icon={<Pencil size={14} />} onClick={() => openEdit(record)} />
          </Tooltip>
          <Popconfirm
            title="轮换后旧 Key 会立即失效，确认继续？"
            onConfirm={() => void rotateKey(record)}
          >
            <Tooltip title="轮换 Key">
              <Button
                type="text"
                size="small"
                loading={rotatingId === record.id}
                icon={<RotateCw size={14} />}
              />
            </Tooltip>
          </Popconfirm>
          <Popconfirm title="确认删除这个 API Key？" onConfirm={() => void removeKey(record)}>
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<Trash2 size={14} />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Modal
        title={service ? `访问控制 · ${service.name}` : '访问控制'}
        open={open}
        onCancel={onCancel}
        footer={null}
        width={900}
        destroyOnHidden
      >
        {service ? (
          <div className="pt-2">
            <div className="mb-4 flex items-center justify-between border border-[#e5e7eb] bg-[#fafafa] px-4 py-3">
              <div>
                <div className="text-[13px] font-medium text-[#344054]">Runtime 访问模式</div>
                <div className="mt-1 text-[11px] text-[#98a2b3]">
                  API Key 模式通过请求头 <span className="font-mono text-[#667085]">X-API-Key</span> 鉴权；控制台测试不受影响。
                </div>
              </div>
              <Select<DataServiceAuthMode>
                value={service.authMode || 'NONE'}
                loading={authSaving}
                disabled={authSaving}
                className="w-[150px]"
                options={[
                  { label: '公开访问', value: 'NONE' },
                  { label: 'API Key', value: 'API_KEY' },
                ]}
                onChange={(value) => void changeAuthMode(value)}
              />
            </div>

            <div className="mb-3 flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2 text-[13px] font-medium text-[#344054]">
                  <KeyRound size={15} /> API Keys
                </div>
                <div className="mt-1 text-[11px] text-[#98a2b3]">
                  为不同调用系统创建独立 Key，可分别设置每分钟调用上限和过期时间。
                </div>
              </div>
              <Space>
                <Button size="small" icon={<RefreshCw size={14} />} onClick={() => void loadKeys()}>刷新</Button>
                <Button type="primary" size="small" icon={<KeyRound size={14} />} onClick={openCreate}>创建 Key</Button>
              </Space>
            </div>

            <Table<DataServiceApiKey>
              rowKey="id"
              size="small"
              loading={loading}
              columns={columns}
              dataSource={keys}
              pagination={false}
              scroll={{ x: 900 }}
              locale={{ emptyText: '还没有 API Key' }}
            />

            <div className="mt-4 bg-[#f8f9fb] px-3 py-2.5 text-[11px] leading-5 text-[#667085]">
              明文 Key 只在创建或轮换完成时展示一次，服务端仅保存 SHA-256 摘要。当前限流为单实例固定分钟窗口；后续如部署多实例，可平滑替换为 Redis/网关级限流。
            </div>
          </div>
        ) : null}
      </Modal>

      <Modal
        title={editingKey ? '编辑 API Key' : '创建 API Key'}
        open={keyEditorOpen}
        onCancel={() => setKeyEditorOpen(false)}
        onOk={() => void saveKey()}
        okText={editingKey ? '保存' : '创建'}
        confirmLoading={keySaving}
        width={520}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" className="pt-3">
          <Form.Item name="name" label="调用方名称" rules={[{ required: true, message: '请输入调用方名称' }]}>
            <Input maxLength={128} placeholder="例如：运营 BI / CRM / 第三方应用" />
          </Form.Item>
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item
              name="rateLimitPerMinute"
              label="每分钟调用上限"
              rules={[{ required: true, message: '请输入调用上限' }]}
            >
              <InputNumber min={1} max={100000} precision={0} className="w-full" />
            </Form.Item>
            <Form.Item name="expiresAt" label="过期时间">
              <DatePicker showTime className="w-full" placeholder="留空表示永不过期" />
            </Form.Item>
          </div>
        </Form>
      </Modal>

      <Modal
        title="保存 API Key"
        open={Boolean(secretResult)}
        onCancel={() => setSecretResult(undefined)}
        footer={[
          <Button key="copy" icon={<Copy size={14} />} onClick={() => void copySecret()}>复制 Key</Button>,
          <Button key="done" type="primary" onClick={() => setSecretResult(undefined)}>我已保存</Button>,
        ]}
        width={620}
        closable={false}
        maskClosable={false}
      >
        <div className="py-2">
          <div className="mb-3 border border-[#fedf89] bg-[#fffaeb] px-3 py-2.5 text-[12px] leading-5 text-[#7a2e0e]">
            这是唯一一次显示完整 API Key。关闭后无法再次查看，只能重新轮换。
          </div>
          <Input.TextArea
            readOnly
            autoSize={{ minRows: 3, maxRows: 4 }}
            value={secretResult?.secret}
            className="font-mono"
          />
          <div className="mt-3 text-[11px] text-[#667085]">
            调用方式：<span className="font-mono">X-API-Key: {secretResult?.secret}</span>
          </div>
        </div>
      </Modal>
    </>
  );
};

export default DataServiceAccessModal;
