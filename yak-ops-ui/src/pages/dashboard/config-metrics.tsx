import { Select } from 'antd';
import { AGGREGATION_OPTIONS } from './helpers';
import type { Aggregation, MetricBinding } from './model';

export function MetricAggregations({
  metrics,
  labels,
  onChange,
}: {
  metrics: MetricBinding[];
  labels: Record<string, string>;
  onChange: (field: string, aggregation: Aggregation) => void;
}) {
  if (!metrics.length) return null;
  return (
    <div className="mt-2 space-y-1.5">
      {metrics.map((metric) => (
        <div key={metric.field} className="flex items-center gap-2 rounded-[4px] bg-[#f7f8fa] px-2 py-1">
          <span className="min-w-0 flex-1 truncate text-[10px] text-[#475467]">
            {labels[metric.field] ?? metric.field}
          </span>
          <Select
            size="small"
            variant="borderless"
            className="w-[76px]"
            value={metric.aggregation}
            options={AGGREGATION_OPTIONS}
            onChange={(aggregation: Aggregation) => onChange(metric.field, aggregation)}
          />
        </div>
      ))}
    </div>
  );
}
