import { Empty, Spin } from 'antd';
import * as echarts from 'echarts';
import { useEffect, useMemo, useRef, useState } from 'react';
import { queryAnalysisDataset } from './dataset-service';
import type {
  Aggregation,
  AnalysisSpec,
  DatasetField,
  DatasetQueryPayload,
  DatasetQueryResult,
  PublishedDataset,
  Scalar,
} from './model';

export const AGGREGATION_LABELS: Record<Aggregation, string> = {
  SUM: '求和',
  AVG: '平均',
  COUNT: '计数',
  COUNT_DISTINCT: '去重计数',
  MAX: '最大值',
  MIN: '最小值',
};

export const getAnalysisField = (dataset: PublishedDataset, fieldKey?: string) =>
  dataset.fields.find((field) => field.key === fieldKey);

export const metricDisplayName = (
  dataset: PublishedDataset,
  metric: AnalysisSpec['metrics'][number],
) => `${getAnalysisField(dataset, metric.field)?.label ?? metric.field} · ${AGGREGATION_LABELS[metric.aggregation]}`;

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

export const buildDatasetQueryPayload = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
): DatasetQueryPayload => {
  const operators: Record<
    AnalysisSpec['filters'][number]['operator'],
    DatasetQueryPayload['filters'][number]['operator']
  > = {
    eq: 'EQ',
    neq: 'NE',
    contains: 'LIKE',
    gt: 'GT',
    gte: 'GTE',
    lt: 'LT',
    lte: 'LTE',
  };
  const filters = spec.filters
    .filter((filter) => filter.field && filter.value !== '')
    .map((filter) => {
      const field = getAnalysisField(dataset, filter.field);
      const value = filter.operator === 'contains'
        ? `%${filter.value}%`
        : filterValue(field, filter.value);
      return { fieldId: filter.field, operator: operators[filter.operator], value };
    });

  const sort = spec.sort;
  let sorts: DatasetQueryPayload['sorts'] = [];
  if (sort?.field) {
    const metric = spec.metrics.find((item) => item.field === sort.field);
    if (metric || spec.dimensions.includes(sort.field)) {
      sorts = [{
        fieldId: sort.field,
        aggregation: metric?.aggregation,
        direction: sort.direction === 'desc' ? 'DESC' : 'ASC',
      }];
    }
  }

  return {
    dimensions: spec.type === 'metric' ? [] : spec.dimensions,
    metrics: spec.metrics.map((metric) => ({ fieldId: metric.field, aggregation: metric.aggregation })),
    filters,
    sorts,
    limit: spec.limit ?? (spec.type === 'table' ? 200 : 500),
    timeoutSeconds: spec.timeoutSeconds ?? 30,
  };
};

export const canQueryAnalysis = (spec: AnalysisSpec) => {
  if (spec.type === 'metric') return spec.metrics.length === 1;
  if (spec.type === 'table') return spec.dimensions.length > 0 || spec.metrics.length > 0;
  return spec.dimensions.length > 0 && spec.metrics.length > 0;
};

function useAnalysisQuery(spec: AnalysisSpec, dataset?: PublishedDataset) {
  const [result, setResult] = useState<DatasetQueryResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const sequence = useRef(0);
  const payload = useMemo(
    () => dataset ? buildDatasetQueryPayload(spec, dataset) : undefined,
    [dataset, spec],
  );
  const payloadKey = useMemo(() => JSON.stringify(payload), [payload]);

  useEffect(() => {
    if (!dataset || !payload || !canQueryAnalysis(spec)) {
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
        const value = await queryAnalysisDataset(dataset.id, payload);
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
    return () => {
      window.clearTimeout(timer);
      if (sequence.current === requestId) sequence.current += 1;
    };
  }, [dataset?.id, dataset?.currentVersionNo, payloadKey]);

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
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const firstDimension = spec.dimensions[0];
  const dimension = getAnalysisField(dataset, firstDimension);
  const metrics = spec.metrics;
  const axisText = '#667085';
  const axisLine = '#d8dde6';
  const splitLine = '#eef1f5';

  if (!firstDimension || !metrics.length) return undefined;

  if (spec.type === 'pie') {
    const metric = metrics[0];
    return {
      tooltip: { trigger: 'item' },
      legend: spec.style.showLegend
        ? { orient: 'vertical', right: 8, top: 'middle', textStyle: { color: axisText, fontSize: 11 } }
        : { show: false },
      series: [{
        name: metricDisplayName(dataset, metric),
        type: 'pie',
        radius: ['42%', '70%'],
        center: [spec.style.showLegend ? '38%' : '50%', '52%'],
        label: { show: spec.style.showDataLabels, formatter: '{b} {d}%' },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        data: result.rows.map((row) => ({
          name: rowLabel(result, row, spec.dimensions),
          value: numericCell(result, row, metric.field, metric.aggregation),
        })),
      }],
    };
  }

  if (spec.type === 'bar' || spec.type === 'line') {
    const isLine = spec.type === 'line';
    return {
      grid: { left: 22, right: 16, top: spec.style.showLegend ? 30 : 14, bottom: 22, containLabel: true },
      tooltip: { trigger: 'axis' },
      legend: spec.style.showLegend
        ? { top: 0, right: 4, textStyle: { color: axisText, fontSize: 11 } }
        : { show: false },
      xAxis: {
        type: 'category',
        boundaryGap: !isLine,
        name: dimension?.label,
        nameTextStyle: { color: '#98a2b3', fontSize: 10 },
        data: result.rows.map((row) => rowLabel(result, row, spec.dimensions)),
        axisLine: { lineStyle: { color: axisLine } },
        axisTick: { show: false },
        axisLabel: { color: axisText, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        splitLine: { show: spec.style.showGrid, lineStyle: { color: splitLine } },
        axisLabel: { color: axisText, fontSize: 11 },
      },
      series: metrics.map((metric) => ({
        name: metricDisplayName(dataset, metric),
        type: spec.type,
        smooth: isLine && spec.style.smooth,
        symbolSize: 5,
        barMaxWidth: 34,
        label: { show: spec.style.showDataLabels, position: 'top' },
        data: result.rows.map((row) => numericCell(result, row, metric.field, metric.aggregation)),
      })),
    };
  }

  return undefined;
};

function EChartAnalysis({
  spec,
  dataset,
  result,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const option = useMemo(() => chartOptionFor(spec, dataset, result), [spec, dataset, result]);

  useEffect(() => {
    if (!containerRef.current || !option) return undefined;
    const chart = echarts.init(containerRef.current);
    chart.setOption(option, true);
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
    };
  }, [option]);

  if (!option) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置维度和指标" className="mt-8" />;
  }
  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
}

function MetricAnalysis({
  spec,
  dataset,
  result,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}) {
  const metric = spec.metrics[0];
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

function TableAnalysis({
  spec,
  dataset,
  result,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
}) {
  const dimensions = spec.dimensions.map((field) => getAnalysisField(dataset, field)).filter(Boolean);
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
            {spec.metrics.map((metric) => (
              <th key={`${metric.field}-${metric.aggregation}`} className="whitespace-nowrap border-b border-[#e7eaf0] px-3 py-2 text-right font-medium">
                {metricDisplayName(dataset, metric)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, rowIndex) => (
            <tr key={rowIndex} className="hover:bg-[#fafbfc]">
              {spec.dimensions.map((field) => (
                <td key={field} className="border-b border-[#f0f2f5] px-3 py-2 text-[#344054]">
                  {String(cell(result, row, field) ?? '')}
                </td>
              ))}
              {spec.metrics.map((metric) => (
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

export function AnalysisPreview({
  spec,
  dataset,
  className = 'h-full min-h-0',
}: {
  spec: AnalysisSpec;
  dataset?: PublishedDataset;
  className?: string;
}) {
  const { result, loading, error } = useAnalysisQuery(spec, dataset);
  if (!dataset) {
    return <div className={className}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Dataset 不存在或已下线" className="mt-8" /></div>;
  }
  if (!canQueryAnalysis(spec)) {
    return <div className={className}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置维度和指标" className="mt-8" /></div>;
  }
  if (loading && !result) {
    return <div className={`${className} flex items-center justify-center`}><Spin size="small" /></div>;
  }
  if (error) {
    return <div className={`${className} flex items-center justify-center px-5 text-center text-[11px] text-[#b42318]`}>{error}</div>;
  }
  if (!result) return <div className={className} />;

  return (
    <div className={`relative ${className}`}>
      {loading ? <Spin size="small" className="absolute right-3 top-3 z-20" /> : null}
      {spec.type === 'metric' ? <MetricAnalysis spec={spec} dataset={dataset} result={result} /> : null}
      {spec.type === 'table' ? <TableAnalysis spec={spec} dataset={dataset} result={result} /> : null}
      {spec.type !== 'metric' && spec.type !== 'table'
        ? <EChartAnalysis spec={spec} dataset={dataset} result={result} />
        : null}
    </div>
  );
}
