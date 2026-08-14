import type { AnalysisSpec } from '@/components/analysis/model';
import { Button, Drawer, Empty, Input, Select, Tooltip } from 'antd';
import { Plus, RefreshCw, SlidersHorizontal, Trash2, Workflow } from 'lucide-react';
import { useMemo, useState } from 'react';
import { FILTER_OPERATOR_OPTIONS } from './helpers';
import type {
  AnalysisAsset,
  DashboardGlobalFilter,
  DashboardInteraction,
  DashboardWidget,
  FilterOperator,
  PublishedDataset,
  Scalar,
} from './model';

const OPERATOR_LABELS: Record<FilterOperator, string> = {
  eq: '等于',
  neq: '不等于',
  contains: '包含',
  gt: '大于',
  gte: '大于等于',
  lt: '小于',
  lte: '小于等于',
};

const own = (value: Record<string, Scalar | undefined>, key: string) =>
  Object.prototype.hasOwnProperty.call(value, key);

const id = (prefix: string) => `${prefix}-${Date.now()}-${Math.round(Math.random() * 10000)}`;

export function DashboardGlobalFilterBar({
  filters,
  interactions,
  widgets,
  analyses,
  datasets,
  runtimeValues,
  preview,
  onFiltersChange,
  onInteractionsChange,
  onRuntimeValue,
  onReset,
}: {
  filters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  widgets: DashboardWidget[];
  analyses: AnalysisAsset[];
  datasets: PublishedDataset[];
  runtimeValues: Record<string, Scalar | undefined>;
  preview: boolean;
  onFiltersChange: (filters: DashboardGlobalFilter[]) => void;
  onInteractionsChange: (interactions: DashboardInteraction[]) => void;
  onRuntimeValue: (filterId: string, value: Scalar | undefined) => void;
  onReset: () => void;
}) {
  const [open, setOpen] = useState(false);
  const analysisMap = useMemo(() => new Map(analyses.map((item) => [item.id, item])), [analyses]);
  const datasetMap = useMemo(() => new Map(datasets.map((item) => [item.id, item])), [datasets]);

  const specForWidget = (widget?: DashboardWidget): AnalysisSpec | undefined => {
    if (!widget) return undefined;
    return widget.analysisId ? analysisMap.get(widget.analysisId) : widget.inlineAnalysis;
  };

  const widgetLabel = (widget: DashboardWidget) => {
    if (widget.analysisId) return analysisMap.get(widget.analysisId)?.name ?? `Analysis #${widget.analysisId}`;
    return widget.title || '未命名图表';
  };

  const fieldOptions = (widgetId?: string) => {
    const widget = widgets.find((item) => item.id === widgetId);
    const spec = specForWidget(widget);
    const dataset = spec ? datasetMap.get(spec.datasetId) : undefined;
    return dataset?.fields.map((field) => ({ label: field.label, value: field.key })) ?? [];
  };

  const sourceDimensionOptions = (widgetId?: string) => {
    const widget = widgets.find((item) => item.id === widgetId);
    const spec = specForWidget(widget);
    const dataset = spec ? datasetMap.get(spec.datasetId) : undefined;
    if (!spec || !dataset || !spec.dimensions.length) return [];
    // AnalysisPreview emits the primary visible category. Multi-dimension linkage can be expanded later.
    const primary = spec.dimensions[0];
    const field = dataset.fields.find((item) => item.key === primary);
    return field ? [{ label: field.label, value: field.key }] : [];
  };

  const widgetOptions = widgets.map((widget) => ({ label: widgetLabel(widget), value: widget.id }));
  const sourceWidgetOptions = widgets
    .filter((widget) => sourceDimensionOptions(widget.id).length > 0)
    .map((widget) => ({ label: widgetLabel(widget), value: widget.id }));

  const replaceFilter = (filterId: string, patch: Partial<DashboardGlobalFilter>) => {
    onFiltersChange(filters.map((filter) => filter.id === filterId ? { ...filter, ...patch } : filter));
  };

  const removeFilter = (filterId: string) => {
    onFiltersChange(filters.filter((filter) => filter.id !== filterId));
    onInteractionsChange(interactions.filter((item) => item.targetFilterId !== filterId));
  };

  const addFilter = () => onFiltersChange([
    ...filters,
    {
      id: id('filter'),
      name: `筛选器 ${filters.length + 1}`,
      operator: 'eq',
      defaultValue: '',
      bindings: [],
    },
  ]);

  const addBinding = (filter: DashboardGlobalFilter) => {
    const used = new Set(filter.bindings.map((item) => item.widgetId));
    const widget = widgets.find((item) => !used.has(item.id) && fieldOptions(item.id).length > 0);
    if (!widget) return;
    const field = fieldOptions(widget.id)[0];
    replaceFilter(filter.id, {
      bindings: [...filter.bindings, { widgetId: widget.id, field: String(field.value) }],
    });
  };

  const updateBinding = (
    filter: DashboardGlobalFilter,
    index: number,
    patch: Partial<DashboardGlobalFilter['bindings'][number]>,
  ) => {
    replaceFilter(filter.id, {
      bindings: filter.bindings.map((binding, bindingIndex) => (
        bindingIndex === index ? { ...binding, ...patch } : binding
      )),
    });
  };

  const addInteraction = () => {
    const sourceWidget = sourceWidgetOptions[0]?.value;
    const targetFilter = filters[0]?.id;
    if (!sourceWidget || !targetFilter) return;
    const sourceField = sourceDimensionOptions(sourceWidget)[0]?.value;
    if (!sourceField) return;
    onInteractionsChange([
      ...interactions,
      {
        id: id('interaction'),
        event: 'select',
        sourceWidgetId: String(sourceWidget),
        sourceField: String(sourceField),
        targetFilterId: targetFilter,
      },
    ]);
  };

  const updateInteraction = (interactionId: string, patch: Partial<DashboardInteraction>) => {
    onInteractionsChange(interactions.map((item) => item.id === interactionId ? { ...item, ...patch } : item));
  };

  if (preview && !filters.length) return null;

  return (
    <>
      <div className="flex min-h-10 shrink-0 items-center gap-2 border-b border-[#e5e7eb] bg-white px-3 py-1.5">
        <div className="flex shrink-0 items-center gap-1.5 text-[11px] font-medium text-[#475467]">
          <SlidersHorizontal size={13} /> 全局筛选
        </div>
        <div className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto">
          {filters.map((filter) => {
            const current = own(runtimeValues, filter.id) ? runtimeValues[filter.id] : filter.defaultValue;
            return (
              <div key={filter.id} className="flex shrink-0 items-center rounded-[4px] border border-[#e5e7eb] bg-[#fafbfc] pl-2">
                <span className="mr-1.5 text-[10px] text-[#667085]">{filter.name}</span>
                <span className="mr-1 text-[9px] text-[#98a2b3]">{OPERATOR_LABELS[filter.operator]}</span>
                <Input
                  variant="borderless"
                  size="small"
                  allowClear
                  className="w-[128px] text-[11px]"
                  placeholder="全部"
                  value={current === undefined || current === null ? '' : String(current)}
                  onChange={(event) => onRuntimeValue(filter.id, event.target.value)}
                />
              </div>
            );
          })}
          {!filters.length ? <span className="text-[10px] text-[#98a2b3]">暂无全局筛选器</span> : null}
        </div>
        {filters.length ? (
          <Tooltip title="恢复默认值">
            <Button size="small" type="text" icon={<RefreshCw size={12} />} onClick={onReset} />
          </Tooltip>
        ) : null}
        {!preview ? (
          <Button size="small" icon={<SlidersHorizontal size={12} />} onClick={() => setOpen(true)}>
            管理筛选与联动
          </Button>
        ) : null}
      </div>

      <Drawer
        title="全局筛选器与组件联动"
        width={520}
        open={open}
        onClose={() => setOpen(false)}
        extra={<span className="text-[10px] text-[#98a2b3]">配置随下一次 Dashboard 保存进入新版本</span>}
      >
        <div className="mb-4 rounded-[5px] bg-[#f7f8fa] px-3 py-2 text-[11px] leading-5 text-[#667085]">
          全局筛选器通过 fieldId 映射到不同 Widget；运行时输入值只影响当前浏览。联动规则会把图表点击的分类值写入目标筛选器，从而驱动所有绑定 Widget 一起刷新。
        </div>

        <div className="mb-2 flex items-center justify-between">
          <div className="text-[12px] font-semibold text-[#344054]">全局筛选器</div>
          <Button size="small" icon={<Plus size={12} />} onClick={addFilter}>新增筛选器</Button>
        </div>

        {!filters.length ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="先创建一个全局筛选器" />
        ) : filters.map((filter) => (
          <div key={filter.id} className="mb-3 rounded-[6px] border border-[#e5e7eb] bg-white p-3">
            <div className="flex items-center gap-2">
              <Input
                size="small"
                value={filter.name}
                placeholder="筛选器名称"
                onChange={(event) => replaceFilter(filter.id, { name: event.target.value })}
              />
              <Select
                size="small"
                className="w-[120px] shrink-0"
                value={filter.operator}
                options={FILTER_OPERATOR_OPTIONS}
                onChange={(operator: FilterOperator) => replaceFilter(filter.id, { operator })}
              />
              <Tooltip title="删除筛选器">
                <Button size="small" type="text" danger icon={<Trash2 size={12} />} onClick={() => removeFilter(filter.id)} />
              </Tooltip>
            </div>
            <div className="mt-2">
              <div className="mb-1 text-[10px] text-[#98a2b3]">默认值（可留空）</div>
              <Input
                size="small"
                allowClear
                value={filter.defaultValue === undefined || filter.defaultValue === null ? '' : String(filter.defaultValue)}
                onChange={(event) => replaceFilter(filter.id, { defaultValue: event.target.value })}
              />
            </div>
            <div className="mb-1 mt-3 flex items-center justify-between">
              <span className="text-[10px] font-medium text-[#667085]">影响组件 / 字段映射</span>
              <Button size="small" type="text" icon={<Plus size={11} />} onClick={() => addBinding(filter)}>添加映射</Button>
            </div>
            <div className="space-y-2">
              {filter.bindings.map((binding, bindingIndex) => {
                const used = new Set(filter.bindings.filter((_, index) => index !== bindingIndex).map((item) => item.widgetId));
                const targetWidgetOptions = widgetOptions.filter((item) => !used.has(String(item.value)));
                return (
                  <div key={`${filter.id}-${bindingIndex}`} className="grid grid-cols-[1fr_1fr_28px] gap-2">
                    <Select
                      size="small"
                      value={binding.widgetId}
                      options={targetWidgetOptions}
                      placeholder="目标组件"
                      onChange={(widgetId: string) => updateBinding(filter, bindingIndex, {
                        widgetId,
                        field: String(fieldOptions(widgetId)[0]?.value ?? ''),
                      })}
                    />
                    <Select
                      size="small"
                      value={binding.field || undefined}
                      options={fieldOptions(binding.widgetId)}
                      placeholder="目标字段"
                      onChange={(field: string) => updateBinding(filter, bindingIndex, { field })}
                    />
                    <Button
                      size="small"
                      type="text"
                      danger
                      icon={<Trash2 size={11} />}
                      onClick={() => replaceFilter(filter.id, {
                        bindings: filter.bindings.filter((_, index) => index !== bindingIndex),
                      })}
                    />
                  </div>
                );
              })}
              {!filter.bindings.length ? (
                <div className="rounded-[4px] border border-dashed border-[#e5e7eb] px-2 py-2 text-[10px] text-[#98a2b3]">
                  尚未绑定 Widget；该筛选器暂时不会影响查询。
                </div>
              ) : null}
            </div>
          </div>
        ))}

        <div className="mb-2 mt-6 flex items-center justify-between border-t border-[#edf0f3] pt-4">
          <div className="flex items-center gap-1.5 text-[12px] font-semibold text-[#344054]"><Workflow size={13} />组件联动</div>
          <Button size="small" icon={<Plus size={12} />} disabled={!filters.length || !sourceWidgetOptions.length} onClick={addInteraction}>
            新增联动
          </Button>
        </div>

        {!interactions.length ? (
          <div className="rounded-[5px] bg-[#f7f8fa] px-3 py-3 text-[10px] leading-5 text-[#98a2b3]">
            配置后，点击来源图表的分类值，会自动写入目标全局筛选器并刷新所有绑定组件。
          </div>
        ) : (
          <div className="space-y-2">
            {interactions.map((interaction) => (
              <div key={interaction.id} className="rounded-[5px] border border-[#e5e7eb] p-2.5">
                <div className="grid grid-cols-[1fr_1fr_30px] gap-2">
                  <Select
                    size="small"
                    value={interaction.sourceWidgetId}
                    options={sourceWidgetOptions}
                    placeholder="来源组件"
                    onChange={(sourceWidgetId: string) => updateInteraction(interaction.id, {
                      sourceWidgetId,
                      sourceField: String(sourceDimensionOptions(sourceWidgetId)[0]?.value ?? ''),
                    })}
                  />
                  <Select
                    size="small"
                    value={interaction.sourceField || undefined}
                    options={sourceDimensionOptions(interaction.sourceWidgetId)}
                    placeholder="点击维度"
                    onChange={(sourceField: string) => updateInteraction(interaction.id, { sourceField })}
                  />
                  <Button
                    size="small"
                    type="text"
                    danger
                    icon={<Trash2 size={11} />}
                    onClick={() => onInteractionsChange(interactions.filter((item) => item.id !== interaction.id))}
                  />
                </div>
                <div className="mt-2 flex items-center gap-2">
                  <span className="text-[10px] text-[#98a2b3]">点击后写入</span>
                  <Select
                    size="small"
                    className="flex-1"
                    value={interaction.targetFilterId}
                    options={filters.map((filter) => ({ label: filter.name, value: filter.id }))}
                    onChange={(targetFilterId: string) => updateInteraction(interaction.id, { targetFilterId })}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </Drawer>
    </>
  );
}
