import { analysisEncodingFieldKeys } from '@/components/analysis/encoding';
import type { AnalysisSpec } from '@/components/analysis/model';
import { Input } from 'antd';
import { Database, GripVertical, Hash, Search, Type } from 'lucide-react';
import { useMemo, useState } from 'react';
import { writeChartFieldDragPayload } from './chart-field-drag';
import type { DatasetField, PublishedDataset } from './model';

const fieldTypeLabel: Record<DatasetField['dataType'], string> = {
  string: '文本',
  number: '数值',
  date: '日期',
  datetime: '日期时间',
  boolean: '布尔',
  unknown: '未知',
};

export function ChartFieldPanel({
  dataset,
  spec,
  editable,
}: {
  dataset?: PublishedDataset;
  spec?: AnalysisSpec;
  editable: boolean;
}) {
  const [keyword, setKeyword] = useState('');
  const normalizedKeyword = keyword.trim().toLowerCase();
  const fields = useMemo(() => {
    if (!dataset) return [];
    if (!normalizedKeyword) return dataset.fields;
    return dataset.fields.filter((field) => (
      field.label.toLowerCase().includes(normalizedKeyword)
      || field.key.toLowerCase().includes(normalizedKeyword)
      || field.physicalName.toLowerCase().includes(normalizedKeyword)
    ));
  }, [dataset, normalizedKeyword]);
  const dimensions = fields.filter((field) => field.role === 'dimension');
  const metrics = fields.filter((field) => field.role === 'metric');
  const encodedFields = useMemo(
    () => spec ? analysisEncodingFieldKeys(spec) : new Set<string>(),
    [spec],
  );

  return (
    <section className="flex w-[244px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white">
      <div className="flex h-14 shrink-0 items-center border-b border-[#eceef1] px-3.5">
        <div className="min-w-0">
          <div className="text-[13px] font-semibold text-[#344054]">数据字段</div>
          <div className="mt-0.5 flex items-center gap-1 text-[9px] text-[#98a2b3]">
            <Database size={9} className="shrink-0" />
            <span className="truncate">{dataset?.name ?? '数据来源不可用'}</span>
          </div>
        </div>
      </div>

      <div className="shrink-0 border-b border-[#f0f1f3] p-3">
        <Input
          allowClear
          size="small"
          value={keyword}
          prefix={<Search size={12} className="text-[#a0a6af]" />}
          placeholder="搜索字段"
          className="!h-8 !rounded-[7px]"
          onChange={(event) => setKeyword(event.target.value)}
        />
        <div className="mt-2 text-[9px] leading-4 text-[#98a2b3]">
          {editable ? '拖动字段到右侧可视化编码槽位' : '共享图表复制为可编辑图表后可拖拽配置'}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2.5 py-3">
        {!dataset ? (
          <div className="px-2 py-6 text-center text-[10px] text-[#98a2b3]">暂无可用字段</div>
        ) : (
          <div className="space-y-4">
            <FieldGroup
              title="维度"
              fields={dimensions}
              encodedFields={encodedFields}
              editable={editable}
            />
            <FieldGroup
              title="指标"
              fields={metrics}
              encodedFields={encodedFields}
              editable={editable}
            />
          </div>
        )}
      </div>
    </section>
  );
}

function FieldGroup({
  title,
  fields,
  encodedFields,
  editable,
}: {
  title: string;
  fields: DatasetField[];
  encodedFields: Set<string>;
  editable: boolean;
}) {
  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between px-1.5">
        <span className="text-[10px] font-semibold text-[#667085]">{title}</span>
        <span className="text-[9px] tabular-nums text-[#b0b5bd]">{fields.length}</span>
      </div>
      <div className="space-y-0.5">
        {fields.map((field) => {
          const selected = encodedFields.has(field.key);
          return (
            <div
              key={field.key}
              draggable={editable}
              title={editable ? `${field.label} · 拖动到图表编码槽位` : field.label}
              className={[
                'group flex h-8 items-center gap-1.5 rounded-[6px] px-1.5 text-[10px] transition-colors',
                editable ? 'cursor-grab active:cursor-grabbing' : 'cursor-default',
                selected
                  ? 'bg-[#f4f5f7] font-medium text-[#344054]'
                  : 'text-[#475467] hover:bg-[#f7f8fa]',
              ].join(' ')}
              onDragStart={(event) => {
                if (!editable) return;
                writeChartFieldDragPayload(event, { field: field.key, role: field.role });
              }}
            >
              <GripVertical
                size={12}
                className={editable ? 'shrink-0 text-[#c2c6cc] group-hover:text-[#8b929c]' : 'shrink-0 text-[#e0e2e6]'}
              />
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-[5px] bg-[#f2f4f7] text-[#7a818c]">
                {field.dataType === 'number' ? <Hash size={11} /> : <Type size={11} />}
              </span>
              <span className="min-w-0 flex-1 truncate">{field.label}</span>
              <span className="shrink-0 text-[8px] text-[#b0b5bd]">{fieldTypeLabel[field.dataType]}</span>
            </div>
          );
        })}
        {!fields.length ? (
          <div className="px-1.5 py-2 text-[9px] text-[#b0b5bd]">没有匹配字段</div>
        ) : null}
      </div>
    </div>
  );
}
