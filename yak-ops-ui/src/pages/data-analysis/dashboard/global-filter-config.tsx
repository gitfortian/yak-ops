import { Button, DatePicker, Drawer, Empty, Input, Select, Switch, Tooltip, message } from 'antd';
import dayjs from 'dayjs';
import { CalendarDays, Plus, SlidersHorizontal, Trash2 } from 'lucide-react';
import { isDateFieldType, isDateFilter, resolveWidgetDataset } from './filter-utils';
import type {
  AnalysisAsset,
  DashboardGlobalFilter,
  DashboardWidget,
  FilterOperator,
  PublishedDataset,
} from './model';

const TEXT_OPERATORS: Array<{ label: string; value: FilterOperator }> = [
  { label: '等于', value: 'eq' },
  { label: '不等于', value: 'neq' },
  { label: '包含', value: 'contains' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'gte' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'lte' },
];

const DATE_OPERATORS: Array<{ label: string; value: FilterOperator }> = [
  { label: '等于', value: 'eq' },
  { label: '不等于', value: 'neq' },
  { label: '晚于', value: 'gt' },
  { label: '晚于或等于', value: 'gte' },
  { label: '早于', value: 'lt' },
  { label: '早于或等于', value: 'lte' },
];

const createId = (prefix: string) => `${prefix}-${Date.now()}-${Math.round(Math.random() * 1000)}`;

export function DashboardGlobalFilterConfig({
  open,
  filters,
  widgets,
  datasets,
  analyses,
  onChange,
  onClose,
}: {
  open: boolean;
  filters: DashboardGlobalFilter[];
  widgets: DashboardWidget[];
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  onChange: (filters: DashboardGlobalFilter[]) => void;
  onClose: () => void;
}) {
  const widgetContext = widgets.map((widget) => ({
    widget,
    dataset: resolveWidgetDataset(widget, datasets, analyses),
  }));

  const patchFilter = (filterId: string, patch: Partial<DashboardGlobalFilter>) => {
    onChange(filters.map((filter) => filter.id === filterId ? { ...filter, ...patch } : filter));
  };

  const removeFilter = (filterId: string) => {
    onChange(filters.filter((filter) => filter.id !== filterId));
  };

  const addTextFilter = () => {
    const context = widgetContext.find((item) => item.dataset?.fields.length);
    const field = context?.dataset?.fields[0];
    const filter: DashboardGlobalFilter = {
      id: createId('filter'),
      name: field?.label || '筛选条件',
      operator: 'eq',
      bindings: context && field ? [{ widgetId: context.widget.id, field: field.key }] : [],
    };
    onChange([...filters, filter]);
  };

  const addDateFilter = () => {
    const context = widgetContext.find((item) =>
      item.dataset?.fields.some((field) => isDateFieldType(field.dataType)));
    const field = context?.dataset?.fields.find((item) => isDateFieldType(item.dataType));
    if (!context || !field) {
      message.info('当前仪表盘没有可用于日期筛选的 date / datetime 字段');
      return;
    }
    const filter: DashboardGlobalFilter = {
      id: createId('date-filter'),
      name: field.label || '日期',
      operator: 'eq',
      bindings: [{ widgetId: context.widget.id, field: field.key }],
    };
    onChange([...filters, filter]);
  };

  return (
    <Drawer
      title={(
        <div>
          <div className="text-[13px] font-semibold text-[#344054]">全局筛选</div>
          <div className="mt-0.5 text-[10px] font-normal text-[#98a2b3]">
            一个筛选器可以映射到多个图表的不同字段
          </div>
        </div>
      )}
      width={500}
      open={open}
      onClose={onClose}
      extra={(
        <div className="flex items-center gap-1.5">
          <Button size="small" icon={<Plus size={12} />} onClick={addTextFilter}>
            筛选
          </Button>
          <Button size="small" icon={<CalendarDays size={12} />} onClick={addDateFilter}>
            日期
          </Button>
        </div>
      )}
    >
      {!filters.length ? (
        <div className="flex min-h-[360px] items-center justify-center">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={(
              <div className="text-[11px] text-[#98a2b3]">
                添加一个全局筛选器，再选择它要作用的图表字段
              </div>
            )}
          />
        </div>
      ) : (
        <div className="space-y-3">
          {filters.map((filter, index) => {
            const dateFilter = isDateFilter(filter, widgets, datasets, analyses);
            const firstBinding = filter.bindings[0];
            const firstContext = firstBinding
              ? widgetContext.find((item) => item.widget.id === firstBinding.widgetId)
              : undefined;
            const firstField = firstContext?.dataset?.fields.find((field) => field.key === firstBinding?.field);
            const dateTime = firstField?.dataType === 'datetime';
            const defaultDate = filter.defaultValue === undefined || filter.defaultValue === null
              ? null
              : dayjs(String(filter.defaultValue));

            return (
              <div key={filter.id} className="rounded-[8px] border border-[#e5e7eb] bg-white p-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[6px] bg-[#f5f6f7] text-[#667085]">
                      {dateFilter ? <CalendarDays size={13} /> : <SlidersHorizontal size={13} />}
                    </div>
                    <div className="min-w-0">
                      <div className="truncate text-[11px] font-medium text-[#344054]">
                        {filter.name || `筛选器 ${index + 1}`}
                      </div>
                      <div className="mt-0.5 text-[9px] text-[#98a2b3]">
                        {dateFilter ? '日期筛选' : '字段筛选'} · 已作用 {filter.bindings.length} 个图表
                      </div>
                    </div>
                  </div>
                  <Tooltip title="删除筛选器">
                    <Button
                      size="small"
                      type="text"
                      danger
                      icon={<Trash2 size={12} />}
                      onClick={() => removeFilter(filter.id)}
                    />
                  </Tooltip>
                </div>

                <div className="mt-3 grid grid-cols-[1fr_132px] gap-2">
                  <div>
                    <div className="mb-1 text-[10px] text-[#667085]">名称</div>
                    <Input
                      size="small"
                      value={filter.name}
                      maxLength={200}
                      onChange={(event) => patchFilter(filter.id, { name: event.target.value })}
                    />
                  </div>
                  <div>
                    <div className="mb-1 text-[10px] text-[#667085]">条件</div>
                    <Select
                      size="small"
                      className="w-full"
                      value={filter.operator}
                      options={dateFilter ? DATE_OPERATORS : TEXT_OPERATORS}
                      onChange={(operator) => patchFilter(filter.id, { operator })}
                    />
                  </div>
                </div>

                <div className="mt-2">
                  <div className="mb-1 text-[10px] text-[#667085]">默认值</div>
                  {dateFilter ? (
                    <DatePicker
                      size="small"
                      allowClear
                      showTime={dateTime ? { format: 'HH:mm:ss' } : false}
                      format={dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'}
                      value={defaultDate?.isValid() ? defaultDate : null}
                      className="w-full"
                      placeholder="默认不筛选"
                      onChange={(value) => patchFilter(filter.id, {
                        defaultValue: value
                          ? value.format(dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD')
                          : undefined,
                      })}
                    />
                  ) : (
                    <Input
                      size="small"
                      allowClear
                      placeholder="默认不筛选"
                      value={filter.defaultValue === undefined || filter.defaultValue === null
                        ? ''
                        : String(filter.defaultValue)}
                      onChange={(event) => patchFilter(filter.id, {
                        defaultValue: event.target.value || undefined,
                      })}
                    />
                  )}
                </div>

                <div className="mt-3 border-t border-[#edf0f3] pt-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-[10px] font-medium text-[#667085]">作用图表</span>
                    <span className="text-[9px] text-[#98a2b3]">每个图表显式映射一个字段</span>
                  </div>

                  {!widgetContext.length ? (
                    <div className="rounded-[5px] bg-[#fafbfc] px-2.5 py-2 text-[10px] text-[#98a2b3]">
                      先添加图表，再配置筛选字段映射。
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      {widgetContext.map(({ widget, dataset }) => {
                        const binding = filter.bindings.find((item) => item.widgetId === widget.id);
                        const fieldOptions = (dataset?.fields || [])
                          .filter((field) => !dateFilter || isDateFieldType(field.dataType))
                          .map((field) => ({
                            label: `${field.label} · ${field.dataType}`,
                            value: field.key,
                          }));
                        const enabled = Boolean(binding);

                        return (
                          <div
                            key={widget.id}
                            className="flex min-h-9 items-center gap-2 rounded-[5px] bg-[#fafbfc] px-2"
                          >
                            <Switch
                              size="small"
                              checked={enabled}
                              disabled={!fieldOptions.length}
                              onChange={(checked) => {
                                if (!checked) {
                                  patchFilter(filter.id, {
                                    bindings: filter.bindings.filter((item) => item.widgetId !== widget.id),
                                  });
                                  return;
                                }
                                const first = fieldOptions[0]?.value;
                                if (!first) return;
                                patchFilter(filter.id, {
                                  bindings: [
                                    ...filter.bindings.filter((item) => item.widgetId !== widget.id),
                                    { widgetId: widget.id, field: first },
                                  ],
                                });
                              }}
                            />
                            <div className="min-w-0 flex-1 truncate text-[10px] text-[#475467]">
                              {widget.title || '未命名图表'}
                            </div>
                            <Select
                              size="small"
                              className="w-[190px]"
                              placeholder={fieldOptions.length ? '选择字段' : dateFilter ? '无日期字段' : '无可用字段'}
                              disabled={!enabled || !fieldOptions.length}
                              value={binding?.field}
                              options={fieldOptions}
                              onChange={(field) => patchFilter(filter.id, {
                                bindings: filter.bindings.map((item) =>
                                  item.widgetId === widget.id ? { ...item, field } : item),
                              })}
                            />
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </Drawer>
  );
}
