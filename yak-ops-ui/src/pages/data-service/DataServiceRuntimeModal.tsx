import { Button, InputNumber, Modal, Spin, Switch, Tag, message } from 'antd';
import { RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState, type ReactNode } from 'react';

import {
  fetchDataServiceRuntime,
  updateDataServiceRuntime,
  type DataServiceApi,
  type DataServiceRuntimeConfig,
  type DataServiceRuntimeStatus,
} from './service';

interface DataServiceRuntimeModalProps {
  open: boolean;
  service?: DataServiceApi;
  onCancel: () => void;
}

const percent = (value?: number) => `${Math.round((value || 0) * 1000) / 10}%`;

const circuitLabel: Record<DataServiceRuntimeStatus['circuitState'], string> = {
  DISABLED: '未启用',
  CLOSED: '正常',
  OPEN: '已熔断',
  HALF_OPEN: '恢复探测',
};

const DataServiceRuntimeModal = ({ open, service, onCancel }: DataServiceRuntimeModalProps) => {
  const [status, setStatus] = useState<DataServiceRuntimeStatus>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [config, setConfig] = useState<DataServiceRuntimeConfig>({
    cacheEnabled: false,
    cacheTtlSeconds: 60,
    cacheMaxEntries: 200,
    circuitBreakerEnabled: true,
    failureThreshold: 5,
    recoverySeconds: 30,
  });

  const load = useCallback(async () => {
    if (!service) return;
    setLoading(true);
    try {
      const response = await fetchDataServiceRuntime(service.id);
      const next = response.data;
      if (!next) throw new Error(response.message || response.msg || '加载 Runtime 状态失败');
      setStatus(next);
      setConfig({
        cacheEnabled: next.cacheEnabled,
        cacheTtlSeconds: next.cacheTtlSeconds,
        cacheMaxEntries: next.cacheMaxEntries,
        circuitBreakerEnabled: next.circuitBreakerEnabled,
        failureThreshold: next.failureThreshold,
        recoverySeconds: next.recoverySeconds,
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 Runtime 状态失败');
    } finally {
      setLoading(false);
    }
  }, [service]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  const save = async () => {
    if (!service) return;
    setSaving(true);
    try {
      const response = await updateDataServiceRuntime(service.id, config);
      if (!response.data) throw new Error(response.message || response.msg || '保存 Runtime 配置失败');
      setStatus(response.data);
      message.success('Runtime 配置已更新，旧缓存与熔断状态已重置');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Runtime 配置失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={service ? `Runtime · ${service.name}` : 'Runtime'}
      width={760}
      centered
      destroyOnHidden
      onCancel={onCancel}
      footer={[
        <Button key="refresh" icon={<RefreshCw size={14} />} onClick={() => void load()} disabled={loading || saving}>
          刷新指标
        </Button>,
        <Button key="cancel" onClick={onCancel}>关闭</Button>,
        <Button key="save" type="primary" loading={saving} onClick={() => void save()}>
          保存配置
        </Button>,
      ]}
    >
      <Spin spinning={loading}>
        <div className="space-y-5 py-2">
          <div>
            <div className="mb-2 text-[12px] font-medium text-[#344054]">运行指标</div>
            <div className="grid grid-cols-4 gap-2">
              <Metric label="总调用" value={String(status?.totalCalls || 0)} />
              <Metric label="成功率" value={percent(status?.successRate)} />
              <Metric label="平均耗时" value={`${status?.averageDurationMs || 0} ms`} />
              <Metric label="P95" value={`${status?.p95DurationMs || 0} ms`} />
              <Metric label="缓存命中" value={String(status?.cacheHits || 0)} />
              <Metric label="命中率" value={percent(status?.cacheHitRate)} />
              <Metric label="失败" value={String(status?.failureCalls || 0)} />
              <Metric label="熔断拒绝" value={String(status?.circuitRejected || 0)} />
            </div>
          </div>

          <div className="border border-[#e5e7eb]">
            <div className="flex items-center justify-between border-b border-[#eef0f2] bg-[#fafafa] px-3 py-2.5">
              <div>
                <div className="text-[12px] font-medium text-[#344054]">结果缓存</div>
                <div className="mt-0.5 text-[10px] text-[#98a2b3]">仅缓存外部 Runtime 的成功 SELECT 结果；控制台测试始终访问真实数据源。</div>
              </div>
              <Switch
                size="small"
                checked={config.cacheEnabled}
                onChange={(value) => setConfig((current) => ({ ...current, cacheEnabled: value }))}
              />
            </div>
            <div className="grid grid-cols-3 gap-3 px-3 py-3">
              <Field label="TTL（秒）">
                <InputNumber
                  className="w-full"
                  min={1}
                  max={3600}
                  disabled={!config.cacheEnabled}
                  value={config.cacheTtlSeconds}
                  onChange={(value) => setConfig((current) => ({ ...current, cacheTtlSeconds: value || 60 }))}
                />
              </Field>
              <Field label="最大条目数">
                <InputNumber
                  className="w-full"
                  min={1}
                  max={5000}
                  disabled={!config.cacheEnabled}
                  value={config.cacheMaxEntries}
                  onChange={(value) => setConfig((current) => ({ ...current, cacheMaxEntries: value || 200 }))}
                />
              </Field>
              <Field label="当前条目">
                <div className="flex h-8 items-center text-[12px] text-[#475467]">{status?.cacheEntries || 0}</div>
              </Field>
            </div>
          </div>

          <div className="border border-[#e5e7eb]">
            <div className="flex items-center justify-between border-b border-[#eef0f2] bg-[#fafafa] px-3 py-2.5">
              <div>
                <div className="flex items-center gap-2 text-[12px] font-medium text-[#344054]">
                  熔断保护
                  {status ? <Tag bordered={false}>{circuitLabel[status.circuitState]}</Tag> : null}
                </div>
                <div className="mt-0.5 text-[10px] text-[#98a2b3]">连续执行失败后短暂停止访问下游，恢复窗口结束后放行一次探测请求。</div>
              </div>
              <Switch
                size="small"
                checked={config.circuitBreakerEnabled}
                onChange={(value) => setConfig((current) => ({ ...current, circuitBreakerEnabled: value }))}
              />
            </div>
            <div className="grid grid-cols-3 gap-3 px-3 py-3">
              <Field label="连续失败阈值">
                <InputNumber
                  className="w-full"
                  min={1}
                  max={20}
                  disabled={!config.circuitBreakerEnabled}
                  value={config.failureThreshold}
                  onChange={(value) => setConfig((current) => ({ ...current, failureThreshold: value || 5 }))}
                />
              </Field>
              <Field label="恢复等待（秒）">
                <InputNumber
                  className="w-full"
                  min={1}
                  max={300}
                  disabled={!config.circuitBreakerEnabled}
                  value={config.recoverySeconds}
                  onChange={(value) => setConfig((current) => ({ ...current, recoverySeconds: value || 30 }))}
                />
              </Field>
              <Field label="当前状态">
                <div className="flex h-8 items-center text-[12px] text-[#475467]">
                  {status ? circuitLabel[status.circuitState] : '-'}
                </div>
              </Field>
            </div>
          </div>

          <div className="bg-[#f8f9fb] px-3 py-2.5 text-[11px] leading-5 text-[#667085]">
            当前缓存和熔断均为单实例 Runtime 能力。SQL 重新发布、服务编辑或 Runtime 配置变更会立即清空旧缓存并重置熔断状态；后续多实例部署可将相同接口替换为 Redis / API Gateway 实现。
          </div>
        </div>
      </Spin>
    </Modal>
  );
};

const Metric = ({ label, value }: { label: string; value: string }) => (
  <div className="border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2.5">
    <div className="text-[10px] text-[#98a2b3]">{label}</div>
    <div className="mt-1 text-[14px] font-medium text-[#344054]">{value}</div>
  </div>
);

const Field = ({ label, children }: { label: string; children: ReactNode }) => (
  <div>
    <div className="mb-1.5 text-[11px] font-medium text-[#475467]">{label}</div>
    {children}
  </div>
);

export default DataServiceRuntimeModal;
