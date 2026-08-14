import { Empty, Spin, Tooltip } from 'antd';
import * as echarts from 'echarts';
import { Copy, GripVertical, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import type {
  Aggregation,
  DashboardWidget,
  DatasetField,
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
  Scalar,
} from './model';
import { queryDashboardDataset } from './service';

const aggregationLabels: Record<Aggregation, string> = {
  SUM: '求和',
  AVG: '平均',
  COUNT: '计数',
  COUNT_DISTINCT: '去重计数',
  MAX: '最大值',
  MIN: '最小值',
};

const getField = (dataset: PublishedDataset, fieldKey?: string) =>
  dataset.fields.find((field) => field.key === fieldKey);

const metricDisplayName = (
  dataset: PublishedDataset,
  metric: DashboardWidget['metrics'][number],
) => `${getField(dataset, metric.field)?.label ?? metric.field} · ${aggregationLabels[metric.aggregation]}`;

const formatMetricValue = (value: number) => {
  const maximumFractionDigits = Number.isInteger(value) ? 0 : 2;
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits }).format(value);
};

const filterValue = (field: DatasetField | undefined, value: string): Scalar => {
  if (field?.dataType === 'number') {
    const number = Number(value);
    if (Number.isFinite(number)) return number;
  }
  if (field?.dataType === 'boolean') {
    if (value.toLowerCase() === 'true') return true;
    if (value.toLowerCase() === 'false') return false;
  }
  return value;
};

const queryPayload = (widget: DashboardWidget, dataset: PublishedDataset): DatasetQueryPayload => {
  const operators: Record<DashboardWidget['filters'][number]['operator'], DatasetQueryPayload['filters'][number]['operator']> = {
    eq: 'EQ',
    neq: 'NE',
    contains: 'LIKE',
    gt: 'GT',
    gte: 'GTE',
    lt: 'LT',
    lte: 'LTE',
  };
  const filters = widget.filters
    .filter((filter) => filter.field && filter.value !== '')
    .map((filter) => {
      const field = getField(dataset, filter.field);
      const value = filter.operator === 'contains'
        ? `%${filter.value}%`
        : filterValue(field, filter.value);
      return { fieldId: filter.field, operator: operators[filter.operator], value };
    });
  const sorts = widget.sort?.field
    ? (() => {
      const metric = widget.metrics.find((item) => item.field === widget.sort?.field);
      if (!metric && !widget.dimensions.includes(widget.sort.field)) return [];
      return [{
        fieldId: widget.sort.field,
        aggregation: metric?.aggregation,
        direction: widget.sort.direction === 'desc' ? 'DESC' as const : 'ASC' as const,
      }];
    })()
    : [];
  return {
    dimensions: widget.type === 'metric' ? [] : widget.dimensions,
    metrics: widget.metrics.map((metric) => ({ fieldId: metric.field, aggregation: metric.aggregation })),
    filters,
    sorts,
    limit: widget.type === 'table' ? 200 : 500,
    timeoutSeconds: 30,
  };
};

const canQuery = (widget: DashboardWidget) => {
  if (widget.type === 'metric') return widget.metrics.length > 0;
  return widget.dimensions.length > 0 && widget.metrics.length > 0;
};

function useWidgetQuery(widget: DashboardWidget, dataset?: PublishedDataset) {
  const [result, setResult] = useState<DatasetQueryResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const sequence = useRef(0);
  const payload = useMemo(
    () => dataset ? queryPayload(widget, dataset) : undefined,
    [dataset, widget],
  );
  const payloadKey = useMemo(() => JSON.stringify(payload), [payload]);

  useEffect(() => {
    if (!dataset || !payload || !canQuery(widget)) {
      setResult(undefined);
      setError('');
      setLoading(false);
      return undefined;
    }
    const requestId = ++sequence.current;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      setError('');
      try {
        const value = await queryDashboardDataset(dataset.id, payload);
        if (requestId === sequence.current) setResult(value);
      } catch (queryError) {
        if (requestId === sequence.current) {
          setResult(undefined);
          setError(queryError instanceof Error ? queryError.message : 'Dataset 查询失败');
        }
      } finally {
        if (requestId === sequence.current) setLoading(false);
      }
    }, 180);
    return () => window.clearTimeout(timer);
  }, [dataset?.id, payloadKey, widget]);

  return { result, loading, error };
}

const bindingIndex = (
  result: DatasetQueryResult,
  fieldId: string,
  aggregation?: Aggregation,
) => result.bindings.findIndex((binding) => (
  binding.fieldId === fieldId
    && (aggregation ? binding.aggregation === aggregation : !binding.aggregation)
));

const cell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation?: Aggregation,
) => {
  const index = bindingIndex(result, fieldId, aggregation);
  return index >= 0 ? row[index] : null;
};

const numericCell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation: Aggregation,
) => {
  const value = Number(cell(result, row, fieldId, aggregation) ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const rowLabel = (result: DatasetQueryResult, row: Scalar[], dimensions: string[]) =>
  dimensions.map((fieldId) => String(cell(result, row, fieldId) ?? '')).join(' / ');

const chartOptionFor = (
  widget: DashboardWidget,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
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
        data: result.rows.map((row) => ({
          name: rowLabel(result, row, widget.dimensions),
          value: numericCell(result, row, metric.field, metric.aggregation),
        })),
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
        data: result.rows.map((row) => rowLabel(result, row, widget.dimensions)),
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
        data: result.rows.map((row) => numericCell(result, row, metric.field, metric.aggregation)),
      })),
    };
  }

  return undefined;
};

function EChartWidget({
  widget,
  dataset,
  result,
}: {
  widget: DashboardWidget;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts>();
  const option = useMemo(() => chartOptionFor(widget, dataset, result), [widget, dataset, result]);

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

function MetricWidget({
  widget,
  dataset,
  result,
}: {
  widget: DashboardWidget;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}) {
  const metric = widget.metrics[0];
  if (!metric) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请添加指标" className="mt-4" />;
  const value = result.rows[0]
    ? numericCell(result, result.rows[0], metric.field, metric.aggregation)
    : 0;
  return (
    <div className="flex h-full flex-col justify-center px-5">
      <div className="text-[12px] font-medium text-[#667085]">{metricDisplayName(dataset, metric)}</div>
      <div className="mt-2 text-[28px] font-semibold tracking-[-0.02em] text-[#161823]">
        {formatMetricValue(value)}
      </div>
      <div className="mt-2 text-[11px] text-[#98a2b3]">
        {dataset.name} · DV{result.datasetVersionNo} · {result.elapsedMillis}ms
      </div>
    </div>
  );
}

function TableWidget({
  widget,
  dataset,
  result,
}: {
  widget: DashboardWidget;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}) {
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
              <th key={`${metric.field}-${metric.aggregation}`} className="whitespace-nowrap border-b border-[#e7eaf0] px-3 py-2 text-right font-medium">
                {metricDisplayName(dataset, metric)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, rowIndex) => (
            <tr key={rowIndex} className="hover:bg-[#fafbfc]">
              {widget.dimensions.map((field) => (
                <td key={field} className="border-b border-[#f0f2f5] px-3 py-2 text-[#344054]">
                  {String(cell(result, row, field) ?? '')}
                </td>
              ))}
              {widget.metrics.map((metric) => (
                <td key={`${metric.field}-${metric.aggregation}`} className="border-b border-[#f0f2f5] px-3 py-2 text-right tabular-nums text-[#344054]">
                  {formatMetricValue(numericCell(result, row, metric.field, metric.aggregation))}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {result.truncated ? (
        <div className="px-3 py-2 text-[10px] text-[#98a2b3]">结果已截断，仅展示前 {result.returnedRows} 行</div>
      ) : null}
    </div>
  );
}

function WidgetContent({ widget, dataset }: { widget: DashboardWidget; dataset?: PublishedDataset }) {
  const { result, loading, error } = useWidgetQuery(widget, dataset);
  if (!dataset) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Dataset 不存在或已下线" className="mt-8" />;
  if (!canQuery(widget)) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置维度和指标" className="mt-8" />;
  if (loading && !result) return <div className="flex h-full items-center justify-center"><Spin size="small" /></div>;
  if (error) return <div className="flex h-full items-center justify-center px-5 text-center text-[11px] text-[#b42318]">{error}</div>;
  if (!result) return null;
  if (widget.type === 'metric') return <MetricWidget widget={widget} dataset={dataset} result={result} />;
  if (widget.type === 'table') return <TableWidget widget={widget} dataset={dataset} result={result} />;
  return <EChartWidget widget={widget} dataset={dataset} result={result} />;
}

export function WidgetShell({
  widget,
  dataset,
  selected,
  preview,
  onSelect,
  onDuplicate,
  onDelete,
}: {
  widget: DashboardWidget;
  dataset?: PublishedDataset;
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
        {dataset ? <span className="mr-2 text-[9px] text-[#b0b7c3]">DV{dataset.currentVersionNo ?? '-'}</span> : null}
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
      <div className="min-h-0 flex-1 overflow-hidden"><WidgetContent widget={widget} dataset={dataset} /></div>
    </div>
  );
}
