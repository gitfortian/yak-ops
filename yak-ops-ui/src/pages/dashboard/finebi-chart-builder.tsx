import {
  calculatedFieldKey,
  isCalculatedFieldKey,
} from '@/components/analysis/calculated-field';
import {
  ANALYSIS_ENCODING_RULES,
  applyAnalysisEncoding,
  changeAnalysisEncodingType,
  rebindAnalysisEncoding,
  resolveAnalysisEncoding,
} from '@/components/analysis/encoding';
import type {
  AnalysisEncoding,
  AnalysisEncodingBinding,
  DatasetFieldRole,
} from '@/components/analysis/model';
import { Button, Collapse, Select } from 'antd';
import {
  Calculator,
  ChevronDown,
  SlidersHorizontal,
  X,
} from 'lucide-react';
import { useState, type DragEvent } from 'react';
import { ChartAnalysisConfig } from './config-analysis';
import { QueryControls } from './config-query';
import { readChartFieldDragPayload } from './chart-field-drag';
import { CHART_META, FIELD_DRAG_MIME, findDataset } from './helpers';
import type {
  AnalysisAsset,
  ChartType,
  DashboardInlineAnalysisSpec,
  DashboardWidget,
  PublishedDataset,
  SortDirection,
} from './model';

interface FieldOption {
  label: string;
  value: string;
  role: DatasetFieldRole;
}

export function FineBiChartBuilderPanel({
  widget,
  datasets,
  analyses,
  updateInlineAnalysis,
  detachAnalysis,
}: {
  widget: DashboardWidget;
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  updateInlineAnalysis: (patch: Partial<DashboardInlineAnalysisSpec>) => void;
  detachAnalysis: () => void;
}) {
  if (widget.analysisId) {
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    const dataset = analysis
      ? datasets.find((item) => item.id === analysis.datasetId)
      : undefined;

    return (
      <section className="flex w-[272px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white 2xl:w-[288px]">
        <div className="p-3.5">
          <div className="rounded-[7px] bg-[#f6f7f8] p-3">
            <div className="truncate text-[11px] font-semibold text-[#344054]">
              {analysis?.name ?? '历史图表'}
            </div>
            <div className="mt-1 truncate text-[9px] text-[#98a2b3]">
              {dataset?.name ?? '数据来源不可用'}
            </div>
          </div>
          <div className="mt-3 text-[10px] leading-5 text-[#667085]">
            共享图表为只读状态。复制为当前仪表盘图表后，可继续调整字段与图表类型。
          </div>
          <Button
            block
            size="small"
            className="mt-4 !h-8 !rounded-[7px]"
            disabled={!analysis}
            onClick={detachAnalysis}
          >
            复制为可编辑图表
          </Button>
        </div>
      </section>
    );
  }

  const spec = widget.inlineAnalysis;
  if (!spec) return null;
  const dataset = findDataset(datasets, spec.datasetId);
  if (!dataset) return null;

  const calculatedFields = spec.analysis?.calculatedFields ?? [];
  const fieldOptions: FieldOption[] = [
    ...dataset.fields.map((field) => ({
      label: field.label,
      value: field.key,
      role: field.role,
    })),
    ...calculatedFields.map((field) => ({
      label: field.name,
      value: calculatedFieldKey(field),
      role: 'metric' as const,
    })),
  ];
  const filterOptions = dataset.fields.map((field) => ({ label: field.label, value: field.key }));
  const selectedFields = new Set([
    ...spec.dimensions,
    ...spec.metrics.map((metric) => metric.field),
  ]);
  const sortOptions = dataset.fields
    .filter((field) => selectedFields.has(field.key))
    .map((field) => ({ label: field.label, value: field.key }));
  const encoding = resolveAnalysisEncoding(spec);
  const secondaryRules = ANALYSIS_ENCODING_RULES[spec.type]
    .filter((rule) => rule.channel !== 'category' && rule.channel !== 'value');

  const changeType = (type: ChartType) => {
    const changed = changeAnalysisEncodingType(spec, type);
    const next = rebindAnalysisEncoding(changed, dataset);
    updateInlineAnalysis({
      type,
      encoding: next.encoding,
      dimensions: next.dimensions,
      metrics: next.metrics,
      sort: undefined,
      style: { ...spec.style, version: 1 },
      analysis: spec.analysis,
      limit: type === 'table' ? 200 : 500,
    });
  };

  const changeEncoding = (nextEncoding: AnalysisEncoding) => {
    const next = applyAnalysisEncoding(spec, nextEncoding);
    const nextSort = spec.sort
      && !next.dimensions.includes(spec.sort.field)
      && !next.metrics.some((metric) => metric.field === spec.sort?.field)
      ? undefined
      : spec.sort;
    const currentTopN = spec.analysis?.topN;
    const topNMetricStillActive = currentTopN
      ? next.metrics.some((metric) => metric.field === currentTopN.metricField)
      : true;
    const topNFallback = next.metrics.find((metric) => !isCalculatedFieldKey(next, metric.field));
    const nextAnalysis = currentTopN && !topNMetricStillActive
      ? {
        ...spec.analysis,
        version: 1 as const,
        topN: topNFallback
          ? { ...currentTopN, metricField: topNFallback.field }
          : { ...currentTopN, enabled: false },
      }
      : spec.analysis;
    const hadColor = Boolean(spec.encoding?.color?.length);
    const hasColor = Boolean(next.encoding.color.length);
    const shouldRevealLegend = !hadColor
      && hasColor
      && ['bar', 'stackedBar', 'line', 'area', 'scatter'].includes(spec.type);

    updateInlineAnalysis({
      encoding: next.encoding,
      dimensions: next.dimensions,
      metrics: next.metrics,
      sort: nextSort,
      analysis: nextAnalysis,
      ...(shouldRevealLegend
        ? { style: { ...spec.style, showLegend: true, version: 1 as const } }
        : {}),
    });
  };

  return (
    <section className="flex w-[272px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white 2xl:w-[288px]">
      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-3.5">
        <div className="text-[10px] font-semibold text-[#667085]">图表类型</div>
        <div className="mt-2 grid grid-cols-5 gap-1">
          {(Object.keys(CHART_META) as ChartType[]).map((type) => {
            const active = spec.type === type;
            return (
              <button
                key={type}
                type="button"
                title={`${CHART_META[type].label} · ${CHART_META[type].description}`}
                onClick={() => changeType(type)}
                className={[
                  'flex min-w-0 flex-col items-center justify-center gap-1 rounded-[6px] border px-0.5 py-2 text-[8px] transition-colors',
                  active
                    ? 'border-[var(--yak-brand-color-border)] bg-[var(--yak-brand-color-soft)] font-medium text-[var(--yak-brand-color)]'
                    : 'border-transparent bg-white text-[#7f8792] hover:bg-[#f6f7f8] hover:text-[#475467]',
                ].join(' ')}
              >
                <span className="text-[13px]">{CHART_META[type].icon}</span>
                <span className="w-full truncate text-center">{CHART_META[type].label}</span>
              </button>
            );
          })}
        </div>

        {secondaryRules.length ? (
          <div className="mt-4 border-t border-[#eceef1] pt-3.5">
            <div className="mb-2 text-[10px] font-semibold text-[#667085]">图形属性</div>
            <div className="space-y-1.5">
              {secondaryRules.map((rule) => (
                <SecondaryEncodingSlot
                  key={rule.channel}
                  label={rule.label}
                  max={rule.max}
                  roles={rule.roles}
                  bindings={encoding[rule.channel]}
                  options={fieldOptions}
                  onChange={(bindings) => changeEncoding({
                    ...encoding,
                    [rule.channel]: bindings,
                  })}
                />
              ))}
            </div>
          </div>
        ) : null}

        <Collapse
          ghost
          className="chart-editor-more mt-4 border-t border-[#eceef1]"
          expandIconPosition="end"
          expandIcon={({ isActive }) => (
            <ChevronDown
              size={13}
              className={isActive ? 'rotate-180 text-[#667085]' : 'text-[#a0a6af]'}
            />
          )}
          items={[
            {
              key: 'analysis',
              label: (
                <span className="flex items-center gap-1.5 text-[10px] font-medium text-[#667085]">
                  <Calculator size={11} />
                  分析设置
                </span>
              ),
              children: (
                <ChartAnalysisConfig
                  spec={spec}
                  dataset={dataset}
                  onChange={(analysis) => updateInlineAnalysis({ analysis })}
                />
              ),
            },
            {
              key: 'query',
              label: (
                <span className="flex items-center gap-1.5 text-[10px] font-medium text-[#667085]">
                  <SlidersHorizontal size={11} />
                  排序与过滤
                </span>
              ),
              children: (
                <QueryControls
                  sortOptions={sortOptions}
                  filterOptions={filterOptions}
                  sortField={spec.sort?.field}
                  sortDirection={spec.sort?.direction ?? 'asc'}
                  filters={spec.filters}
                  onSortField={(field?: string) => updateInlineAnalysis({
                    sort: field
                      ? { field, direction: spec.sort?.direction ?? 'asc' }
                      : undefined,
                  })}
                  onSortDirection={(direction: SortDirection) =>
                    spec.sort && updateInlineAnalysis({ sort: { ...spec.sort, direction } })}
                  onFiltersChange={(filters) => updateInlineAnalysis({ filters })}
                />
              ),
            },
          ]}
        />
      </div>
    </section>
  );
}

function SecondaryEncodingSlot({
  label,
  max,
  roles,
  bindings,
  options,
  onChange,
}: {
  label: string;
  max: number;
  roles: DatasetFieldRole[];
  bindings: AnalysisEncodingBinding[];
  options: FieldOption[];
  onChange: (bindings: AnalysisEncodingBinding[]) => void;
}) {
  const [dragOver, setDragOver] = useState(false);
  const activeBindings = bindings.filter((binding) => roles.includes(binding.role)).slice(0, max);
  const fieldLabel = new Map(options.map((option) => [option.value, option.label]));
  const active = new Set(activeBindings.map((binding) => binding.field));
  const allBound = new Set(bindings.map((binding) => binding.field));
  const available = options.filter((option) => roles.includes(option.role) && (
    max === 1 ? !active.has(option.value) : !allBound.has(option.value)
  ));
  const full = activeBindings.length >= max;

  const addField = (field: string, role: DatasetFieldRole) => {
    const nextBinding: AnalysisEncodingBinding = {
      field,
      role,
      aggregation: role === 'metric' ? 'SUM' : undefined,
    };
    const existingIndex = bindings.findIndex((binding) => binding.field === field);
    const existing = existingIndex >= 0 ? bindings[existingIndex] : undefined;
    if (max === 1) {
      onChange([
        existing ?? nextBinding,
        ...bindings.filter((_, index) => index !== existingIndex),
      ]);
      return;
    }
    if (existing) {
      onChange([existing, ...bindings.filter((_, index) => index !== existingIndex)]);
      return;
    }
    if (activeBindings.length < max) onChange([...bindings, nextBinding]);
  };

  const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
    if (
      !event.dataTransfer.types.includes(FIELD_DRAG_MIME)
      && !event.dataTransfer.types.includes('text/plain')
    ) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
    setDragOver(true);
  };

  return (
    <div
      className={[
        'flex min-h-9 items-center gap-2 rounded-[6px] px-2 transition-colors',
        dragOver ? 'bg-[var(--yak-brand-color-soft)]' : 'bg-[#f7f8fa]',
      ].join(' ')}
      onDragEnter={handleDragOver}
      onDragOver={handleDragOver}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragOver(false);
      }}
      onDrop={(event) => {
        event.preventDefault();
        setDragOver(false);
        const payload = readChartFieldDragPayload(event);
        if (!payload || !roles.includes(payload.role)) return;
        if (!options.some((option) => option.value === payload.field && option.role === payload.role)) return;
        addField(payload.field, payload.role);
      }}
    >
      <span className="w-10 shrink-0 text-[9px] text-[#667085]">{label}</span>
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1 py-1">
        {activeBindings.map((binding) => (
          <span
            key={binding.field}
            className="flex h-6 max-w-[150px] items-center gap-1 rounded-[4px] bg-white px-1.5 text-[9px] text-[#475467]"
          >
            <span className="min-w-0 truncate">{fieldLabel.get(binding.field) ?? binding.field}</span>
            <button
              type="button"
              className="flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] text-[#a0a6af] hover:bg-[#f0f1f3] hover:text-[#667085]"
              onClick={() => onChange(bindings.filter((item) => item.field !== binding.field))}
              aria-label={`移除${fieldLabel.get(binding.field) ?? binding.field}`}
            >
              <X size={8} />
            </button>
          </span>
        ))}
        {(max === 1 || !full) ? (
          <Select
            showSearch
            size="small"
            variant="borderless"
            value={undefined}
            className="min-w-[106px] flex-1"
            optionFilterProp="label"
            placeholder={activeBindings.length ? '+ 字段' : '拖入一个字段'}
            options={available.map((option) => ({ label: option.label, value: option.value }))}
            onChange={(field) => {
              const option = available.find((item) => item.value === field);
              if (option) addField(option.value, option.role);
            }}
          />
        ) : null}
      </div>
    </div>
  );
}
