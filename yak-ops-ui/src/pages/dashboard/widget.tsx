import { Empty, Tooltip } from 'antd';
import * as echarts from 'echarts';
import { Copy, GripVertical, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useRef } from 'react';
import { aggregateWidgetRows, formatMetricValue, getField, metricDisplayName } from './data';
import { findDataset } from './helpers';
import type { DashboardWidget, PublishedDataset } from './model';

const chartOptionFor = (widget: DashboardWidget, dataset: PublishedDataset) => {
  const rows = aggregateWidgetRows(dataset, widget);
  const firstDimension = widget.dimensions[0];
  const dimension = getField(dataset, firstDimension);
  const metrics = widget.metrics;
  const axisText = '#667085';
  const axisLine = '#d8dde6';
  const splitLine = '#eef1f5';

  if (!firstDimension || !metrics.length) return undefined;

  if (widget.type === 'pie') {
    const metric = metrics[0];
    return {
      tooltip: { trigger: 'item' },
      legend: widget.style.showLegend
        ? { orient: 'vertical', right: 8, top: 'middle', textStyle: { color: axisText, fontSize: 11 } }
        : { show: false },
      series: [{
        name: metricDisplayName(dataset, metric),
        type: 'pie',
        radius: ['42%', '70%'],
        center: [widget.style.showLegend ? '38%' : '50%', '52%'],
        label: { show: widget.style.showDataLabels, formatter: '{b} {d}%' },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        data: rows.map((row) => ({ name: row.label, value: row.values[metric.field] ?? 0 })),
      }],
    };
  }

  if (widget.type === 'bar' || widget.type === 'line') {
    const isLine = widget.type === 'line';
    return {
      grid: { left: 22, right: 16, top: widget.style.showLegend ? 30 : 14, bottom: 22, containLabel: true },
      tooltip: { trigger: 'axis' },
      legend: widget.style.showLegend
        ? { top: 0, right: 4, textStyle: { color: axisText, fontSize: 11 } }
        : { show: false },
      xAxis: {
        type: 'category',
        boundaryGap: !isLine,
        name: dimension?.label,
        nameTextStyle: { color: '#98a2b3', fontSize: 10 },
        data: rows.map((row) => row.label),
        axisLine: { lineStyle: { color: axisLine } },
        axisTick: { show: false },
        axisLabel: { color: axisText, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        splitLine: { show: widget.style.showGrid, lineStyle: { color: splitLine } },
        axisLabel: { color: axisText, fontSize: 11 },
      },
      series: metrics.map((metric) => ({
        name: metricDisplayName(dataset, metric),
        type: widget.type,
        smooth: isLine && widget.style.smooth,
        symbolSize: 5,
        barMaxWidth: 34,
        label: { show: widget.style.showDataLabels, position: 'top' },
        data: rows.map((row) => row.values[metric.field] ?? 0),
      })),
    };
  }

  return undefined;
};

function EChartWidget({ widget, dataset }: { widget: DashboardWidget; dataset: PublishedDataset }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts>();
  const option = useMemo(() => chartOptionFor(widget, dataset), [widget, dataset]);

  useEffect(() => {
    if (!containerRef.current || !option) return undefined;
    const chart = echarts.init(containerRef.current);
    chartRef.current = chart;
    chart.setOption(option, true);
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = undefined;
    };
  }, [option]);

  if (!option) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置维度和指标" className="mt-8" />;
  }
  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
}

function MetricWidget({ widget, dataset }: { widget: DashboardWidget; dataset: PublishedDataset }) {
  const metric = widget.metrics[0];
  const row = aggregateWidgetRows(dataset, widget)[0];
  if (!metric) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请添加指标" className="mt-4" />;
  }
  const value = row?.values[metric.field] ?? 0;
  return (
    <div className="flex h-full flex-col justify-center px-5">
      <div className="text-[12px] font-medium text-[#667085]">{metricDisplayName(dataset, metric)}</div>
      <div className="mt-2 text-[28px] font-semibold tracking-[-0.02em] text-[#161823]">
        {formatMetricValue(value)}
      </div>
      <div className="mt-2 text-[11px] text-[#98a2b3]">{dataset.name}</div>
    </div>
  );
}

function TableWidget({ widget, dataset }: { widget: DashboardWidget; dataset: PublishedDataset }) {
  const rows = aggregateWidgetRows(dataset, widget);
  const dimensions = widget.dimensions.map((field) => getField(dataset, field)).filter(Boolean);
  return (
    <div className="h-full overflow-auto">
      <table className="w-full border-collapse text-[11px]">
        <thead className="sticky top-0 z-10 bg-[#fafafa] text-[#475467]">
          <tr>
            {dimensions.map((field) => (
              <th key={field?.key} className="whitespace-nowrap border-b border-[#e7eaf0] px-3 py-2 text-left font-medium">
                {field?.label}
              </th>
            ))}
            {widget.metrics.map((metric) => (
              <th key={metric.field} className="whitespace-nowrap border-b border-[#e7eaf0] px-3 py-2 text-right font-medium">
                {metricDisplayName(dataset, metric)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key} className="hover:bg-[#fafbfc]">
              {widget.dimensions.map((field) => (
                <td key={field} className="border-b border-[#f0f2f5] px-3 py-2 text-[#344054]">
                  {String(row.raw[field] ?? '')}
                </td>
              ))}
              {widget.metrics.map((metric) => (
                <td key={metric.field} className="border-b border-[#f0f2f5] px-3 py-2 text-right tabular-nums text-[#344054]">
                  {formatMetricValue(row.values[metric.field] ?? 0)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function WidgetContent({ widget }: { widget: DashboardWidget }) {
  const dataset = findDataset(widget.datasetId);
  if (widget.type === 'metric') return <MetricWidget widget={widget} dataset={dataset} />;
  if (widget.type === 'table') return <TableWidget widget={widget} dataset={dataset} />;
  return <EChartWidget widget={widget} dataset={dataset} />;
}

export function WidgetShell({
  widget,
  selected,
  preview,
  onSelect,
  onDuplicate,
  onDelete,
}: {
  widget: DashboardWidget;
  selected: boolean;
  preview: boolean;
  onSelect: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  return (
    <div
      onMouseDown={onSelect}
      className={[
        'group relative flex h-full min-h-0 flex-col overflow-hidden bg-white',
        preview
          ? 'border border-[#e7eaf0]'
          : selected
            ? 'border border-[var(--yak-brand-color)] shadow-[0_0_0_1px_var(--yak-brand-color-soft)]'
            : 'border border-[#e3e7ed] hover:border-[#cbd2dc]',
      ].join(' ')}
    >
      <div className="dashboard-widget__drag-handle flex h-9 shrink-0 cursor-move items-center border-b border-[#f0f2f5] px-3">
        {!preview ? <GripVertical size={13} className="mr-1 text-[#98a2b3]" /> : null}
        <span className="min-w-0 flex-1 truncate text-[12px] font-medium text-[#344054]">{widget.title}</span>
        {!preview ? (
          <div className={['flex items-center gap-0.5 transition-opacity', selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'].join(' ')}>
            <Tooltip title="复制">
              <button
                type="button"
                onMouseDown={(event) => event.stopPropagation()}
                onClick={(event) => { event.stopPropagation(); onDuplicate(); }}
                className="flex h-6 w-6 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#f5f6f7]"
              >
                <Copy size={12} />
              </button>
            </Tooltip>
            <Tooltip title="删除">
              <button
                type="button"
                onMouseDown={(event) => event.stopPropagation()}
                onClick={(event) => { event.stopPropagation(); onDelete(); }}
                className="flex h-6 w-6 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#fff1f2] hover:text-[#d92d20]"
              >
                <Trash2 size={12} />
              </button>
            </Tooltip>
          </div>
        ) : null}
      </div>
      <div className="min-h-0 flex-1 overflow-hidden"><WidgetContent widget={widget} /></div>
    </div>
  );
}
