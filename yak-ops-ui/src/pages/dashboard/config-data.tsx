import { Select } from 'antd';
import type { DashboardWidget } from './model';

export function ConfigData({
  widget,
  dimensionOptions,
  metricOptions,
  onDimensions,
  onMetrics,
}: {
  widget: DashboardWidget;
  dimensionOptions: Array<{ label: string; value: string }>;
  metricOptions: Array<{ label: string; value: string }>;
  onDimensions: (value: string[]) => void;
  onMetrics: (value: string[]) => void;
}) {
  const dimensionLimit = widget.type === 'table' ? 3 : 1;
  const metricLimit = ['bar', 'line', 'table'].includes(widget.type) ? 3 : 1;
  return (
    <div className="space-y-3">
      {widget.type !== 'metric' ? (
        <div>
          <div className="mb-1 text-[11px] text-[#667085]">维度</div>
          <Select mode="multiple" size="small" maxCount={dimensionLimit} className="w-full" value={widget.dimensions} options={dimensionOptions} onChange={onDimensions} />
        </div>
      ) : null}
      <div>
        <div className="mb-1 text-[11px] text-[#667085]">指标</div>
        <Select mode="multiple" size="small" maxCount={metricLimit} className="w-full" value={widget.metrics.map((item) => item.field)} options={metricOptions} onChange={onMetrics} />
      </div>
    </div>
  );
}
