import {
  metricComputationFor,
  NUMBER_FORMAT_OPTIONS,
  patchMetricComputation,
  QUICK_CALCULATION_OPTIONS,
} from '@/components/analysis/analysis';
import {
  calculatedFieldFor,
  isCalculatedFieldKey,
} from '@/components/analysis/calculated-field';
import { resolveAnalysisEncoding } from '@/components/analysis/encoding';
import type {
  AnalysisComputationConfig,
  AnalysisMetricComputation,
  AnalysisNumberFormat,
  AnalysisQuickCalculation,
  AnalysisSpec,
  AnalysisTopNDirection,
  PublishedDataset,
} from '@/components/analysis/model';
import { InputNumber, Select, Switch } from 'antd';
import { AGGREGATION_OPTIONS } from './helpers';

export function ChartAnalysisConfig({
  spec,
  dataset,
  onChange,
}: {
  spec: AnalysisSpec;
  dataset: PublishedDataset;
  onChange: (analysis: AnalysisComputationConfig) => void;
}) {
  const colorActive = Boolean(
    resolveAnalysisEncoding(spec).color.find((item) => item.role === 'dimension')?.field,
  );
  const supportsSequentialCalculation = spec.type !== 'metric' && spec.dimensions.length > 0;
  const topN = spec.analysis?.topN;
  const physicalMetrics = spec.metrics.filter((metric) => !isCalculatedFieldKey(spec, metric.field));
  const supportsTopN = spec.type !== 'metric' && spec.dimensions.length > 0 && physicalMetrics.length > 0;
  const topNMetricActive = !topN || physicalMetrics.some((metric) => metric.field === topN.metricField);
  const aggregationLabels = Object.fromEntries(
    AGGREGATION_OPTIONS.map((item) => [item.value, item.label]),
  );

  const patchMetric = (field: string, patch: Partial<AnalysisMetricComputation>) => {
    onChange(patchMetricComputation(spec, field, patch));
  };

  const patchTopN = (patch: Partial<NonNullable<AnalysisComputationConfig['topN']>>) => {
    const fallbackMetric = physicalMetrics[0];
    if (!fallbackMetric) return;
    const currentMetric = topN && physicalMetrics.some((metric) => metric.field === topN.metricField)
      ? topN.metricField
      : fallbackMetric.field;
    onChange({
      ...spec.analysis,
      version: 1,
      topN: {
        enabled: topN?.enabled ?? false,
        metricField: currentMetric,
        count: topN?.count ?? 10,
        direction: topN?.direction ?? 'top',
        ...patch,
      },
    });
  };

  return (
    <div className="space-y-4 pb-1 text-[11px] text-[#475467]">
      <div>
        <div className="mb-2.5 text-[10px] font-semibold text-[#667085]">指标计算</div>
        <div className="space-y-2.5">
          {spec.metrics.map((metric) => {
            const field = dataset.fields.find((item) => item.key === metric.field);
            const calculated = calculatedFieldFor(spec, metric.field);
            const config = metricComputationFor(spec, metric.field);
            const stored = spec.analysis?.metrics?.[metric.field];
            return (
              <div key={metric.field} className="rounded-[8px] border border-[#e8eaee] bg-[#fafbfc] p-2.5">
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <div className="truncate text-[10px] font-medium text-[#344054]">
                      {calculated?.name ?? field?.label ?? metric.field}
                    </div>
                    <div className="mt-0.5 text-[9px] text-[#98a2b3]">
                      {calculated ? '计算字段' : aggregationLabels[metric.aggregation] ?? metric.aggregation}
                    </div>
                  </div>
                  {supportsSequentialCalculation ? (
                    <Select
                      size="small"
                      className="w-[126px]"
                      value={config.quickCalculation}
                      options={QUICK_CALCULATION_OPTIONS.map((item) => ({
                        label: item.label,
                        value: item.value,
                      }))}
                      onChange={(quickCalculation: AnalysisQuickCalculation) =>
                        patchMetric(metric.field, { quickCalculation })}
                    />
                  ) : (
                    <span className="text-[9px] text-[#98a2b3]">单值指标</span>
                  )}
                </div>

                <div className="mt-2.5 grid grid-cols-[1fr_78px] gap-2">
                  <Select
                    size="small"
                    value={config.numberFormat}
                    options={NUMBER_FORMAT_OPTIONS}
                    onChange={(numberFormat: AnalysisNumberFormat) =>
                      patchMetric(metric.field, { numberFormat })}
                  />
                  <Select
                    size="small"
                    value={stored?.decimalPlaces ?? 'auto'}
                    options={[
                      { label: '自动', value: 'auto' },
                      ...([0, 1, 2, 3, 4] as const).map((value) => ({ label: `${value} 位`, value })),
                    ]}
                    onChange={(decimalPlaces: 'auto' | 0 | 1 | 2 | 3 | 4) =>
                      patchMetric(metric.field, {
                        decimalPlaces: decimalPlaces === 'auto' ? undefined : decimalPlaces,
                      })}
                  />
                </div>
                <label className="mt-2.5 flex items-center justify-between">
                  <span className="text-[10px] text-[#667085]">千分位</span>
                  <Switch
                    size="small"
                    checked={config.useGrouping}
                    onChange={(useGrouping) => patchMetric(metric.field, { useGrouping })}
                  />
                </label>
                {calculated ? (
                  <div className="mt-2 truncate rounded-[5px] bg-white px-2 py-1 font-mono text-[8px] text-[#98a2b3]" title={calculated.expression}>
                    {calculated.expression}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
        <div className="mt-2 text-[9px] leading-4 text-[#98a2b3]">
          占比、累计、排名和较上期变化在当前查询结果上计算；计算字段也可继续叠加这些表计算。
        </div>
      </div>

      {supportsTopN ? (
        <div className="border-t border-[#f0f1f3] pt-4">
          <div className="mb-2.5 flex items-center justify-between">
            <div>
              <div className="text-[10px] font-semibold text-[#667085]">Top / Bottom N</div>
              <div className="mt-0.5 text-[9px] text-[#98a2b3]">服务端排序仅支持物理聚合指标</div>
            </div>
            <Switch
              size="small"
              checked={Boolean(topN?.enabled)}
              disabled={colorActive && !topN?.enabled}
              onChange={(enabled) => patchTopN({ enabled })}
            />
          </div>

          {topN?.enabled ? (
            <div className="space-y-2">
              <Select
                size="small"
                className="w-full"
                placeholder="选择 Top N 指标"
                value={topNMetricActive ? topN.metricField : undefined}
                options={physicalMetrics.map((metric) => ({
                  label: `${dataset.fields.find((item) => item.key === metric.field)?.label ?? metric.field} · ${aggregationLabels[metric.aggregation] ?? metric.aggregation}`,
                  value: metric.field,
                }))}
                onChange={(metricField: string) => patchTopN({ metricField })}
              />
              <div className="grid grid-cols-[1fr_92px] gap-2">
                <Select
                  size="small"
                  value={topN.direction}
                  options={[
                    { label: 'Top N', value: 'top' },
                    { label: 'Bottom N', value: 'bottom' },
                  ]}
                  onChange={(direction: AnalysisTopNDirection) => patchTopN({ direction })}
                />
                <InputNumber
                  size="small"
                  className="w-full"
                  min={1}
                  max={100}
                  value={topN.count}
                  onChange={(count) => {
                    if (typeof count === 'number') patchTopN({ count });
                  }}
                />
              </div>
              {!topNMetricActive ? (
                <div className="text-[9px] leading-4 text-[#98a2b3]">
                  原 Top N 指标当前不可用于服务端排序，请重新选择一个物理指标。
                </div>
              ) : null}
            </div>
          ) : null}

          {colorActive ? (
            <div className="mt-2 rounded-[6px] bg-[#f7f8fa] px-2 py-1.5 text-[9px] leading-4 text-[#98a2b3]">
              当前使用了颜色分组。为避免服务端截断某个分类的部分系列，Top N 暂不生效；移除颜色字段后会自动恢复。
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
