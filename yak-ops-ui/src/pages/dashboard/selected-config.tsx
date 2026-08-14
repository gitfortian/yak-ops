import type { AnalysisSpec } from '@/components/analysis/model';
import { Button } from 'antd';
import { PanelRight, X } from 'lucide-react';
import { ConfigPanel } from './config-panel';
import { findDataset } from './helpers';
import type {
  Aggregation,
  AnalysisAsset,
  DashboardWidget,
  FilterOperator,
  MetricBinding,
  PublishedDataset,
  SortDirection,
} from './model';

export function SelectedConfig({
  widget,
  datasets,
  analyses,
  updateWidget,
  updateInlineAnalysis,
  changeDataset,
  detachAnalysis,
  close,
}: {
  widget: DashboardWidget;
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  updateWidget: (patch: Partial<DashboardWidget>) => void;
  updateInlineAnalysis: (patch: Partial<AnalysisSpec>) => void;
  changeDataset: (datasetId: string) => void;
  detachAnalysis: () => void;
  close: () => void;
}) {
  if (widget.analysisId) {
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    const dataset = analysis
      ? datasets.find((item) => item.id === analysis.datasetId)
      : undefined;

    return (
      <aside className="flex w-[304px] shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
        <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#edf0f3] px-3">
          <div className="flex items-center gap-2 text-[12px] font-semibold text-[#344054]">
            <PanelRight size={14} />
            图表配置
          </div>
          <button
            type="button"
            onClick={close}
            className="flex h-7 w-7 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#f5f6f7]"
            aria-label="关闭图表配置"
          >
            <X size={14} />
          </button>
        </div>

        <div className="p-3">
          <div className="rounded-[5px] border border-[#e5e7eb] bg-[#fafbfc] p-3">
            <div className="truncate text-[11px] font-medium text-[#344054]">
              {analysis?.name ?? '历史图表'}
            </div>
            <div className="mt-0.5 text-[9px] text-[#98a2b3]">
              共享图表 · {dataset?.name ?? '数据来源不可用'}
            </div>
          </div>

          <div className="mt-3 text-[10px] leading-5 text-[#667085]">
            这是由历史共享资产创建的图表，当前保持只读引用。需要修改数据、查询或样式时，
            可先复制为当前仪表盘的独立图表。
          </div>

          {!analysis ? (
            <div className="mt-3 rounded-[4px] border border-[#fecdca] bg-[#fffbfa] px-2.5 py-2 text-[10px] text-[#b42318]">
              图表数据来源已删除或当前不可访问。
            </div>
          ) : null}

          <Button
            block
            size="small"
            type="primary"
            className="mt-4"
            disabled={!analysis}
            onClick={detachAnalysis}
          >
            复制为可编辑图表
          </Button>
        </div>
      </aside>
    );
  }

  const spec = widget.inlineAnalysis;
  if (!spec) return null;
  const dataset = findDataset(datasets, spec.datasetId);
  if (!dataset) return null;

  const dimensionOptions = dataset.fields
    .filter((field) => field.role === 'dimension')
    .map((field) => ({ label: field.label, value: field.key }));
  const metricOptions = dataset.fields
    .filter((field) => field.role === 'metric')
    .map((field) => ({ label: field.label, value: field.key }));
  const filterOptions = dataset.fields
    .map((field) => ({ label: field.label, value: field.key }));
  const selectedFields = new Set([
    ...spec.dimensions,
    ...spec.metrics.map((metric) => metric.field),
  ]);
  const sortOptions = dataset.fields
    .filter((field) => selectedFields.has(field.key))
    .map((field) => ({ label: field.label, value: field.key }));
  const metricLabels = Object.fromEntries(
    dataset.fields.map((field) => [field.key, field.label]),
  );
  const filter = spec.filters[0];

  return (
    <ConfigPanel
      spec={spec}
      title={widget.title || '未命名图表'}
      widget={widget}
      datasetOptions={datasets.map((item) => ({
        label: item.name,
        value: item.id,
      }))}
      dimensionOptions={dimensionOptions}
      metricOptions={metricOptions}
      sortOptions={sortOptions}
      filterOptions={filterOptions}
      metricLabels={metricLabels}
      onSpec={updateInlineAnalysis}
      onTitle={(title) => updateWidget({ title })}
      onLayout={updateWidget}
      onDataset={changeDataset}
      onDimensions={(dimensions) => {
        const nextSort = spec.sort
          && !dimensions.includes(spec.sort.field)
          && !spec.metrics.some((metric) => metric.field === spec.sort?.field)
          ? undefined
          : spec.sort;
        updateInlineAnalysis({ dimensions, sort: nextSort });
      }}
      onMetrics={(fields) => {
        const previous = new Map(
          spec.metrics.map((metric) => [metric.field, metric]),
        );
        const metrics: MetricBinding[] = fields.map(
          (field) => previous.get(field) ?? { field, aggregation: 'SUM' },
        );
        const nextSort = spec.sort
          && !spec.dimensions.includes(spec.sort.field)
          && !metrics.some((metric) => metric.field === spec.sort?.field)
          ? undefined
          : spec.sort;
        updateInlineAnalysis({ metrics, sort: nextSort });
      }}
      onAggregation={(field: string, aggregation: Aggregation) =>
        updateInlineAnalysis({
          metrics: spec.metrics.map((metric) =>
            metric.field === field ? { ...metric, aggregation } : metric),
        })}
      onSortField={(field?: string) =>
        updateInlineAnalysis({
          sort: field
            ? { field, direction: spec.sort?.direction ?? 'asc' }
            : undefined,
        })}
      onSortDirection={(direction: SortDirection) =>
        spec.sort
        && updateInlineAnalysis({ sort: { ...spec.sort, direction } })}
      onFilterField={(field?: string) =>
        updateInlineAnalysis({
          filters: field
            ? [{
              id: filter?.id ?? 'filter-main',
              field,
              operator: filter?.operator ?? 'eq',
              value: filter?.value ?? '',
            }]
            : [],
        })}
      onFilterOperator={(operator: FilterOperator) =>
        filter
        && updateInlineAnalysis({ filters: [{ ...filter, operator }] })}
      onFilterValue={(value) =>
        filter
        && updateInlineAnalysis({ filters: [{ ...filter, value }] })}
      onClose={close}
    />
  );
}
