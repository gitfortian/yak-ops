import { YakButton } from '@/components/ui';
import {
  updateDataServiceConsumerAccess,
  type DataServiceAccessOverviewItem,
  type DataServiceConsumer,
  type DataServiceConsumerAccessScope,
} from '@/services/data-service';
import { Select, message } from 'antd';
import { CheckCircle2, Layers3 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

interface ConsumerApiAccessPanelProps {
  consumer: DataServiceConsumer;
  apis: DataServiceAccessOverviewItem[];
  onChanged: (next: DataServiceConsumer) => void;
}

export default function ConsumerApiAccessPanel({
  consumer,
  apis,
  onChanged,
}: ConsumerApiAccessPanelProps) {
  const [scope, setScope] = useState<DataServiceConsumerAccessScope>(consumer.accessScope);
  const [apiIds, setApiIds] = useState<number[]>(consumer.apiIds || []);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setScope(consumer.accessScope);
    setApiIds(consumer.apiIds || []);
  }, [consumer]);

  const options = useMemo(
    () => apis.map((api) => ({
      value: api.apiId,
      label: `${api.name}  ·  ${api.path}`,
    })),
    [apis],
  );

  const save = async () => {
    setSaving(true);
    try {
      const next = await updateDataServiceConsumerAccess(consumer.id, {
        accessScope: scope,
        apiIds: scope === 'SELECTED' ? apiIds : [],
      });
      onChanged(next);
      message.success('API 权限已更新');
    } catch (error: any) {
      message.error(error?.message || '更新 API 权限失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-3">
      <section className="rounded-lg bg-white p-5">
        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-[#f5f6f8] text-[#475467]">
            <Layers3 size={17} />
          </div>
          <div className="min-w-0 flex-1">
            <div className="text-[15px] font-semibold text-[#161823]">API 权限</div>
            <div className="mt-1 text-[11px] leading-5 text-[#8a8f98]">
              一个调用方使用同一组凭证访问多个 API，不再为每个接口重复创建 Key。
            </div>
          </div>
        </div>

        <div className="mt-5 grid gap-2 md:grid-cols-2">
          {([
            {
              key: 'ALL' as const,
              title: '所有数据服务',
              description: `自动覆盖当前项目空间的全部 ${apis.length} 个 API，也包含后续新发布的 API。`,
            },
            {
              key: 'SELECTED' as const,
              title: '指定 API',
              description: '只允许调用明确授权的数据服务，适合第三方、合作方和最小权限场景。',
            },
          ]).map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setScope(item.key)}
              className={[
                'rounded-lg border border-solid px-4 py-3 text-left transition-colors',
                scope === item.key
                  ? 'border-[#161823] bg-[#f7f7f8]'
                  : 'border-[#eceef1] bg-white hover:bg-[#fafafa]',
              ].join(' ')}
            >
              <div className="flex items-center gap-2 text-[13px] font-medium text-[#161823]">
                <span className={[
                  'h-2 w-2 rounded-full',
                  scope === item.key ? 'bg-[#161823]' : 'bg-[#d0d5dd]',
                ].join(' ')} />
                {item.title}
              </div>
              <div className="mt-1.5 text-[11px] leading-5 text-[#8a8f98]">
                {item.description}
              </div>
            </button>
          ))}
        </div>

        {scope === 'SELECTED' ? (
          <div className="mt-5">
            <div className="mb-2 text-[12px] font-medium text-[#475467]">可访问 API</div>
            <Select<number[]>
              mode="multiple"
              value={apiIds}
              onChange={setApiIds}
              variant="filled"
              options={options}
              optionFilterProp="label"
              placeholder="选择允许调用的数据服务"
              className="w-full"
              maxTagCount="responsive"
            />
            <div className="mt-2 text-[10px] text-[#98a2b3]">
              当前选择 {apiIds.length} 个 API。未授权 API 即使使用有效 Key 也会被拒绝。
            </div>
          </div>
        ) : null}

        <div className="mt-5 flex items-center justify-between border-t border-solid border-[#f0f0f0] pt-4">
          <div className="flex items-center gap-1.5 text-[10px] text-[#98a2b3]">
            <CheckCircle2 size={12} />
            已进入调用方访问模型的 API 会保持鉴权兜底，移除授权不会意外变成公开访问。
          </div>
          <YakButton loading={saving} onClick={() => void save()}>
            保存权限
          </YakButton>
        </div>
      </section>
    </div>
  );
}
