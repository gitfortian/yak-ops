import { Button, Select, Tooltip } from 'antd';
import { Database, Hash, Layers3, Plus, Sigma } from 'lucide-react';
import { CHART_META, FIELD_DRAG_MIME, findDataset } from './helpers';
import { PUBLISHED_DATASETS } from './mock';
import type { ChartType, DashboardWidget, DatasetField } from './model';

function FieldRow({ field, onAdd }: { field: DatasetField; onAdd: () => void }) {
  const isMetric = field.role === 'metric';
  return (
    <div
      draggable
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'copy';
        event.dataTransfer.setData(FIELD_DRAG_MIME, JSON.stringify({ field: field.key, role: field.role }));
      }}
      className="group flex h-8 cursor-grab items-center gap-2 rounded-[4px] px-2 text-[12px] text-[#475467] hover:bg-[#f5f6f7] active:cursor-grabbing"
      title={field.description}
    >
      <span className={isMetric ? 'text-[#667085]' : 'text-[#98a2b3]'}>
        {isMetric ? <Hash size={13} /> : <Layers3 size={13} />}
      </span>
      <span className="min-w-0 flex-1 truncate">{field.label}</span>
      <span className="text-[10px] text-[#b0b7c3]">{field.dataType}</span>
      <button
        type="button"
        aria-label={`添加${field.label}`}
        onClick={onAdd}
        className="flex h-5 w-5 items-center justify-center border-0 bg-transparent text-[#98a2b3] opacity-0 group-hover:opacity-100 hover:text-[#344054]"
      >
        <Plus size={12} />
      </button>
    </div>
  );
}

export function LeftPanel({
  activeDatasetId,
  selectedWidget,
  onDatasetChange,
  onAddChart,
  onAddField,
}: {
  activeDatasetId: string;
  selectedWidget?: DashboardWidget;
  onDatasetChange: (datasetId: string) => void;
  onAddChart: (type: ChartType) => void;
  onAddField: (field: DatasetField) => void;
}) {
  const dataset = findDataset(activeDatasetId);
  const dimensions = dataset.fields.filter((field) => field.role === 'dimension');
  const metrics = dataset.fields.filter((field) => field.role === 'metric');

  return (
    <aside className="flex w-[276px] shrink-0 flex-col border-r border-[#e5e7eb] bg-white">
      <div className="border-b border-[#edf0f3] p-3">
        <div className="mb-2 flex items-center gap-2 text-[12px] font-semibold text-[#344054]">
          <Database size={14} /> 数据集
        </div>
        <Select
          size="small"
          className="w-full"
          value={dataset.id}
          onChange={onDatasetChange}
          options={PUBLISHED_DATASETS.map((item) => ({ label: item.name, value: item.id }))}
        />
        <div className="mt-2 rounded-[4px] bg-[#f7f8fa] px-2.5 py-2 text-[10px] leading-4 text-[#667085]">
          <div className="flex items-center justify-between gap-2">
            <span className="truncate">来源：{dataset.sourceTaskName}</span>
            <span className="shrink-0 text-[#98a2b3]">已发布</span>
          </div>
          <div className="mt-0.5 text-[#98a2b3]">更新于 {dataset.updatedAt}</div>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
        <div className="mb-1 mt-1 flex items-center justify-between px-1 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
          <span>维度</span><span>{dimensions.length}</span>
        </div>
        {dimensions.map((field) => <FieldRow key={field.key} field={field} onAdd={() => onAddField(field)} />)}

        <div className="mb-1 mt-4 flex items-center justify-between px-1 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
          <span>指标</span><span>{metrics.length}</span>
        </div>
        {metrics.map((field) => <FieldRow key={field.key} field={field} onAdd={() => onAddField(field)} />)}

        <div className="mb-2 mt-5 border-t border-[#edf0f3] pt-4 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
          图表
        </div>
        <div className="grid grid-cols-2 gap-2">
          {(Object.keys(CHART_META) as ChartType[]).map((type) => (
            <button
              key={type}
              type="button"
              onClick={() => onAddChart(type)}
              className="flex min-h-[62px] flex-col items-start justify-center rounded-[5px] border border-[#e5e7eb] bg-white px-3 text-left hover:border-[#cbd2dc] hover:bg-[#fafbfc]"
            >
              <span className="mb-1 text-[#475467]">{CHART_META[type].icon}</span>
              <span className="text-[11px] font-medium text-[#344054]">{CHART_META[type].label}</span>
            </button>
          ))}
        </div>
        {!selectedWidget ? (
          <div className="mt-4 rounded-[4px] border border-dashed border-[#d8dde6] px-3 py-2 text-[10px] leading-4 text-[#98a2b3]">
            选择画布中的图表后，可点击字段快速加入；也可以把字段拖到右侧配置槽位。
          </div>
        ) : null}
      </div>
    </aside>
  );
}
