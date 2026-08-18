import {
  applyAnalysisEncoding,
  changeAnalysisEncodingType,
  updateEncodingMetricAggregation,
} from '@/components/analysis/encoding';
import type { AnalysisEncoding, AnalysisSpec } from '@/components/analysis/model';
import { Button, Collapse, Input, Select } from 'antd';
import { Calculator, ChevronDown, MousePointerClick, Palette, SlidersHorizontal, X } from 'lucide-react';
import { ChartAnalysisConfig } from './config-analysis';
import { ConfigData } from './config-data';
import { MetricAggregations } from './config-metrics';
import { QueryControls } from './config-query';
import { ChartStyleConfig } from './config-style';
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
  PublishedDataset,
  SortDirection,
} from './model';
import { DashboardWidgetActionEditor } from './widget-action-editor';

export function ChartSheetConfigPanel({
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
  onDone,
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
  onDone: () => void;
}) {
  if (widget.analysisId) {
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    const dataset = analysis
      ? datasets.find((item) => item.id === analysis.datasetId)
      : undefined;

    return (
      <section className="chart-sheet-config-panel flex w-[360px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white">
        <ConfigPanelHeader onDone={onDone} />
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
      </section>
    );
  }

  const spec = widget.inlineAnalysis;
  if (!spec) return null;
  const dataset = findDataset(datasets, spec.datasetId);
  if (!dataset) return null;

  const fieldOptions = dataset.fields.map((field) => ({
    label: field.label,
    value: field.key,
    role: field.role,
  }));
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

  const changeType = (type: ChartType) => {
    const next = changeAnalysisEncodingType(spec, type);
    updateInlineAnalysis({
      type,
      encoding: next.encoding,
      dimensions: next.dimensions,
      metrics: next.metrics,
      sort: undefined,
      // Keep style and analysis values non-destructive across chart type switches. Each
      // renderer consumes only the options relevant to the currently active chart.
      style: { ...spec.style, version: 1 },
      analysis: spec.analysis,
      limit: type === 'table' ? 200 : 500,
    });
  };

  const changeEncoding = (encoding: AnalysisEncoding) => {
    const next = applyAnalysisEncoding(spec, encoding);
    const nextSort = spec.sort
      && !next.dimensions.includes(spec.sort.field)
      && !next.metrics.some((metric) => metric.field === spec.sort?.field)
      ? undefined
      : spec.sort;
    const currentTopN = spec.analysis?.topN;
    const topNMetricStillActive = currentTopN
      ? next.metrics.some((metric) => metric.field === currentTopN.metricField)
      : true;
    const nextAnalysis = currentTopN && !topNMetricStillActive
      ? {
        ...spec.analysis,
        version: 1 as const,
        topN: next.metrics[0]
          ? { ...currentTopN, metricField: next.metrics[0].field }
          : { ...currentTopN, enabled: false },
      }
      : spec.analysis;
    const hadColor = Boolean(spec.encoding?.color?.length);
    const hasColor = Boolean(next.encoding.color.length);
    const shouldRevealLegend = !hadColor
      && hasColor
      && (spec.type === 'bar' || spec.type === 'line');
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

  const updateStyle = (patch: Partial<AnalysisSpec['style']>) =>
    updateInlineAnalysis({ style: { ...spec.style, ...patch, version: 1 } });

  return (
    <section className="chart-sheet-config-panel flex w-[360px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white">
      <ConfigPanelHeader onDone={onDone} />

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
          <div className="mb-3 flex items-center justify-between gap-3">
            <div className="text-[11px] font-semibold text-[#475467]">可视化编码</div>
            <span className="rounded-[4px] border border-[#e1e4e8] bg-white px-1.5 py-0.5 text-[8px] text-[#98a2b3]">
              Encoding v1
            </span>
          </div>
          <ConfigData
            spec={spec}
            fieldOptions={fieldOptions}
            onEncodingChange={changeEncoding}
          />
          <MetricAggregations
            metrics={spec.metrics}
            labels={metricLabels}
            onChange={(field: string, aggregation: Aggregation) => {
              const next = updateEncodingMetricAggregation(spec, field, aggregation);
              updateInlineAnalysis({
                encoding: next.encoding,
                metrics: next.metrics,
              });
            }}
          />
        </div>

        <Collapse
          ghost
          className="chart-editor-more mt-4 border-t border-[#eceef1]"
          expandIconPosition="end"
          defaultActiveKey={['analysis']}
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
                <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
                  <Calculator size={12} />
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
              key: 'style',
              label: (
                <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
                  <Palette size={12} />
                  样式设置
                </span>
              ),
              children: (
                <ChartStyleConfig spec={spec} onChange={updateStyle} />
              ),
            },
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
              key: 'query',
              label: (
                <span className="flex items-center gap-1.5 text-[11px] font-medium text-[#667085]">
                  <SlidersHorizontal size={12} />
                  查询设置
                </span>
              ),
              children: (
                <div className="pb-2">
                  <QueryControls
                    sortOptions={sortOptions}
                    filterOptions={filterOptions}
                    sortField={spec.sort?.field}
                    sortDirection={spec.sort?.direction ?? 'asc'}
                    filters={spec.filters}
                    onSortField={(field?: string) =>
                      updateInlineAnalysis({
                        sort: field
                          ? { field, direction: spec.sort?.direction ?? 'asc' }
                          : undefined,
                      })}
                    onSortDirection={(direction: SortDirection) =>
                      spec.sort
                      && updateInlineAnalysis({ sort: { ...spec.sort, direction } })}
                    onFiltersChange={(filters) => updateInlineAnalysis({ filters })}
                  />
                </div>
              ),
            },
          ]}
        />
      </div>

      <div className="shrink-0 border-t border-[#eceef1] bg-[#fbfcfd] p-3">
        <Button block size="small" className="!h-8 !rounded-[7px]" onClick={onDone}>
          完成
        </Button>
      </div>
    </section>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div className="mb-1.5 text-[10px] font-medium text-[#667085]">
      {children}
    </div>
  );
}

function ConfigPanelHeader({ onDone }: { onDone: () => void }) {
  return (
    <div className="flex h-14 shrink-0 items-center justify-between border-b border-[#eceef1] px-4">
      <div>
        <div className="text-[13px] font-semibold text-[#344054]">图表配置</div>
        <div className="mt-0.5 text-[9px] text-[#98a2b3]">字段编码、分析、样式与交互配置</div>
      </div>
      <button
        type="button"
        onClick={onDone}
        className="flex h-7 w-7 items-center justify-center rounded-[6px] border-0 bg-transparent text-[#7a818c] hover:bg-[#f5f6f7] hover:text-[#344054]"
        aria-label="返回仪表盘 Sheet"
      >
        <X size={14} />
      </button>
    </div>
  );
}
