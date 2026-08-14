import { ConfigPanel } from './config-panel';
import { findDataset } from './helpers';
import type {
  Aggregation,
  DashboardWidget,
  FilterOperator,
  MetricBinding,
  PublishedDataset,
  SortDirection,
} from './model';

export function SelectedConfig({
  widget,
  datasets,
  update,
  changeDataset,
  close,
}: {
  widget: DashboardWidget;
  datasets: PublishedDataset[];
  update: (patch: Partial<DashboardWidget>) => void;
  changeDataset: (datasetId: string) => void;
  close: () => void;
}) {
  const dataset = findDataset(datasets, widget.datasetId);
  if (!dataset) return null;
  const dimensionOptions = dataset.fields.filter((field) => field.role === 'dimension').map((field) => ({ label: field.label, value: field.key }));
  const metricOptions = dataset.fields.filter((field) => field.role === 'metric').map((field) => ({ label: field.label, value: field.key }));
  const filterOptions = dataset.fields.map((field) => ({ label: field.label, value: field.key }));
  const selectedFields = new Set([
    ...widget.dimensions,
    ...widget.metrics.map((metric) => metric.field),
  ]);
  const sortOptions = dataset.fields
    .filter((field) => selectedFields.has(field.key))
    .map((field) => ({ label: field.label, value: field.key }));
  const metricLabels = Object.fromEntries(dataset.fields.map((field) => [field.key, field.label]));
  const filter = widget.filters[0];

  return (
    <ConfigPanel
      widget={widget}
      datasetOptions={datasets.map((item) => ({ label: item.name, value: item.id }))}
      dimensionOptions={dimensionOptions}
      metricOptions={metricOptions}
      sortOptions={sortOptions}
      filterOptions={filterOptions}
      metricLabels={metricLabels}
      onWidget={update}
      onDataset={changeDataset}
      onDimensions={(dimensions) => {
        const nextSort = widget.sort && !dimensions.includes(widget.sort.field)
          && !widget.metrics.some((metric) => metric.field === widget.sort?.field)
          ? undefined
          : widget.sort;
        update({ dimensions, sort: nextSort });
      }}
      onMetrics={(fields) => {
        const previous = new Map(widget.metrics.map((metric) => [metric.field, metric]));
        const metrics: MetricBinding[] = fields.map((field) => previous.get(field) ?? { field, aggregation: 'SUM' });
        const nextSort = widget.sort && !widget.dimensions.includes(widget.sort.field)
          && !metrics.some((metric) => metric.field === widget.sort?.field)
          ? undefined
          : widget.sort;
        update({ metrics, sort: nextSort });
      }}
      onAggregation={(field: string, aggregation: Aggregation) => update({
        metrics: widget.metrics.map((metric) => metric.field === field ? { ...metric, aggregation } : metric),
      })}
      onSortField={(field?: string) => update({
        sort: field ? { field, direction: widget.sort?.direction ?? 'asc' } : undefined,
      })}
      onSortDirection={(direction: SortDirection) => widget.sort && update({ sort: { ...widget.sort, direction } })}
      onFilterField={(field?: string) => update({
        filters: field ? [{
          id: filter?.id ?? 'filter-main',
          field,
          operator: filter?.operator ?? 'eq',
          value: filter?.value ?? '',
        }] : [],
      })}
      onFilterOperator={(operator: FilterOperator) => filter && update({ filters: [{ ...filter, operator }] })}
      onFilterValue={(value) => filter && update({ filters: [{ ...filter, value }] })}
      onClose={close}
    />
  );
}
