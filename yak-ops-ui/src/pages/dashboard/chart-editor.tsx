import type { AnalysisSpec } from '@/components/analysis/model';
import { Button, Collapse, Input, Select, Switch } from 'antd';
import { ChevronDown, SlidersHorizontal, X } from 'lucide-react';
import { ConfigData } from './config-data';
import { MetricAggregations } from './config-metrics';
import { QueryControls } from './config-query';
import { CHART_META, findDataset } from './helpers';
import type {
  Aggregation,
  AnalysisAsset,
  ChartType,
  DashboardWidget,
  FilterOperator,
  MetricBinding,
  PublishedDataset,
  SortDirection,
} from './model';

export function ChartEditor({
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
      <aside className="flex w-[368px] shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
        <EditorHeader onClose={close} />
        <div className="p-4">
          <div className="rounded-[6px] border border-[#e5e7eb] bg-[#fafbfc] p-3">
            <div className="truncate text-[12px] font-medium text-[#344054]">
              {analysis?.name ?? '历史图表'}
            </div>
            <div className="mt-1 text-[10px] text-[#98a2b3]">
              历史共享图表 · {dataset?.name ?? '数据来源不可用'}
            </div>
          </div>
          <div className="mt-3 text-[11px] leading-5 text-[#667085]">
            这个图表来自旧版共享资产。复制为当前仪表盘图表后，即可使用新的 Chart Editor 编辑数据和样式。
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
  const filterOptions = dataset.fields.map((field) => ({
    label: field.label,
    value: field.key,
  }));
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

  const changeType = (type: ChartType) => {
    const dimensionLimit = type === 'table' ? 3 : 1;
    const metricLimit = ['bar', 'line', 'table'].includes(type) ? 3 : 1;
    updateInlineAnalysis({
      type,
      dimensions: type === 'metric' ? [] : spec.dimensions.slice(0, dimensionLimit),
      metrics: spec.metrics.slice(0, metricLimit),
      sort: undefined,
      style: {
        ...spec.style,
        showLegend: type === 'pie' ? true : spec.style.showLegend,
        smooth: type === 'line' ? spec.style.smooth : false,
        showGrid: type === 'line' || type === 'bar' ? spec.style.showGrid : false,
      },
      limit: type === 'table' ? 200 : 500,
    });
  };

  const onDimensions = (dimensions: string[]) => {
    const nextSort = spec.sort
      && !dimensions.includes(spec.sort.field)
      && !spec.metrics.some((metric) => metric.field === spec.sort?.field)
      ? undefined
      : spec.sort;
    updateInlineAnalysis({ dimensions, sort: nextSort });
  };

  const onMetrics = (fields: string[]) => {
    const previous = new Map(spec.metrics.map((metric) => [metric.field, metric]));
    const metrics: MetricBinding[] = fields.map(
      (field) => previous.get(field) ?? { field, aggregation: 'SUM' },
    );
    const nextSort = spec.sort
      && !spec.dimensions.includes(spec.sort.field)
      && !metrics.some((metric) => metric.field === spec.sort?.field)
      ? undefined
      : spec.sort;
    updateInlineAnalysis({ metrics, sort: nextSort });
  };

  const updateStyle = (patch: Partial<AnalysisSpec['style']>) =>
    updateInlineAnalysis({ style: { ...spec.style, ...patch } });

  return (
    <aside className="flex w-[368px] shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
      <EditorHeader onClose={close} />

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        <label className="mb-1 block text-[11px] font-medium text-[#667085]">
          图表标题
        </label>
        <Input
          size="small"
          value={widget.title || ''}
          placeholder="未命名图表"
          onChange={(event) => updateWidget({ title: event.target.value })}
        />

        <div className="mt-4 border-t border-[#edf0f3] pt-4">
          <div className="mb-1 text-[11px] font-medium text-[#667085]">数据集</div>
          <Select
            size="small"
            className="w-full"
            value={spec.datasetId}
            options={datasets.map((item) => ({ label: item.name, value: item.id }))}
            onChange={changeDataset}
          />
        </div>

        <div className="mt-4">
          <div className="mb-2 text-[11px] font-medium text-[#667085]">图表类型</div>
          <div className="grid grid-cols-5 gap-1.5">
            {(Object.keys(CHART_META) as ChartType[]).map((type) => {
              const active = spec.type === type;
              return (
                <button
                  key={type}
                  type="button"
                  onClick={() => changeType(type)}
                  className={[
                    'flex min-w-0 flex-col items-center justify-center gap-1 rounded-[5px] border px-1 py-2 text-[10px] transition-colors',
                    active
                      ? 'border-[#cfd4dc] bg-[#f5f6f7] font-medium text-[#161823]'
                      : 'border-[#edf0f3] bg-white text-[#667085] hover:bg-[#fafbfc]',
                  ].join(' ')}
                >
                  <span className={active ? 'text-[#344054]' : 'text-[#98a2b3]'}>
                    {CHART_META[type].icon}
                  </span>
                  <span className="truncate">{CHART_META[type].label}</span>
                </button>
              );
            })}
          </div>
        </div>

        <div className="mt-4 border-t border-[#edf0f3] pt-4">
          <ConfigData
            spec={spec}
            dimensionOptions={dimensionOptions}
            metricOptions={metricOptions}
            onDimensions={onDimensions}
            onMetrics={onMetrics}
          />
          <MetricAggregations
            metrics={spec.metrics}
            labels={metricLabels}
            onChange={(field: string, aggregation: Aggregation) =>
              updateInlineAnalysis({
                metrics: spec.metrics.map((metric) =>
                  metric.field === field ? { ...metric, aggregation } : metric),
              })}
          />
        </div>

        <Collapse
          ghost
          className="chart-editor-more mt-3 border-t border-[#edf0f3] pt-1"
          expandIconPosition="end"
          expandIcon={({ isActive }) => (
            <ChevronDown
              size={13}
              className={isActive ? 'rotate-180 text-[#667085]' : 'text-[#98a2b3]'}
            />
          )}
          items={[
            {
              key: 'advanced',
              label: (
                <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
                  <SlidersHorizontal size={12} />
                  更多设置
                </span>
              ),
              children: (
                <div className="pb-2">
                  <div className="space-y-3 text-[11px] text-[#475467]">
                    {spec.type !== 'metric' && spec.type !== 'table' ? (
                      <label className="flex items-center justify-between">
                        <span>显示图例</span>
                        <Switch
                          size="small"
                          checked={spec.style.showLegend}
                          onChange={(showLegend) => updateStyle({ showLegend })}
                        />
                      </label>
                    ) : null}
                    {spec.type !== 'metric' && spec.type !== 'table' ? (
                      <label className="flex items-center justify-between">
                        <span>显示数据标签</span>
                        <Switch
                          size="small"
                          checked={spec.style.showDataLabels}
                          onChange={(showDataLabels) => updateStyle({ showDataLabels })}
                        />
                      </label>
                    ) : null}
                    {spec.type === 'line' ? (
                      <label className="flex items-center justify-between">
                        <span>平滑曲线</span>
                        <Switch
                          size="small"
                          checked={spec.style.smooth}
                          onChange={(smooth) => updateStyle({ smooth })}
                        />
                      </label>
                    ) : null}
                    {spec.type === 'line' || spec.type === 'bar' ? (
                      <label className="flex items-center justify-between">
                        <span>显示网格线</span>
                        <Switch
                          size="small"
                          checked={spec.style.showGrid}
                          onChange={(showGrid) => updateStyle({ showGrid })}
                        />
                      </label>
                    ) : null}
                  </div>

                  <QueryControls
                    sortOptions={sortOptions}
                    filterOptions={filterOptions}
                    sortField={spec.sort?.field}
                    sortDirection={spec.sort?.direction ?? 'asc'}
                    filterField={filter?.field}
                    filterOperator={filter?.operator ?? 'eq'}
                    filterValue={filter?.value ?? ''}
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
                  />
                </div>
              ),
            },
          ]}
        />
      </div>

      <div className="shrink-0 border-t border-[#edf0f3] p-3">
        <Button block size="small" type="primary" onClick={close}>
          完成
        </Button>
      </div>
    </aside>
  );
}

function EditorHeader({ onClose }: { onClose: () => void }) {
  return (
    <div className="flex h-12 shrink-0 items-center justify-between border-b border-[#edf0f3] px-4">
      <div>
        <div className="text-[12px] font-semibold text-[#344054]">图表编辑</div>
        <div className="mt-0.5 text-[9px] text-[#98a2b3]">选择数据与字段，图表会即时更新</div>
      </div>
      <button
        type="button"
        onClick={onClose}
        className="flex h-7 w-7 items-center justify-center rounded-[4px] border-0 bg-transparent text-[#667085] hover:bg-[#f5f6f7]"
        aria-label="关闭图表编辑"
      >
        <X size={14} />
      </button>
    </div>
  );
}
