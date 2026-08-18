import type {
  Aggregation,
  AnalysisEncoding,
  AnalysisEncodingBinding,
  AnalysisEncodingChannel,
  AnalysisSpec,
  ChartType,
  DatasetFieldRole,
  MetricBinding,
  PublishedDataset,
} from './model';

export interface AnalysisEncodingSlotRule {
  channel: AnalysisEncodingChannel;
  label: string;
  roles: DatasetFieldRole[];
  min: number;
  max: number;
  hint?: string;
}

const slot = (
  channel: AnalysisEncodingChannel,
  label: string,
  roles: DatasetFieldRole[],
  min: number,
  max: number,
  hint?: string,
): AnalysisEncodingSlotRule => ({ channel, label, roles, min, max, hint });

/**
 * Active visual channels for the chart types currently shipped by Yak Ops.
 * The encoding model already defines color / size / label / detail / tooltip so
 * future chart renderers can enable them without another persistence migration.
 */
export const ANALYSIS_ENCODING_RULES: Record<ChartType, AnalysisEncodingSlotRule[]> = {
  metric: [
    slot('value', '值', ['metric'], 1, 1, '指标卡需要 1 个指标'),
  ],
  bar: [
    slot('category', '分类', ['dimension'], 1, 1, '柱状图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 3, '最多 3 个指标'),
  ],
  line: [
    slot('category', '分类', ['dimension'], 1, 1, '折线图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 3, '最多 3 个指标'),
  ],
  pie: [
    slot('category', '分类', ['dimension'], 1, 1, '饼图需要 1 个分类字段'),
    slot('value', '值', ['metric'], 1, 1, '饼图需要 1 个指标'),
  ],
  table: [
    slot('category', '维度', ['dimension'], 0, 3, '最多 3 个维度'),
    slot('value', '指标', ['metric'], 0, 3, '最多 3 个指标'),
  ],
};

export const EMPTY_ANALYSIS_ENCODING: AnalysisEncoding = {
  version: 1,
  category: [],
  value: [],
  color: [],
  size: [],
  label: [],
  detail: [],
  tooltip: [],
};

const cloneBinding = (binding: AnalysisEncodingBinding): AnalysisEncodingBinding => ({
  field: binding.field,
  role: binding.role,
  aggregation: binding.aggregation,
});

export const cloneAnalysisEncoding = (encoding: AnalysisEncoding): AnalysisEncoding => ({
  version: 1,
  category: encoding.category.map(cloneBinding),
  value: encoding.value.map(cloneBinding),
  color: encoding.color.map(cloneBinding),
  size: encoding.size.map(cloneBinding),
  label: encoding.label.map(cloneBinding),
  detail: encoding.detail.map(cloneBinding),
  tooltip: encoding.tooltip.map(cloneBinding),
});

export const legacyAnalysisEncoding = (spec: Pick<AnalysisSpec, 'dimensions' | 'metrics'>): AnalysisEncoding => ({
  ...cloneAnalysisEncoding(EMPTY_ANALYSIS_ENCODING),
  category: spec.dimensions.map((field) => ({ field, role: 'dimension' as const })),
  value: spec.metrics.map((metric) => ({
    field: metric.field,
    role: 'metric' as const,
    aggregation: metric.aggregation,
  })),
});

/** Existing snapshots without `encoding` are upgraded lazily and losslessly in memory. */
export const resolveAnalysisEncoding = (spec: Pick<AnalysisSpec, 'encoding' | 'dimensions' | 'metrics'>): AnalysisEncoding => (
  spec.encoding?.version === 1
    ? cloneAnalysisEncoding(spec.encoding)
    : legacyAnalysisEncoding(spec)
);

const activeRuleMap = (type: ChartType) => new Map(
  ANALYSIS_ENCODING_RULES[type].map((rule) => [rule.channel, rule]),
);

const metricProjection = (bindings: AnalysisEncodingBinding[]): MetricBinding[] => bindings
  .filter((binding) => binding.role === 'metric')
  .map((binding) => ({
    field: binding.field,
    aggregation: binding.aggregation ?? 'SUM',
  }));

/**
 * Keeps the existing query/render contract alive while encoding becomes the editor's
 * semantic source of truth. Inactive channels remain persisted in `encoding`, but do
 * not leak into the legacy query projection for a chart type that cannot render them.
 */
export const applyAnalysisEncoding = <T extends AnalysisSpec>(
  spec: T,
  encoding: AnalysisEncoding,
): T => {
  const rules = activeRuleMap(spec.type);
  const categoryRule = rules.get('category');
  const valueRule = rules.get('value');
  return {
    ...spec,
    encoding: cloneAnalysisEncoding(encoding),
    dimensions: categoryRule
      ? encoding.category
        .filter((binding) => binding.role === 'dimension')
        .slice(0, categoryRule.max)
        .map((binding) => binding.field)
      : [],
    metrics: valueRule
      ? metricProjection(encoding.value).slice(0, valueRule.max)
      : [],
  };
};

const sanitizeChannel = (
  bindings: AnalysisEncodingBinding[],
  rule: AnalysisEncodingSlotRule,
) => bindings
  .filter((binding) => rule.roles.includes(binding.role))
  .slice(0, rule.max)
  .map(cloneBinding);

/**
 * Chart switching preserves every inactive semantic channel, while active channels are
 * validated and trimmed to the target chart's capacity. This is what makes switching
 * metric -> bar -> metric reversible instead of destructively clearing field choices.
 */
export const changeAnalysisEncodingType = <T extends AnalysisSpec>(
  spec: T,
  type: ChartType,
): T => {
  const encoding = resolveAnalysisEncoding(spec);
  const nextRules = ANALYSIS_ENCODING_RULES[type];
  const next = cloneAnalysisEncoding(encoding);
  nextRules.forEach((rule) => {
    next[rule.channel] = sanitizeChannel(next[rule.channel], rule);
  });
  return applyAnalysisEncoding({ ...spec, type } as T, next);
};

export const analysisEncodingFieldKeys = (
  spec: Pick<AnalysisSpec, 'encoding' | 'dimensions' | 'metrics'>,
) => {
  const encoding = resolveAnalysisEncoding(spec);
  return new Set< string >([
    ...encoding.category.map((binding) => binding.field),
    ...encoding.value.map((binding) => binding.field),
    ...encoding.color.map((binding) => binding.field),
    ...encoding.size.map((binding) => binding.field),
    ...encoding.label.map((binding) => binding.field),
    ...encoding.detail.map((binding) => binding.field),
    ...encoding.tooltip.map((binding) => binding.field),
  ]);
};

const validBindingForDataset = (
  binding: AnalysisEncodingBinding,
  dataset: PublishedDataset,
) => {
  const field = dataset.fields.find((item) => item.key === binding.field);
  if (!field || field.role !== binding.role) return undefined;
  return {
    ...binding,
    aggregation: binding.role === 'metric'
      ? (binding.aggregation ?? 'SUM')
      : undefined,
  } satisfies AnalysisEncodingBinding;
};

/** Dataset changes remove invalid bindings from every semantic channel in one place. */
export const rebindAnalysisEncoding = <T extends AnalysisSpec>(
  spec: T,
  dataset: PublishedDataset,
): T => {
  const source = resolveAnalysisEncoding(spec);
  const next = cloneAnalysisEncoding(EMPTY_ANALYSIS_ENCODING);
  (Object.keys(next) as Array<keyof AnalysisEncoding>).forEach((key) => {
    if (key === 'version') return;
    next[key] = source[key]
      .map((binding) => validBindingForDataset(binding, dataset))
      .filter(Boolean) as AnalysisEncodingBinding[];
  });
  return applyAnalysisEncoding({ ...spec, datasetId: dataset.id } as T, next);
};

export const updateEncodingMetricAggregation = <T extends AnalysisSpec>(
  spec: T,
  field: string,
  aggregation: Aggregation,
): T => {
  const encoding = resolveAnalysisEncoding(spec);
  const patchChannel = (bindings: AnalysisEncodingBinding[]) => bindings.map((binding) => (
    binding.field === field && binding.role === 'metric'
      ? { ...binding, aggregation }
      : binding
  ));
  const next: AnalysisEncoding = {
    ...encoding,
    value: patchChannel(encoding.value),
    color: patchChannel(encoding.color),
    size: patchChannel(encoding.size),
    label: patchChannel(encoding.label),
    detail: patchChannel(encoding.detail),
    tooltip: patchChannel(encoding.tooltip),
  };
  return applyAnalysisEncoding(spec, next);
};

export const encodingMeetsChartRequirements = (spec: AnalysisSpec) => {
  const encoding = resolveAnalysisEncoding(spec);
  return ANALYSIS_ENCODING_RULES[spec.type].every((rule) => {
    const validCount = encoding[rule.channel]
      .filter((binding) => rule.roles.includes(binding.role))
      .length;
    return validCount >= rule.min && validCount <= rule.max;
  });
};
