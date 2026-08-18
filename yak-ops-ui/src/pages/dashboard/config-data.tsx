import {
  ANALYSIS_ENCODING_RULES,
  resolveAnalysisEncoding,
  type AnalysisEncodingSlotRule,
} from '@/components/analysis/encoding';
import type {
  AnalysisEncoding,
  AnalysisEncodingBinding,
  AnalysisSpec,
  DatasetFieldRole,
} from '@/components/analysis/model';
import { Select } from 'antd';
import { Plus, X } from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { readChartFieldDragPayload } from './chart-field-drag';
import { AGGREGATION_OPTIONS, FIELD_DRAG_MIME } from './helpers';

interface FieldOption {
  label: string;
  value: string;
  role: DatasetFieldRole;
}

interface BoundField {
  field: string;
  label: string;
  suffix?: string;
}

export function ConfigData({
  spec,
  fieldOptions,
  onEncodingChange,
}: {
  spec: AnalysisSpec;
  fieldOptions: FieldOption[];
  onEncodingChange: (encoding: AnalysisEncoding) => void;
}) {
  const encoding = resolveAnalysisEncoding(spec);
  const fieldLabel = new Map(fieldOptions.map((option) => [option.value, option.label]));
  const aggregationLabel = new Map(AGGREGATION_OPTIONS.map((option) => [option.value, option.label]));

  return (
    <div className="space-y-3">
      {ANALYSIS_ENCODING_RULES[spec.type].map((rule) => {
        const bindings = encoding[rule.channel];
        const values: BoundField[] = bindings.map((binding) => ({
          field: binding.field,
          label: fieldLabel.get(binding.field) ?? binding.field,
          suffix: binding.role === 'metric'
            ? aggregationLabel.get(binding.aggregation ?? 'SUM') ?? binding.aggregation ?? 'SUM'
            : undefined,
        }));
        const options = fieldOptions.filter((option) => rule.roles.includes(option.role));

        return (
          <EncodingDropZone
            key={rule.channel}
            rule={rule}
            values={values}
            options={options}
            onAdd={(field, role) => {
              const nextBinding: AnalysisEncodingBinding = {
                field,
                role,
                aggregation: role === 'metric' ? 'SUM' : undefined,
              };
              const current = encoding[rule.channel];
              if (current.some((binding) => binding.field === field)) return;
              const nextChannel = rule.max === 1
                ? [nextBinding]
                : [...current, nextBinding].slice(0, rule.max);
              onEncodingChange({
                ...encoding,
                [rule.channel]: nextChannel,
              });
            }}
            onRemove={(field) => onEncodingChange({
              ...encoding,
              [rule.channel]: bindings.filter((binding) => binding.field !== field),
            })}
          />
        );
      })}
    </div>
  );
}

function EncodingDropZone({
  rule,
  values,
  options,
  onAdd,
  onRemove,
}: {
  rule: AnalysisEncodingSlotRule;
  values: BoundField[];
  options: FieldOption[];
  onAdd: (field: string, role: DatasetFieldRole) => void;
  onRemove: (field: string) => void;
}) {
  const [dragOver, setDragOver] = useState(false);
  const availableOptions = options.filter((option) => !values.some((item) => item.field === option.value));
  const full = values.length >= rule.max;

  const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
    if (
      !event.dataTransfer.types.includes(FIELD_DRAG_MIME)
      && !event.dataTransfer.types.includes('text/plain')
    ) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
    setDragOver(true);
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragOver(false);
    const payload = readChartFieldDragPayload(event);
    if (!payload || !rule.roles.includes(payload.role)) return;
    onAdd(payload.field, payload.role);
  };

  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between gap-3 text-[10px] text-[#667085]">
        <div className="min-w-0">
          <span>{rule.label}</span>
          {rule.min > 0 ? <span className="ml-1 text-[8px] text-[#b42318]">必填</span> : null}
        </div>
        <span className="shrink-0 text-[9px] text-[#a0a6af]">{values.length}/{rule.max}</span>
      </div>
      <div
        className={[
          'rounded-[8px] border border-dashed p-2 transition-[border-color,background-color]',
          dragOver
            ? 'border-[var(--yak-brand-color)] bg-[var(--yak-brand-color-soft)]'
            : 'border-[#dfe3e8] bg-white',
        ].join(' ')}
        onDragEnter={handleDragOver}
        onDragOver={handleDragOver}
        onDragLeave={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragOver(false);
        }}
        onDrop={handleDrop}
      >
        <div className="space-y-1.5">
          {values.map((value) => (
            <div
              key={value.field}
              className="flex min-h-7 items-center gap-1.5 rounded-[6px] border border-[#e4e7ec] bg-[#f8f9fa] px-2 text-[10px] text-[#344054]"
            >
              <span className="min-w-0 flex-1 truncate font-medium">{value.label}</span>
              {value.suffix ? (
                <span className="shrink-0 text-[8px] text-[#98a2b3]">{value.suffix}</span>
              ) : null}
              <button
                type="button"
                className="flex h-5 w-5 shrink-0 items-center justify-center rounded-[4px] text-[#a0a6af] hover:bg-[#eceef1] hover:text-[#667085]"
                aria-label={`移除${value.label}`}
                onClick={() => onRemove(value.field)}
              >
                <X size={10} />
              </button>
            </div>
          ))}
        </div>

        {!values.length ? (
          <div className="flex h-9 items-center justify-center gap-1 text-[9px] text-[#a0a6af]">
            <Plus size={10} />
            拖入{rule.label}字段
          </div>
        ) : null}

        <Select
          showSearch
          size="small"
          value={undefined}
          disabled={full || !availableOptions.length}
          className="mt-2 w-full"
          options={availableOptions.map((option) => ({ label: option.label, value: option.value }))}
          optionFilterProp="label"
          placeholder={full ? `最多 ${rule.max} 个字段` : '+ 点击选择字段'}
          onChange={(field) => {
            const option = availableOptions.find((item) => item.value === field);
            if (option) onAdd(option.value, option.role);
          }}
        />
        {rule.hint ? (
          <div className="mt-1.5 text-[8px] leading-4 text-[#a0a6af]">{rule.hint}</div>
        ) : null}
      </div>
    </div>
  );
}
