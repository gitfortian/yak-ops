import { Select, Tabs } from 'antd';
import { PanelRight, X } from 'lucide-react';
import { ConfigData } from './config-data';
import { MetricAggregations } from './config-metrics';
import { QueryControls } from './config-query';
import { CHART_META } from './helpers';
import type { Aggregation, ChartType, DashboardWidget, FilterOperator, SortDirection } from './model';
import { StyleConfigPanel } from './style-config';

export function ConfigPanel({
  widget,
  datasetOptions,
  dimensionOptions,
  metricOptions,
  sortOptions,
  filterOptions,
  metricLabels,
  onWidget,
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
  widget: DashboardWidget;
  datasetOptions: Array<{ label: string; value: string }>;
  dimensionOptions: Array<{ label: string; value: string }>;
  metricOptions: Array<{ label: string; value: string }>;
  sortOptions: Array<{ label: string; value: string }>;
  filterOptions: Array<{ label: string; value: string }>;
  metricLabels: Record<string, string>;
  onWidget: (patch: Partial<DashboardWidget>) => void;
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
  const filter = widget.filters[0];
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
                  <Select size="small" className="mb-4 w-full" value={widget.datasetId} options={datasetOptions} onChange={onDataset} />
                  <div className="mb-1 text-[11px] text-[#667085]">图表</div>
                  <Select
                    size="small"
                    className="mb-4 w-full"
                    value={widget.type}
                    options={(Object.keys(CHART_META) as ChartType[]).map((type) => ({ label: CHART_META[type].label, value: type }))}
                    onChange={(type: ChartType) => onWidget({ type, dimensions: type === 'metric' ? [] : widget.dimensions })}
                  />
                  <ConfigData widget={widget} dimensionOptions={dimensionOptions} metricOptions={metricOptions} onDimensions={onDimensions} onMetrics={onMetrics} />
                  <MetricAggregations metrics={widget.metrics} labels={metricLabels} onChange={onAggregation} />
                  <QueryControls
                    sortOptions={sortOptions}
                    filterOptions={filterOptions}
                    sortField={widget.sort?.field}
                    sortDirection={widget.sort?.direction ?? 'asc'}
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
            { key: 'style', label: '样式', children: <StyleConfigPanel widget={widget} onChange={onWidget} /> },
          ]}
          className="dashboard-config-tabs"
        />
      </div>
    </aside>
  );
}
