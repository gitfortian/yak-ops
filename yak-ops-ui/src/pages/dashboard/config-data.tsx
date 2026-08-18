import type { AnalysisSpec } from '@/components/analysis/model';
import { Select } from 'antd';
import { Plus, X } from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { readChartFieldDragPayload } from './chart-field-drag';
import { AGGREGATION_OPTIONS, FIELD_DRAG_MIME } from './helpers';

interface FieldOption {
  label: string;
  value: string;
}

interface BoundField {
  field: string;
  label: string;
  suffix?: string;
}

export function ConfigData({
  spec,
  dimensionOptions,
  metricOptions,
  onDimensions,
  onMetrics,
}: {
  spec: AnalysisSpec;
  dimensionOptions: FieldOption[];
  metricOptions: FieldOption[];
  onDimensions: (value: string[]) => void;
  onMetrics: (value: string[]) => void;
}) {
  const dimensionLimit = spec.type === 'table' ? 3 : 1;
  const metricLimit = ['bar', 'line', 'table'].includes(spec.type) ? 3 : 1;
  const dimensionLabel = new Map(dimensionOptions.map((option) => [option.value, option.label]));
  const metricLabel = new Map(metricOptions.map((option) => [option.value, option.label]));
  const aggregationLabel = new Map(AGGREGATION_OPTIONS.map((option) => [option.value, option.label]));

  const addDimension = (field: string) => {
    if (!dimensionOptions.some((option) => option.value === field)) return;
    if (spec.dimensions.includes(field)) return;
    if (dimensionLimit === 1) {
      onDimensions([field]);
      return;
    }
    if (spec.dimensions.length >= dimensionLimit) return;
    onDimensions([...spec.dimensions, field]);
  };

  const addMetric = (field: string) => {
    if (!metricOptions.some((option) => option.value === field)) return;
    const fields = spec.metrics.map((metric) => metric.field);
    if (fields.includes(field)) return;
    if (metricLimit === 1) {
      onMetrics([field]);
      return;
    }
    if (fields.length >= metricLimit) return;
    onMetrics([...fields, field]);
  };

  const dimensions: BoundField[] = spec.dimensions.map((field) => ({
    field,
    label: dimensionLabel.get(field) ?? field,
  }));
  const metrics: BoundField[] = spec.metrics.map((metric) => ({
    field: metric.field,
    label: metricLabel.get(metric.field) ?? metric.field,
    suffix: aggregationLabel.get(metric.aggregation) ?? metric.aggregation,
  }));

  return (
    <div className="space-y-3">
      {spec.type !== 'metric' ? (
        <BindingDropZone
          role="dimension"
          label="维度"
          emptyText="拖入维度字段"
          values={dimensions}
          options={dimensionOptions}
          limit={dimensionLimit}
          onAdd={addDimension}
          onRemove={(field) => onDimensions(spec.dimensions.filter((item) => item !== field))}
        />
      ) : null}
      <BindingDropZone
        role="metric"
        label="指标"
        emptyText="拖入指标字段"
        values={metrics}
        options={metricOptions}
        limit={metricLimit}
        onAdd={addMetric}
        onRemove={(field) => onMetrics(spec.metrics
          .map((metric) => metric.field)
          .filter((item) => item !== field))}
      />
    </div>
  );
}

function BindingDropZone({
  role,
  label,
  emptyText,
  values,
  options,
  limit,
  onAdd,
  onRemove,
}: {
  role: 'dimension' | 'metric';
  label: string;
  emptyText: string;
  values: BoundField[];
  options: FieldOption[];
  limit: number;
  onAdd: (field: string) => void;
  onRemove: (field: string) => void;
}) {
  const [dragOver, setDragOver] = useState(false);
  const availableOptions = options.filter((option) => !values.some((item) => item.field === option.value));
  const full = values.length >= limit;

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
    if (!payload || payload.role !== role) return;
    onAdd(payload.field);
  };

  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between text-[10px] text-[#667085]">
        <span>{label}</span>
        <span className="text-[9px] text-[#a0a6af]">{values.length}/{limit}</span>
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
            {emptyText}
          </div>
        ) : null}

        <Select
          showSearch
          size="small"
          value={undefined}
          disabled={full || !availableOptions.length}
          className="mt-2 w-full"
          options={availableOptions}
          optionFilterProp="label"
          placeholder={full ? `最多 ${limit} 个字段` : '+ 点击选择字段'}
          onChange={onAdd}
        />
      </div>
    </div>
  );
}
