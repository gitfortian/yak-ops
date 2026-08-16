import { Form, Modal, Select, Tag, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  DATA_SERVICE_NODE_SOURCE,
  fetchDataServices,
  fetchDataServiceSources,
  publishDataService,
  type DataServiceSource,
  type DataSourceOption,
} from './service';

interface CreateDataServiceModalProps {
  open: boolean;
  dataSources: DataSourceOption[];
  onCancel: () => void;
  onCreated: () => Promise<void> | void;
}

interface DeployFormValues {
  sourceRef: string;
}

export default function CreateDataServiceModal({
  open,
  dataSources,
  onCancel,
  onCreated,
}: CreateDataServiceModalProps) {
  const [form] = Form.useForm<DeployFormValues>();
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
        sourceType: DATA_SERVICE_NODE_SOURCE,
        pageNo: 1,
        pageSize: 100,
        keyword: keyword?.trim() || undefined,
      });
      setSources(response.data?.records || []);
    } catch (error: any) {
      message.error(error?.message || '加载已发布 Data Service Node 失败');
    } finally {
      setSourceLoading(false);
    }
  }, []);

  const loadPublishedRefs = useCallback(async () => {
    try {
      const response = await fetchDataServices();
      setPublishedRefs(new Set(
        (response.data || [])
          .filter((item) => item.sourceType === DATA_SERVICE_NODE_SOURCE && item.sourceRef)
          .map((item) => item.sourceRef as string),
      ));
    } catch (error: any) {
      message.error(error?.message || '加载 Runtime 部署状态失败');
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setSelectedRef(undefined);
    void Promise.all([loadSources(), loadPublishedRefs()]);
  }, [form, loadPublishedRefs, loadSources, open]);

  const selectSource = (sourceRef?: string) => {
    setSelectedRef(sourceRef);
    form.setFieldValue('sourceRef', sourceRef);
  };

  const save = async () => {
    const values = await form.validateFields();
    if (publishedRefs.has(values.sourceRef)) {
      message.warning('该 Data Service Node 已部署，请在已有 API 中同步最新 Revision');
      return;
    }

    setSaving(true);
    try {
      await publishDataService({
        sourceType: DATA_SERVICE_NODE_SOURCE,
        sourceRef: values.sourceRef,
        enabled: false,
      });
      message.success('API 已部署，当前保持停用');
      onCancel();
      await onCreated();
    } catch (error: any) {
      message.error(error?.message || '部署数据服务失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title="部署 API"
      open={open}
      onCancel={onCancel}
      onOk={() => void save()}
      okText="部署"
      confirmLoading={saving}
      width={680}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" className="pt-3">
        <div className="mb-4 text-[13px] leading-5 text-black/45">
          选择数据开发中已经发布的 Data Service Node。API 定义直接来自最新 DS Revision。
        </div>

        <Form.Item
          name="sourceRef"
          label="Data Service Node"
          rules={[{ required: true, message: '请选择已发布 Data Service Node' }]}
          extra="同一个节点只部署一个 API；后续发布新 DS Revision 后直接同步即可。"
        >
          <Select
            allowClear
            showSearch
            filterOption={false}
            loading={sourceLoading}
            placeholder="搜索已发布 Data Service Node"
            notFoundContent={sourceLoading ? '加载中...' : '暂无可部署节点'}
            onSearch={(value) => void loadSources(value)}
            onChange={selectSource}
            options={sources.map((item) => {
              const published = publishedRefs.has(item.sourceRef);
              return {
                value: item.sourceRef,
                disabled: published,
                label: `${item.name} · DS R${item.sourceRevisionNo || '-'}${published ? ' · 已部署' : ''}`,
              };
            })}
          />
        </Form.Item>

        {selectedSource ? (
          <div className="mb-4 border border-[#e5e7eb] bg-[#fafafa] px-4 py-3">
            <div className="flex items-center gap-2">
              <span className="font-medium text-[#161823]">{selectedSource.name}</span>
              <Tag bordered={false}>DS R{selectedSource.sourceRevisionNo || '-'}</Tag>
            </div>
            <div className="mt-3 space-y-2 text-xs text-black/45">
              <div>
                Endpoint：
                <span className="font-mono text-black/65">GET {selectedSource.defaultPath}</span>
              </div>
              <div>
                数据源：<span className="text-black/65">{dataSourceName}</span>
                <span className="mx-2 text-black/15">·</span>
                最大返回：<span className="text-black/65">{selectedSource.maxRows || 1000} 行</span>
                <span className="mx-2 text-black/15">·</span>
                超时：<span className="text-black/65">{selectedSource.timeoutSeconds || 30}s</span>
              </div>
            </div>
          </div>
        ) : null}

        <div className="border border-[#e5e7eb] bg-white px-4 py-3 text-[12px] leading-5 text-[#667085]">
          部署后默认保持停用。确认接口后，可直接在 API 列表中启用；API Key、Runtime 和调用记录在详情中管理。
        </div>
      </Form>
    </Modal>
  );
}
