import type { AnalysisSpec } from '@/components/analysis/model';
import { Select, Tabs } from 'antd';
import { PanelRight, X } from 'lucide-react';
import { ConfigData } from './config-data';
import { MetricAggregations } from './config-metrics';
import { QueryControls } from './config-query';
import { CHART_META } from './helpers';
import type { Aggregation, ChartType, DashboardWidget, FilterOperator, SortDirection } from './model';
import { StyleConfigPanel } from './style-config';

export function ConfigPanel({
  spec,
  title,
  widget,
  datasetOptions,
  dimensionOptions,
  metricOptions,
  sortOptions,
  filterOptions,
  metricLabels,
  onSpec,
  onTitle,
  onLayout,
  onDataset,
  onDimensions,
  onMetrics,
  onAggregation,
  onSortField,
  onSortDirection,
  onFilterField,
  onFilterOperator,
  onFilterValue,
  onClose,
}: {
  spec: AnalysisSpec;
  title: string;
  widget: DashboardWidget;
  datasetOptions: Array<{ label: string; value: string }>;
  dimensionOptions: Array<{ label: string; value: string }>;
  metricOptions: Array<{ label: string; value: string }>;
  sortOptions: Array<{ label: string; value: string }>;
  filterOptions: Array<{ label: string; value: string }>;
  metricLabels: Record<string, string>;
  onSpec: (patch: Partial<AnalysisSpec>) => void;
  onTitle: (title: string) => void;
  onLayout: (patch: Partial<DashboardWidget>) => void;
  onDataset: (value: string) => void;
  onDimensions: (value: string[]) => void;
  onMetrics: (value: string[]) => void;
  onAggregation: (field: string, aggregation: Aggregation) => void;
  onSortField: (field?: string) => void;
  onSortDirection: (direction: SortDirection) => void;
  onFilterField: (field?: string) => void;
  onFilterOperator: (operator: FilterOperator) => void;
  onFilterValue: (value: string) => void;
  onClose: () => void;
}) {
  const filter = spec.filters[0];
  return (
    <aside className="flex w-[320px] shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#edf0f3] px-3">
        <div className="flex items-center gap-2 text-[12px] font-semibold text-[#344054]">
          <PanelRight size={14} />图表配置
        </div>
        <button type="button" onClick={onClose} className="flex h-7 w-7 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#f5f6f7]">
          <X size={14} />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        <Tabs
          size="small"
          defaultActiveKey="data"
          items={[
            {
              key: 'data',
              label: '数据',
              children: (
                <div className="p-3">
                  <div className="mb-3 text-[11px] text-[#667085]">数据集</div>
                  <Select size="small" className="mb-4 w-full" value={spec.datasetId} options={datasetOptions} onChange={onDataset} />
                  <div className="mb-1 text-[11px] text-[#667085]">图表</div>
                  <Select
                    size="small"
                    className="mb-4 w-full"
                    value={spec.type}
                    options={(Object.keys(CHART_META) as ChartType[]).map((type) => ({ label: CHART_META[type].label, value: type }))}
                    onChange={(type: ChartType) => onSpec({
                      type,
                      dimensions: type === 'metric' ? [] : spec.dimensions.slice(0, type === 'pie' ? 1 : undefined),
                      metrics: type === 'metric' || type === 'pie' ? spec.metrics.slice(0, 1) : spec.metrics,
                      sort: undefined,
                      style: {
                        ...spec.style,
                        showLegend: type === 'pie' ? spec.style.showLegend : spec.style.showLegend,
                        smooth: type === 'line' ? spec.style.smooth : false,
                        showGrid: type === 'line' || type === 'bar' ? spec.style.showGrid : false,
                      },
                      limit: type === 'table' ? 200 : 500,
                    })}
                  />
                  <ConfigData spec={spec} dimensionOptions={dimensionOptions} metricOptions={metricOptions} onDimensions={onDimensions} onMetrics={onMetrics} />
                  <MetricAggregations metrics={spec.metrics} labels={metricLabels} onChange={onAggregation} />
                  <QueryControls
                    sortOptions={sortOptions}
                    filterOptions={filterOptions}
                    sortField={spec.sort?.field}
                    sortDirection={spec.sort?.direction ?? 'asc'}
                    filterField={filter?.field}
                    filterOperator={filter?.operator ?? 'eq'}
                    filterValue={filter?.value ?? ''}
                    onSortField={onSortField}
                    onSortDirection={onSortDirection}
                    onFilterField={onFilterField}
                    onFilterOperator={onFilterOperator}
                    onFilterValue={onFilterValue}
                  />
                </div>
              ),
            },
            {
              key: 'style',
              label: '样式',
              children: (
                <StyleConfigPanel
                  spec={spec}
                  title={title}
                  widget={widget}
                  onSpec={onSpec}
                  onTitle={onTitle}
                  onLayout={onLayout}
                />
              ),
            },
          ]}
          className="dashboard-config-tabs"
        />
      </div>
    </aside>
  );
}
