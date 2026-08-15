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
    <div className="flex min-h-11 shrink-0 items-center gap-2 border-b border-[#eceef1] bg-[#fbfcfd] px-4 py-1.5">
      <div className="flex shrink-0 items-center gap-1.5 text-[11px] font-medium text-[#475467]">
        <SlidersHorizontal size={13} />
        筛选条件
        {filters.length ? (
          <span className="rounded-full bg-[#eef0f2] px-1.5 py-px text-[9px] font-normal text-[#7a818c]">
            {filters.length}
          </span>
        ) : null}
      </div>

      <div className="mx-1 h-5 w-px shrink-0 bg-[#eceef1]" />

      <div className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto py-0.5">
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
              className="flex h-8 shrink-0 items-center rounded-[7px] border border-[#e7e9ed] bg-white pl-2.5 shadow-[0_1px_2px_rgba(16,24,40,.025)]"
            >
              <span className="mr-1.5 max-w-[120px] truncate text-[10px] font-medium text-[#475467]">
                {filter.name}
              </span>
              <span className="mr-0.5 text-[9px] text-[#a0a6af]">
                {OPERATOR_LABELS[filter.operator]}
              </span>
              {dateFilter ? (
                <DatePicker
                  variant="borderless"
                  size="small"
                  allowClear
                  showTime={dateTime ? { format: 'HH:mm:ss' } : false}
                  format={dateTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'}
                  className="!h-7 w-[158px] text-[10px]"
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
                  className="!h-7 w-[116px] text-[10px]"
                  placeholder="全部"
                  value={current === undefined || current === null ? '' : String(current)}
                  onChange={(event) => onRuntimeValue(filter.id, event.target.value || undefined)}
                />
              )}
            </div>
          );
        }) : (
          <span className="text-[10px] text-[#a0a6af]">还没有筛选条件，可在这里添加全局筛选</span>
        )}
      </div>

      {filters.length ? (
        <Tooltip title="恢复默认筛选">
          <Button
            type="text"
            className="!h-7 !w-7 !min-w-0 !rounded-[6px] !p-0 !text-[#667085]"
            icon={<RefreshCw size={12} />}
            onClick={onReset}
          />
        </Tooltip>
      ) : null}

      {editable ? (
        <Button
          size="small"
          className="!h-7 !rounded-[6px] !border-[#e4e7ec] !px-2.5 !text-[11px]"
          icon={filters.length ? <Settings2 size={12} /> : <Plus size={12} />}
          onClick={onManage}
        >
          {filters.length ? '管理筛选' : '添加筛选'}
        </Button>
      ) : null}
    </div>
  );
}
