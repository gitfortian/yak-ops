import type { AnalysisSpec } from '@/components/analysis/model';
import { Button, Collapse, Input, Select, Switch } from 'antd';
import { ChevronDown, MousePointerClick, SlidersHorizontal, X } from 'lucide-react';
import { ConfigData } from './config-data';
import { MetricAggregations } from './config-metrics';
import { QueryControls } from './config-query';
import { CHART_META, findDataset } from './helpers';
import { DashboardInteractionEditor } from './interaction-editor';
import type {
  Aggregation,
  AnalysisAsset,
  ChartType,
  DashboardGlobalFilter,
  DashboardInlineAnalysisSpec,
  DashboardInteraction,
  DashboardWidget,
  FilterOperator,
  MetricBinding,
  PublishedDataset,
  SortDirection,
} from './model';
import { DashboardWidgetActionEditor } from './widget-action-editor';

export function ChartEditor({
  currentDashboardId,
  widget,
  datasets,
  analyses,
  globalFilters,
  interactions,
  updateWidget,
  updateInlineAnalysis,
  updateInteractions,
  changeDataset,
  detachAnalysis,
  close,
}: {
  currentDashboardId: string;
  widget: DashboardWidget;
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  updateWidget: (patch: Partial<DashboardWidget>) => void;
  updateInlineAnalysis: (patch: Partial<DashboardInlineAnalysisSpec>) => void;
  updateInteractions: (interactions: DashboardInteraction[]) => void;
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
      <aside className="flex w-[344px] shrink-0 flex-col border-l border-[#e8eaee] bg-white">
        <EditorHeader onClose={close} />
        <div className="p-4">
          <div className="rounded-[8px] border border-[#e7e9ed] bg-[#fafbfc] p-3.5">
            <div className="truncate text-[12px] font-semibold text-[#344054]">
              {analysis?.name ?? '历史图表'}
            </div>
            <div className="mt-1 text-[10px] text-[#98a2b3]">
              历史共享图表 · {dataset?.name ?? '数据来源不可用'}
            </div>
          </div>
          <div className="mt-3 text-[11px] leading-5 text-[#667085]">
            这个图表来自旧版共享资产。复制为当前仪表盘图表后，即可继续编辑数据、样式与交互。
          </div>
          {!analysis ? (
            <div className="mt-3 rounded-[6px] border border-[#fecdca] bg-[#fffbfa] px-2.5 py-2 text-[10px] text-[#b42318]">
              图表数据来源已删除或当前不可访问。
            </div>
          ) : null}
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
    <aside className="flex w-[344px] shrink-0 flex-col border-l border-[#e8eaee] bg-white">
      <EditorHeader onClose={close} />

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
        <div className="space-y-4">
          <div>
            <SectionLabel>图表标题</SectionLabel>
            <Input
              size="small"
              className="!h-8 !rounded-[7px]"
              value={widget.title || ''}
              placeholder="未命名图表"
              onChange={(event) => updateWidget({ title: event.target.value })}
            />
          </div>

          <div>
            <SectionLabel>数据集</SectionLabel>
            <Select
              size="small"
              className="w-full"
              value={spec.datasetId}
              options={datasets.map((item) => ({ label: item.name, value: item.id }))}
              onChange={changeDataset}
            />
          </div>
        </div>

        <div className="mt-5">
          <SectionLabel>图表类型</SectionLabel>
          <div className="grid grid-cols-5 gap-1.5">
            {(Object.keys(CHART_META) as ChartType[]).map((type) => {
              const active = spec.type === type;
              return (
                <button
                  key={type}
                  type="button"
                  onClick={() => changeType(type)}
                  className={[
                    'flex min-w-0 flex-col items-center justify-center gap-1.5 rounded-[7px] border px-1 py-2.5 text-[10px] transition-[background-color,border-color,color]',
                    active
                      ? 'border-[#cfd4dc] bg-[#f5f6f7] font-medium text-[#161823]'
                      : 'border-[#eceef1] bg-white text-[#7a818c] hover:border-[#dfe2e6] hover:bg-[#fafbfc]',
                  ].join(' ')}
                >
                  <span className={active ? 'text-[#344054]' : 'text-[#a0a6af]'}>
                    {CHART_META[type].icon}
                  </span>
                  <span className="truncate">{CHART_META[type].label}</span>
                </button>
              );
            })}
          </div>
        </div>

        <div className="mt-5 rounded-[9px] bg-[#f8f9fa] p-3.5">
          <div className="mb-3 text-[11px] font-semibold text-[#475467]">数据配置</div>
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
              key: 'interaction',
              label: (
                <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
                  <MousePointerClick size={12} />
                  交互设置
                </span>
              ),
              children: (
                <div className="space-y-4 pb-2">
                  <DashboardInteractionEditor
                    widget={widget}
                    spec={spec}
                    dataset={dataset}
                    filters={globalFilters}
                    interactions={interactions}
                    onChange={updateInteractions}
                  />
                  <div className="border-t border-[#eceef1] pt-4">
                    <DashboardWidgetActionEditor
                      currentDashboardId={currentDashboardId}
                      spec={spec}
                      dataset={dataset}
                      onChange={(dashboardBehavior) => updateInlineAnalysis({ dashboardBehavior })}
                    />
                  </div>
                </div>
              ),
            },
            {
              key: 'advanced',
              label: (
                <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
                  <SlidersHorizontal size={12} />
                  样式与查询
                </span>
              ),
              children: (
                <div className="pb-2">
                  <div className="space-y-3 text-[11px] text-[#475467]">
                    {spec.type !== 'metric' && spec.type !== 'table' ? (
                      <label className="flex items-center justify-between">
                        <span>显示图例</span>
                        <Switch size="small" checked={spec.style.showLegend} onChange={(showLegend) => updateStyle({ showLegend })} />
                      </label>
                    ) : null}
                    {spec.type !== 'metric' && spec.type !== 'table' ? (
                      <label className="flex items-center justify-between">
                        <span>显示数据标签</span>
                        <Switch size="small" checked={spec.style.showDataLabels} onChange={(showDataLabels) => updateStyle({ showDataLabels })} />
                      </label>
                    ) : null}
                    {spec.type === 'line' ? (
                      <label className="flex items-center justify-between">
                        <span>平滑曲线</span>
                        <Switch size="small" checked={spec.style.smooth} onChange={(smooth) => updateStyle({ smooth })} />
                      </label>
                    ) : null}
                    {spec.type === 'line' || spec.type === 'bar' ? (
                      <label className="flex items-center justify-between">
                        <span>显示网格线</span>
                        <Switch size="small" checked={spec.style.showGrid} onChange={(showGrid) => updateStyle({ showGrid })} />
                      </label>
                    ) : null}
                  </div>

                  <div className="mt-4 border-t border-[#eceef1] pt-4">
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
                </div>
              ),
            },
          ]}
        />
      </div>

      <div className="shrink-0 border-t border-[#eceef1] bg-[#fbfcfd] p-3">
        <Button block size="small" className="!h-8 !rounded-[7px]" onClick={close}>
          完成
        </Button>
      </div>
    </aside>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div className="mb-1.5 text-[10px] font-medium text-[#667085]">
      {children}
    </div>
  );
}

function EditorHeader({ onClose }: { onClose: () => void }) {
  return (
    <div className="flex h-14 shrink-0 items-center justify-between border-b border-[#eceef1] px-4">
      <div>
        <div className="text-[13px] font-semibold text-[#344054]">图表设置</div>
        <div className="mt-0.5 text-[9px] text-[#98a2b3]">数据、展示与交互配置</div>
      </div>
      <button
        type="button"
        onClick={onClose}
        className="flex h-7 w-7 items-center justify-center rounded-[6px] border-0 bg-transparent text-[#7a818c] hover:bg-[#f5f6f7] hover:text-[#344054]"
        aria-label="关闭图表编辑"
      >
        <X size={14} />
      </button>
    </div>
  );
}
