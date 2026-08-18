import { Empty, Spin } from 'antd';
import * as echarts from 'echarts';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  analysisMetricValues,
  formatAnalysisMetricValue,
  metricComputationFor,
  quickCalculationLabel,
  resolveAnalysisTopN,
} from './analysis';
import { encodingMeetsChartRequirements, resolveAnalysisEncoding } from './encoding';
import { queryAnalysisDataset } from './dataset-service';
import type {
  Aggregation,
  AnalysisFilter,
  AnalysisSelection,
  AnalysisSpec,
  DatasetField,
  DatasetQueryPayload,
  DatasetQueryResult,
  MetricBinding,
  PublishedDataset,
  Scalar,
} from './model';
import { paletteColors, resolveAnalysisStyle } from './style';

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

const metricAnalysisDisplayName = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  metric: MetricBinding,
) => {
  const base = metricDisplayName(dataset, metric);
  const calculation = spec.type === 'metric'
    ? 'none'
    : metricComputationFor(spec, metric.field).quickCalculation;
  return calculation === 'none' ? base : `${base} · ${quickCalculationLabel(calculation)}`;
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
  runtimeFilters: AnalysisFilter[] = [],
): DatasetQueryPayload => {
  const operators: Record<
    AnalysisFilter['operator'],
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
  const filters = [...spec.filters, ...runtimeFilters]
    .filter((filter) => filter.field && filter.value !== '')
    .map((filter) => {
      const field = getAnalysisField(dataset, filter.field);
      const value = filter.operator === 'contains'
        ? `%${filter.value}%`
        : filterValue(field, filter.value);
      return { fieldId: filter.field, operator: operators[filter.operator], value };
    });

  const sorts: DatasetQueryPayload['sorts'] = [];
  const topN = resolveAnalysisTopN(spec);
  if (topN) {
    sorts.push({
      fieldId: topN.metric.field,
      aggregation: topN.metric.aggregation,
      direction: topN.direction === 'top' ? 'DESC' : 'ASC',
    });
  }

  const sort = spec.sort;
  if (sort?.field) {
    const metric = spec.metrics.find((item) => item.field === sort.field);
    const valid = Boolean(metric || spec.dimensions.includes(sort.field));
    const duplicatedByTopN = Boolean(topN && metric?.field === topN.metric.field);
    if (valid && !duplicatedByTopN) {
      sorts.push({
        fieldId: sort.field,
        aggregation: metric?.aggregation,
        direction: sort.direction === 'desc' ? 'DESC' : 'ASC',
      });
    }
  }

  const baseLimit = spec.limit ?? (spec.type === 'table' ? 200 : 500);
  return {
    dimensions: spec.type === 'metric' ? [] : spec.dimensions,
    metrics: spec.metrics.map((metric) => ({ fieldId: metric.field, aggregation: metric.aggregation })),
    filters,
    sorts,
    limit: topN ? Math.min(baseLimit, topN.count) : baseLimit,
    timeoutSeconds: spec.timeoutSeconds ?? 30,
  };
};

export const canQueryAnalysis = (spec: AnalysisSpec) => {
  if (!encodingMeetsChartRequirements(spec)) return false;
  if (spec.type === 'table') return spec.dimensions.length > 0 || spec.metrics.length > 0;
  return true;
};

function useAnalysisQuery(
  spec: AnalysisSpec,
  dataset?: PublishedDataset,
  runtimeFilters: AnalysisFilter[] = [],
) {
  const [result, setResult] = useState<DatasetQueryResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const sequence = useRef(0);
  const runtimeFilterKey = useMemo(() => JSON.stringify(runtimeFilters), [runtimeFilters]);
  const payload = useMemo(
    () => dataset ? buildDatasetQueryPayload(spec, dataset, runtimeFilters) : undefined,
    [dataset, spec, runtimeFilterKey],
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

const rowLabel = (result: DatasetQueryResult, row: Scalar[], dimensions: string[]) =>
  dimensions.map((fieldId) => String(cell(result, row, fieldId) ?? '')).join(' / ');

const scalarKey = (value: Scalar) => `${typeof value}:${String(value)}`;

const uniqueDimensionValues = (
  result: DatasetQueryResult,
  fieldId: string,
) => {
  const seen = new Set<string>();
  return result.rows.flatMap((row) => {
    const value = cell(result, row, fieldId);
    const key = scalarKey(value);
    if (seen.has(key)) return [];
    seen.add(key);
    return [{ key, value, label: String(value ?? '') }];
  });
};

const selectionForRow = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
  rowIndex: number,
): AnalysisSelection | undefined => {
  const encoding = resolveAnalysisEncoding(spec);
  const fieldId = encoding.category.find((binding) => binding.role === 'dimension')?.field
    ?? spec.dimensions[0];
  const row = result.rows[rowIndex];
  if (!fieldId || !row) return undefined;
  const value = cell(result, row, fieldId);
  if (value === null) return undefined;
  return {
    fieldId,
    value,
    label: `${getAnalysisField(dataset, fieldId)?.label ?? fieldId}: ${String(value)}`,
    rowIndex,
  };
};

const legendOption = (
  visible: boolean,
  position: 'top' | 'right' | 'bottom',
  axisText: string,
) => {
  if (!visible) return { show: false };
  const base = { textStyle: { color: axisText, fontSize: 11 } };
  if (position === 'right') return { ...base, orient: 'vertical', right: 0, top: 'middle' };
  if (position === 'bottom') return { ...base, orient: 'horizontal', left: 'center', bottom: 0 };
  return { ...base, orient: 'horizontal', right: 4, top: 0 };
};

const chartOptionFor = (
  spec: AnalysisSpec,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => {
  const encoding = resolveAnalysisEncoding(spec);
  const style = resolveAnalysisStyle(spec.style);
  const colors = paletteColors(spec.style);
  const firstDimension = encoding.category.find((binding) => binding.role === 'dimension')?.field
    ?? spec.dimensions[0];
  const colorDimension = encoding.color.find((binding) => binding.role === 'dimension')?.field;
  const dimension = getAnalysisField(dataset, firstDimension);
  const metrics = spec.metrics;
  const computedValues = new Map(metrics.map((metric) => [
    metric.field,
    analysisMetricValues(spec, result, metric),
  ]));
  const metricValue = (metric: MetricBinding, rowIndex: number) =>
    computedValues.get(metric.field)?.[rowIndex] ?? null;
  const formatted = (metric: MetricBinding, value: unknown) => {
    if (value === null || value === undefined) return '—';
    const number = Number(value);
    return formatAnalysisMetricValue(spec, metric, Number.isFinite(number) ? number : null);
  };
  const axisText = '#667085';
  const axisLine = '#d8dde6';
  const splitLine = '#eef1f5';

  if (!firstDimension || !metrics.length) return undefined;

  if (spec.type === 'pie') {
    const metric = metrics[0];
    const metricConfig = metricComputationFor(spec, metric.field);
    const legacyPieLabel = metricConfig.quickCalculation === 'none'
      && metricConfig.numberFormat === 'auto';
    const legendVisible = style.showLegend;
    const legendOnRight = legendVisible && style.legendPosition === 'right';
    const legendOnTop = legendVisible && style.legendPosition === 'top';
    const legendOnBottom = legendVisible && style.legendPosition === 'bottom';
    const labelPosition = style.dataLabelPosition === 'inside' ? 'inside' : 'outside';
    return {
      color: colors,
      tooltip: {
        trigger: 'item',
        valueFormatter: (value: unknown) => formatted(metric, value),
      },
      legend: legendOption(legendVisible, style.legendPosition, axisText),
      series: [{
        name: metricAnalysisDisplayName(spec, dataset, metric),
        type: 'pie',
        radius: [`${style.pieInnerRadius}%`, '70%'],
        center: [
          legendOnRight ? '38%' : '50%',
          legendOnTop ? '56%' : legendOnBottom ? '45%' : '52%',
        ],
        label: {
          show: style.showDataLabels,
          position: labelPosition,
          formatter: legacyPieLabel
            ? (labelPosition === 'inside' ? '{d}%' : '{b} {d}%')
            : (params: any) => labelPosition === 'inside'
              ? formatted(metric, params?.value)
              : `${params?.name ?? ''} ${formatted(metric, params?.value)}`.trim(),
        },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        data: result.rows.map((row, rowIndex) => ({
          name: rowLabel(result, row, [firstDimension]),
          value: metricValue(metric, rowIndex),
          __rowIndex: rowIndex,
        })),
      }],
    };
  }

  if (spec.type === 'bar' || spec.type === 'line') {
    const isLine = spec.type === 'line';
    const categories = uniqueDimensionValues(result, firstDimension);
    const colorValues = colorDimension
      ? uniqueDimensionValues(result, colorDimension)
      : [];
    const legendVisible = style.showLegend;
    const labelPosition = style.dataLabelPosition === 'inside' ? 'inside' : 'top';

    const rowIndexFor = (category: Scalar, color?: Scalar) => result.rows.findIndex((row) => (
      Object.is(cell(result, row, firstDimension), category)
      && (!colorDimension || Object.is(cell(result, row, colorDimension), color))
    ));

    const seriesStyleFor = (metric: MetricBinding) => ({
      smooth: isLine && style.smooth,
      symbolSize: isLine ? style.symbolSize : undefined,
      lineStyle: isLine ? { width: style.lineWidth } : undefined,
      barMaxWidth: !isLine ? style.barMaxWidth : undefined,
      itemStyle: !isLine ? { borderRadius: style.barRadius } : undefined,
      label: {
        show: style.showDataLabels,
        position: labelPosition,
        formatter: (params: any) => formatted(metric, params?.value),
      },
      tooltip: {
        valueFormatter: (value: unknown) => formatted(metric, value),
      },
    });

    const series = colorDimension
      ? metrics.flatMap((metric) => colorValues.map((color) => ({
        name: metrics.length > 1
          ? `${color.label} · ${metricAnalysisDisplayName(spec, dataset, metric)}`
          : color.label,
        type: spec.type,
        ...seriesStyleFor(metric),
        data: categories.map((category) => {
          const rowIndex = rowIndexFor(category.value, color.value);
          if (rowIndex < 0) return null;
          return {
            value: metricValue(metric, rowIndex),
            __rowIndex: rowIndex,
          };
        }),
      })))
      : metrics.map((metric) => ({
        name: metricAnalysisDisplayName(spec, dataset, metric),
        type: spec.type,
        ...seriesStyleFor(metric),
        data: categories.map((category) => {
          const rowIndex = rowIndexFor(category.value);
          if (rowIndex < 0) return null;
          return {
            value: metricValue(metric, rowIndex),
            __rowIndex: rowIndex,
          };
        }),
      }));

    return {
      color: colors,
      grid: {
        left: 22,
        right: legendVisible && style.legendPosition === 'right' ? 112 : 16,
        top: legendVisible && style.legendPosition === 'top' ? 34 : 14,
        bottom: legendVisible && style.legendPosition === 'bottom' ? 40 : 22,
        containLabel: true,
      },
      tooltip: { trigger: 'axis' },
      legend: legendOption(legendVisible, style.legendPosition, axisText),
      xAxis: {
        type: 'category',
        boundaryGap: !isLine,
        name: dimension?.label,
        nameTextStyle: { color: '#98a2b3', fontSize: 10 },
        data: categories.map((item) => item.label),
        axisLine: { lineStyle: { color: axisLine } },
        axisTick: { show: false },
        axisLabel: {
          color: axisText,
          fontSize: 11,
          rotate: style.axisLabelRotation,
        },
      },
      yAxis: {
        type: 'value',
        splitLine: { show: style.showGrid, lineStyle: { color: splitLine } },
        axisLabel: {
          color: axisText,
          fontSize: 11,
          formatter: (value: number) => formatAnalysisMetricValue(spec, metrics[0], value),
        },
      },
      series,
    };
  }

  return undefined;
};

function EChartAnalysis({
  spec,
  dataset,
  result,
  onSelect,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
  onSelect?: (selection: AnalysisSelection) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const option = useMemo(() => chartOptionFor(spec, dataset, result), [spec, dataset, result]);

  useEffect(() => {
    if (!containerRef.current || !option) return undefined;
    const chart = echarts.init(containerRef.current);
    chart.setOption(option, true);
    const click = (params: any) => {
      const rowIndex = Number(params?.data?.__rowIndex ?? params?.dataIndex);
      const selection = selectionForRow(spec, dataset, result, rowIndex);
      if (selection) onSelect?.(selection);
    };
    chart.on('click', click);
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.off('click', click);
      chart.dispose();
    };
  }, [option, onSelect, spec, dataset, result]);

  if (!option) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置必填编码" className="mt-8" />;
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
  const value = analysisMetricValues(spec, result, metric)[0] ?? null;
  const style = resolveAnalysisStyle(spec.style);
  const alignment = {
    left: { alignItems: 'flex-start' as const, textAlign: 'left' as const },
    center: { alignItems: 'center' as const, textAlign: 'center' as const },
    right: { alignItems: 'flex-end' as const, textAlign: 'right' as const },
  }[style.metricAlign];
  const valueSize = { sm: 24, md: 28, lg: 36 }[style.metricValueSize];
  return (
    <div className="flex h-full flex-col justify-center px-5" style={{ alignItems: alignment.alignItems }}>
      <div className="text-[12px] font-medium text-[#667085]" style={{ textAlign: alignment.textAlign }}>
        {metricAnalysisDisplayName(spec, dataset, metric)}
      </div>
      <div
        className="mt-2 font-semibold tracking-[-0.02em] text-[#161823]"
        style={{ fontSize: valueSize, lineHeight: 1.15, textAlign: alignment.textAlign }}
      >
        {formatAnalysisMetricValue(spec, metric, value)}
      </div>
      {style.showMetricMeta ? (
        <div className="mt-2 text-[11px] text-[#98a2b3]" style={{ textAlign: alignment.textAlign }}>
          {dataset.name} · DV{result.datasetVersionNo} · {result.elapsedMillis}ms
        </div>
      ) : null}
    </div>
  );
}

function TableAnalysis({
  spec,
  dataset,
  result,
  onSelect,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  result: DatasetQueryResult;
  onSelect?: (selection: AnalysisSelection) => void;
}) {
  const style = resolveAnalysisStyle(spec.style);
  const dimensions = spec.dimensions.map((field) => getAnalysisField(dataset, field)).filter(Boolean);
  const computedValues = new Map(spec.metrics.map((metric) => [
    metric.field,
    analysisMetricValues(spec, result, metric),
  ]));
  const cellPadding = style.tableDensity === 'compact'
    ? 'px-3 py-1.5'
    : style.tableDensity === 'relaxed'
      ? 'px-3 py-3'
      : 'px-3 py-2';
  return (
    <div className="h-full overflow-auto">
      <table className="w-full border-collapse text-[11px]">
        <thead className="sticky top-0 z-10 bg-[#fafafa] text-[#475467]">
          <tr>
            {dimensions.map((field) => (
              <th key={field?.key} className={`whitespace-nowrap border-b border-[#e7eaf0] text-left font-medium ${cellPadding}`}>
                {field?.label}
              </th>
            ))}
            {spec.metrics.map((metric) => (
              <th key={`${metric.field}-${metric.aggregation}`} className={`whitespace-nowrap border-b border-[#e7eaf0] text-right font-medium ${cellPadding}`}>
                {metricAnalysisDisplayName(spec, dataset, metric)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((row, rowIndex) => {
            const striped = style.stripedRows && rowIndex % 2 === 1;
            const hover = onSelect && spec.dimensions.length ? 'cursor-pointer hover:bg-[#f7f8fa]' : 'hover:bg-[#fafbfc]';
            return (
              <tr
                key={rowIndex}
                className={`${striped ? 'bg-[#fafbfc]' : ''} ${hover}`}
                onClick={() => {
                  const selection = selectionForRow(spec, dataset, result, rowIndex);
                  if (selection) onSelect?.(selection);
                }}
              >
                {spec.dimensions.map((field) => (
                  <td key={field} className={`border-b border-[#f0f2f5] text-[#344054] ${cellPadding}`}>
                    {String(cell(result, row, field) ?? '')}
                  </td>
                ))}
                {spec.metrics.map((metric) => (
                  <td key={`${metric.field}-${metric.aggregation}`} className={`border-b border-[#f0f2f5] text-right tabular-nums text-[#344054] ${cellPadding}`}>
                    {formatAnalysisMetricValue(spec, metric, computedValues.get(metric.field)?.[rowIndex])}
                  </td>
                ))}
              </tr>
            );
          })}
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
  runtimeFilters = [],
  onSelect,
  className = 'h-full min-h-0',
}: {
  spec: AnalysisSpec;
  dataset?: PublishedDataset;
  runtimeFilters?: AnalysisFilter[];
  onSelect?: (selection: AnalysisSelection) => void;
  className?: string;
}) {
  const { result, loading, error } = useAnalysisQuery(spec, dataset, runtimeFilters);
  if (!dataset) {
    return <div className={className}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Dataset 不存在或已下线" className="mt-8" /></div>;
  }
  if (!canQueryAnalysis(spec)) {
    return <div className={className}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请配置必填编码" className="mt-8" /></div>;
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
      {spec.type === 'table' ? <TableAnalysis spec={spec} dataset={dataset} result={result} onSelect={onSelect} /> : null}
      {spec.type !== 'metric' && spec.type !== 'table'
        ? <EChartAnalysis spec={spec} dataset={dataset} result={result} onSelect={onSelect} />
        : null}
    </div>
  );
}
