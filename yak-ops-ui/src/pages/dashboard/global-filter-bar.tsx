import { Button, DatePicker, Input, Tooltip } from 'antd';
import dayjs from 'dayjs';
import { Plus, RefreshCw, Settings2, SlidersHorizontal } from 'lucide-react';
import { isDateFilter, resolveBindingField } from './filter-utils';
import type {
  AnalysisAsset,
  DashboardGlobalFilter,
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

export function DashboardGlobalFilterBar({
  filters,
  runtimeValues,
  widgets,
  datasets,
  analyses,
  editable,
  onRuntimeValue,
  onReset,
  onManage,
}: {
  filters: DashboardGlobalFilter[];
  runtimeValues: Record<string, Scalar | undefined>;
  widgets: DashboardWidget[];
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  editable: boolean;
  onRuntimeValue: (filterId: string, value: Scalar | undefined) => void;
  onReset: () => void;
  onManage: () => void;
}) {
  if (!filters.length && !editable) return null;

  return (
    <div className="flex min-h-9 shrink-0 items-center gap-2 border-b border-[#e5e7eb] bg-white px-3 py-1">
      <div className="flex shrink-0 items-center gap-1.5 text-[10px] font-medium text-[#667085]">
        <SlidersHorizontal size={12} />
        筛选
      </div>

      <div className="flex min-w-0 flex-1 items-center gap-1.5 overflow-x-auto">
        {filters.length ? filters.map((filter) => {
          const current = own(runtimeValues, filter.id)
            ? runtimeValues[filter.id]
            : filter.defaultValue;
          const dateFilter = isDateFilter(filter, widgets, datasets, analyses);
          const firstBinding = filter.bindings[0];
          const firstField = firstBinding
            ? resolveBindingField(
                firstBinding.widgetId,
                firstBinding.field,
                widgets,
                datasets,
                analyses,
              )
            : undefined;
          const dateTime = firstField?.dataType === 'datetime';
          const dateValue = current === undefined || current === null || current === ''
            ? null
            : dayjs(String(current));

          return (
            <div
              key={filter.id}
              className="flex h-7 shrink-0 items-center rounded-[4px] bg-[#f7f8fa] pl-2"
            >
              <span className="mr-1.5 text-[10px] text-[#667085]">
                {filter.name}
              </span>
              <span className="mr-1 text-[9px] text-[#98a2b3]">
                {OPERATOR_LABELS[filter.operator]}
              </span>
              {dateFilter ? (
                <DatePicker
                  variant="borderless"
                  size="small"
                  allowClear
                  showTime={dateTime ? { format: 'HH:mm:ss' } : false}
                  format={dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'}
                  className="w-[156px] text-[10px]"
                  placeholder="全部"
                  value={dateValue?.isValid() ? dateValue : null}
                  onChange={(value) => onRuntimeValue(
                    filter.id,
                    value
                      ? value.format(dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD')
                      : undefined,
                  )}
                />
              ) : (
                <Input
                  variant="borderless"
                  size="small"
                  allowClear
                  className="w-[112px] text-[10px]"
                  placeholder="全部"
                  value={current === undefined || current === null ? '' : String(current)}
                  onChange={(event) => onRuntimeValue(filter.id, event.target.value || undefined)}
                />
              )}
            </div>
          );
        }) : (
          <span className="text-[10px] text-[#98a2b3]">暂无筛选条件</span>
        )}
      </div>

      {filters.length ? (
        <Tooltip title="恢复默认筛选">
          <Button
            size="small"
            type="text"
            icon={<RefreshCw size={12} />}
            onClick={onReset}
          />
        </Tooltip>
      ) : null}

      {editable ? (
        <Button
          size="small"
          type="text"
          icon={filters.length ? <Settings2 size={12} /> : <Plus size={12} />}
          onClick={onManage}
        >
          {filters.length ? '管理' : '添加筛选'}
        </Button>
      ) : null}
    </div>
  );
}
