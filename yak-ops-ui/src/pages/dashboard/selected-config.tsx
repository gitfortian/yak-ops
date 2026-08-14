import { ConfigPanel } from './config-panel';
import { findDataset } from './helpers';
import { PUBLISHED_DATASETS } from './mock';
import type { Aggregation, DashboardWidget, FilterOperator, MetricBinding, SortDirection } from './model';

export function SelectedConfig({
  widget,
  update,
  changeDataset,
  close,
}: {
  widget: DashboardWidget;
  update: (patch: Partial<DashboardWidget>) => void;
  changeDataset: (datasetId: string) => void;
  close: () => void;
}) {
  const dataset = findDataset(widget.datasetId);
  const dimensionOptions = dataset.fields.filter((field) => field.role === 'dimension').map((field) => ({ label: field.label, value: field.key }));
  const metricOptions = dataset.fields.filter((field) => field.role === 'metric').map((field) => ({ label: field.label, value: field.key }));
  const fieldOptions = dataset.fields.map((field) => ({ label: field.label, value: field.key }));
  const metricLabels = Object.fromEntries(dataset.fields.map((field) => [field.key, field.label]));
  const filter = widget.filters[0];

  return (
    <ConfigPanel
      widget={widget}
      datasetOptions={PUBLISHED_DATASETS.map((item) => ({ label: item.name, value: item.id }))}
      dimensionOptions={dimensionOptions}
      metricOptions={metricOptions}
      fieldOptions={fieldOptions}
      metricLabels={metricLabels}
      onWidget={update}
      onDataset={changeDataset}
      onDimensions={(dimensions) => update({ dimensions })}
      onMetrics={(fields) => {
        const previous = new Map(widget.metrics.map((metric) => [metric.field, metric]));
        const metrics: MetricBinding[] = fields.map((field) => previous.get(field) ?? { field, aggregation: 'SUM' });
        update({ metrics });
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
