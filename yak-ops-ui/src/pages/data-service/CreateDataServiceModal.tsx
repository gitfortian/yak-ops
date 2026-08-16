import { Form, Input, InputNumber, Modal, Select, Switch, Tag, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  DATA_DEVELOPMENT_RELEASE_SOURCE,
  fetchDataServices,
  fetchDataServiceSources,
  publishDataService,
  type DataServicePublishPayload,
  type DataServiceSource,
  type DataSourceOption,
} from './service';

interface CreateDataServiceModalProps {
  open: boolean;
  dataSources: DataSourceOption[];
  onCancel: () => void;
  onCreated: () => Promise<void> | void;
}

type CreateFormValues = Omit<DataServicePublishPayload, 'sourceType'>;

const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';

export default function CreateDataServiceModal({
  open,
  dataSources,
  onCancel,
  onCreated,
}: CreateDataServiceModalProps) {
  const [form] = Form.useForm<CreateFormValues>();
  const [sources, setSources] = useState<DataServiceSource[]>([]);
  const [publishedRefs, setPublishedRefs] = useState<Set<string>>(new Set());
  const [sourceLoading, setSourceLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [selectedRef, setSelectedRef] = useState<string>();

  const selectedSource = useMemo(
    () => sources.find((item) => item.sourceRef === selectedRef),
    [selectedRef, sources],
  );

  const dataSourceName = useMemo(() => {
    if (!selectedSource) return '-';
    return dataSources.find((item) => String(item.value) === String(selectedSource.dataSourceId))?.label
      || `#${selectedSource.dataSourceId}`;
  }, [dataSources, selectedSource]);

  const loadSources = useCallback(async (keyword?: string) => {
    setSourceLoading(true);
    try {
      const response = await fetchDataServiceSources({
        sourceType: DATA_DEVELOPMENT_RELEASE_SOURCE,
        pageNo: 1,
        pageSize: 100,
        keyword: keyword?.trim() || undefined,
      });
      setSources(response.data?.records || []);
    } catch (error: any) {
      message.error(error?.message || '加载可发布 SQL 失败');
    } finally {
      setSourceLoading(false);
    }
  }, []);

  const loadPublishedRefs = useCallback(async () => {
    try {
      const response = await fetchDataServices();
      setPublishedRefs(new Set(
        (response.data || [])
          .filter((item) => item.sourceType === DATA_DEVELOPMENT_RELEASE_SOURCE && item.sourceRef)
          .map((item) => item.sourceRef as string),
      ));
    } catch (error: any) {
      message.error(error?.message || '加载已发布 API 状态失败');
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ maxRows: 1000, timeoutSeconds: 30, enabled: false });
    setSelectedRef(undefined);
    void Promise.all([loadSources(), loadPublishedRefs()]);
  }, [form, loadPublishedRefs, loadSources, open]);

  const selectSource = (sourceRef?: string) => {
    setSelectedRef(sourceRef);
    if (!sourceRef) return;
    const source = sources.find((item) => item.sourceRef === sourceRef);
    if (!source || publishedRefs.has(sourceRef)) return;
    form.setFieldsValue({
      sourceRef,
      name: `${source.name} API`,
      path: source.defaultPath,
      maxRows: 1000,
      timeoutSeconds: source.timeoutSeconds || 30,
      enabled: false,
    });
  };

  const save = async () => {
    const values = await form.validateFields();
    if (publishedRefs.has(values.sourceRef)) {
      message.warning('该 SQL 已发布为数据服务，请从已有 API 或数据开发执行更新');
      return;
    }
    setSaving(true);
    try {
      await publishDataService({
        sourceType: DATA_DEVELOPMENT_RELEASE_SOURCE,
        ...values,
      });
      message.success('数据服务已创建');
      onCancel();
      await onCreated();
    } catch (error: any) {
      message.error(error?.message || '创建数据服务失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="新建 API 服务"
      open={open}
      onCancel={onCancel}
      onOk={() => void save()}
      okText="创建 API"
      confirmLoading={saving}
      width={720}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" className="pt-3">
        <div className="mb-4 text-[13px] leading-5 text-black/45">
          选择数据开发中已上线的 SQL 发布版本。SQL 与数据源由来源版本管理，这里只配置 API 的服务侧参数。
        </div>

        <Form.Item
          name="sourceRef"
          label="来源 SQL"
          rules={[{ required: true, message: '请选择已发布 SQL' }]}
          extra="仅展示数据开发中 ONLINE 的 SQL 发布版本；已创建 API 的来源会保留展示但不可重复选择。"
        >
          <Select
            allowClear
            showSearch
            filterOption={false}
            loading={sourceLoading}
            placeholder="搜索并选择已发布 SQL"
            notFoundContent={sourceLoading ? '加载中...' : '暂无可发布 SQL，请先在数据开发完成 SQL 发布'}
            onSearch={(value) => void loadSources(value)}
            onChange={selectSource}
            options={sources.map((item) => {
              const published = publishedRefs.has(item.sourceRef);
              return {
                value: item.sourceRef,
                disabled: published,
                label: `${item.name} · v${item.sourceRevisionNo || '-'}${published ? ' · 已发布' : ''}`,
              };
            })}
          />
        </Form.Item>

        {selectedSource ? (
          <div className="mb-5 border border-[#e5e7eb] bg-[#fafafa] px-4 py-3">
            <div className="flex items-center gap-2">
              <span className="font-medium text-[#161823]">{selectedSource.name}</span>
              <Tag bordered={false}>SQL</Tag>
              <Tag bordered={false}>v{selectedSource.sourceRevisionNo || '-'}</Tag>
            </div>
            <div className="mt-2 grid grid-cols-2 gap-x-6 gap-y-1 text-xs text-black/45">
              <div>数据源：<span className="text-black/65">{dataSourceName}</span></div>
              <div>更新时间：<span className="text-black/65">{formatTime(selectedSource.updateTime)}</span></div>
              <div>来源：<span className="text-black/65">数据开发 · 已发布</span></div>
              <div>Revision：<span className="text-black/65">#{selectedSource.sourceRevisionId}</span></div>
            </div>
          </div>
        ) : null}

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
  );
}
