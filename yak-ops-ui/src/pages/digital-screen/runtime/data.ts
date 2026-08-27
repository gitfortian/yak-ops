import type {
  ScreenComponent,
  ScreenComponentData,
} from '@/components/screen-engine';
import {
  queryDataset,
  type Aggregation,
  type DatasetQueryPayload,
  type DatasetQueryResult,
  type PublishedDataset,
  type Scalar,
} from '@/services/dataset';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';
import { screenRuntimeComponentRegistry } from './component-registry';

export const SCREEN_AGGREGATION_LABELS: Record<Aggregation, string> = {
  SUM: '求和',
  AVG: '平均',
  COUNT: '计数',
  COUNT_DISTINCT: '去重计数',
  MAX: '最大值',
  MIN: '最小值',
};

export const isBindableScreenComponent = (component?: ScreenComponent) => Boolean(
  component
  && component.type !== 'text'
  && component.type !== 'map'
  && component.type !== 'ticker',
);

export const canQueryScreenComponent = (
  component: ScreenComponent,
  binding?: DigitalScreenComponentBinding,
) => {
  if (!binding?.datasetId) return false;
  if (component.type === 'text' || component.type === 'map' || component.type === 'ticker') return false;
  if (component.type === 'metric') return binding.metrics.length === 1;
  if (component.type === 'table') return binding.dimensions.length > 0 || binding.metrics.length > 0;
  return binding.dimensions.length > 0 && binding.metrics.length > 0;
};

export const buildScreenDatasetQueryPayload = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
): DatasetQueryPayload => ({
  dimensions: component.type === 'metric' ? [] : binding.dimensions,
  metrics: binding.metrics.map((metric) => ({
    fieldId: metric.field,
    aggregation: metric.aggregation,
  })),
  filters: [],
  sorts: [],
  limit: component.type === 'table' ? 100 : 200,
  timeoutSeconds: 30,
});

const bindingIndex = (
  result: DatasetQueryResult,
  fieldId: string,
  aggregation?: Aggregation,
) => result.bindings.findIndex((item) => (
  item.fieldId === fieldId
  && (aggregation ? item.aggregation === aggregation : !item.aggregation)
));

const getCell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation?: Aggregation,
) => {
  const index = bindingIndex(result, fieldId, aggregation);
  return index >= 0 ? row[index] : null;
};

const getNumericCell = (
  result: DatasetQueryResult,
  row: Scalar[],
  fieldId: string,
  aggregation: Aggregation,
) => {
  const value = Number(getCell(result, row, fieldId, aggregation) ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const getFieldLabel = (dataset: PublishedDataset, fieldId: string) => (
  dataset.fields.find((field) => field.key === fieldId)?.label || fieldId
);

const getMetricLabel = (
  dataset: PublishedDataset,
  metric: DigitalScreenComponentBinding['metrics'][number],
) => `${getFieldLabel(dataset, metric.field)} · ${SCREEN_AGGREGATION_LABELS[metric.aggregation]}`;

const getRowLabel = (
  dataset: PublishedDataset,
  result: DatasetQueryResult,
  row: Scalar[],
  dimensions: string[],
) => dimensions.map((fieldId) => {
  const value = getCell(result, row, fieldId);
  return value == null ? getFieldLabel(dataset, fieldId) : String(value);
}).join(' / ');

screenRuntimeComponentRegistry.register({
  type: 'bar',
  adaptData: ({ binding, dataset, result }) => ({
    categories: result.rows.map((row) => getRowLabel(dataset, result, row, binding.dimensions)),
    series: binding.metrics.map((metric) => ({
      name: getMetricLabel(dataset, metric),
      values: result.rows.map((row) => getNumericCell(
        result,
        row,
        metric.field,
        metric.aggregation,
      )),
    })),
  }),
});

export const toScreenComponentData = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
): ScreenComponentData | undefined => {
  const plugin = screenRuntimeComponentRegistry.get(component.type);
  if (plugin?.adaptData) {
    return plugin.adaptData({ component, binding, dataset, result });
  }

  if (component.type === 'metric') {
    const metric = binding.metrics[0];
    if (!metric) return undefined;
    const row = result.rows[0];
    return {
      value: row ? getNumericCell(result, row, metric.field, metric.aggregation) : 0,
      trendLabel: `${dataset.name} · DV${result.datasetVersionNo}`,
    };
  }

  if (component.type === 'line') {
    return {
      categories: result.rows.map((row) => getRowLabel(dataset, result, row, binding.dimensions)),
      series: binding.metrics.map((metric) => ({
        name: getMetricLabel(dataset, metric),
        values: result.rows.map((row) => getNumericCell(
          result,
          row,
          metric.field,
          metric.aggregation,
        )),
      })),
    };
  }

  if (component.type === 'pie') {
    const metric = binding.metrics[0];
    if (!metric) return undefined;
    return {
      items: result.rows.map((row) => ({
        name: getRowLabel(dataset, result, row, binding.dimensions),
        value: getNumericCell(result, row, metric.field, metric.aggregation),
      })),
    };
  }

  if (component.type === 'table') {
    const dimensionColumns = binding.dimensions.map((fieldId) => ({
      key: `dimension:${fieldId}`,
      title: getFieldLabel(dataset, fieldId),
      align: 'left' as const,
    }));
    const metricColumns = binding.metrics.map((metric) => ({
      key: `metric:${metric.field}:${metric.aggregation}`,
      title: getMetricLabel(dataset, metric),
      align: 'right' as const,
    }));
    return {
      columns: [...dimensionColumns, ...metricColumns],
      rows: result.rows.map((row) => {
        const record: Record<string, Scalar> = {};
        binding.dimensions.forEach((fieldId) => {
          record[`dimension:${fieldId}`] = getCell(result, row, fieldId);
        });
        binding.metrics.forEach((metric) => {
          record[`metric:${metric.field}:${metric.aggregation}`] = getCell(
            result,
            row,
            metric.field,
            metric.aggregation,
          );
        });
        return record;
      }),
    };
  }

  return undefined;
};

export const queryScreenComponentData = async (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
) => {
  if (!canQueryScreenComponent(component, binding)) return undefined;
  const result = await queryDataset(
    dataset.id,
    buildScreenDatasetQueryPayload(component, binding),
  );
  return toScreenComponentData(component, binding, dataset, result);
};
