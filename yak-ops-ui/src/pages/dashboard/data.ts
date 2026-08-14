import type {
  AggregatedRow,
  DashboardFilter,
  DashboardWidget,
  DatasetField,
  MetricBinding,
  PublishedDataset,
  Scalar,
} from './model';

const numericValue = (value: Scalar | undefined) => {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
};

const aggregateMetric = (
  rows: Array<Record<string, Scalar>>,
  metric: MetricBinding,
) => {
  if (metric.aggregation === 'COUNT') return rows.length;
  const values = rows.map((row) => numericValue(row[metric.field]));
  if (!values.length) return 0;
  if (metric.aggregation === 'AVG') {
    return values.reduce((sum, value) => sum + value, 0) / values.length;
  }
  if (metric.aggregation === 'MAX') return Math.max(...values);
  if (metric.aggregation === 'MIN') return Math.min(...values);
  return values.reduce((sum, value) => sum + value, 0);
};

const matchesFilter = (
  row: Record<string, Scalar>,
  filter: DashboardFilter,
  field?: DatasetField,
) => {
  if (!filter.field || filter.value === '') return true;
  const value = row[filter.field];
  const target = filter.value;
  if (field?.dataType === 'number') {
    const left = numericValue(value);
    const right = Number(target);
    if (!Number.isFinite(right)) return true;
    switch (filter.operator) {
      case 'neq': return left !== right;
      case 'gt': return left > right;
      case 'gte': return left >= right;
      case 'lt': return left < right;
      case 'lte': return left <= right;
      default: return left === right;
    }
  }

  const left = String(value ?? '').toLocaleLowerCase();
  const right = target.toLocaleLowerCase();
  if (filter.operator === 'contains') return left.includes(right);
  if (filter.operator === 'neq') return left !== right;
  return left === right;
};

export const filterDatasetRows = (
  dataset: PublishedDataset,
  filters: DashboardFilter[],
) => {
  const fieldMap = new Map(dataset.fields.map((field) => [field.key, field]));
  return dataset.rows.filter((row) =>
    filters.every((filter) => matchesFilter(row, filter, fieldMap.get(filter.field))),
  );
};

export const aggregateWidgetRows = (
  dataset: PublishedDataset,
  widget: DashboardWidget,
): AggregatedRow[] => {
  const rows = filterDatasetRows(dataset, widget.filters);
  const dimensions = widget.dimensions;
  const metrics = widget.metrics;

  if (!dimensions.length) {
    return [{
      key: 'total',
      label: '全部',
      raw: {},
      values: Object.fromEntries(
        metrics.map((metric) => [metric.field, aggregateMetric(rows, metric)]),
      ),
    }];
  }

  const groups = new Map<string, Array<Record<string, Scalar>>>();
  rows.forEach((row) => {
    const key = dimensions.map((field) => String(row[field] ?? '')).join('\u0001');
    const group = groups.get(key) ?? [];
    group.push(row);
    groups.set(key, group);
  });

  const result = Array.from(groups.entries()).map(([key, groupedRows]) => {
    const first = groupedRows[0] ?? {};
    const raw = Object.fromEntries(dimensions.map((field) => [field, first[field] ?? '']));
    return {
      key,
      label: dimensions.map((field) => String(first[field] ?? '')).join(' / '),
      raw,
      values: Object.fromEntries(
        metrics.map((metric) => [metric.field, aggregateMetric(groupedRows, metric)]),
      ),
    } satisfies AggregatedRow;
  });

  const sort = widget.sort;
  if (!sort?.field) return result;

  const direction = sort.direction === 'desc' ? -1 : 1;
  return [...result].sort((left, right) => {
    const leftValue = left.raw[sort.field] ?? left.values[sort.field] ?? '';
    const rightValue = right.raw[sort.field] ?? right.values[sort.field] ?? '';
    if (typeof leftValue === 'number' && typeof rightValue === 'number') {
      return (leftValue - rightValue) * direction;
    }
    return String(leftValue).localeCompare(String(rightValue), 'zh-CN') * direction;
  });
};

export const getField = (dataset: PublishedDataset, fieldKey?: string) =>
  dataset.fields.find((field) => field.key === fieldKey);

export const metricDisplayName = (dataset: PublishedDataset, metric: MetricBinding) => {
  const field = getField(dataset, metric.field);
  const aggregationLabels: Record<MetricBinding['aggregation'], string> = {
    SUM: '求和',
    AVG: '平均',
    COUNT: '计数',
    MAX: '最大值',
    MIN: '最小值',
  };
  return `${field?.label ?? metric.field} · ${aggregationLabels[metric.aggregation]}`;
};

export const formatMetricValue = (value: number) => {
  const maximumFractionDigits = Number.isInteger(value) ? 0 : 2;
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits }).format(value);
};
