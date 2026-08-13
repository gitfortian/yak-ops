import { Input, Select } from 'antd';
import { FILTER_OPERATOR_OPTIONS } from './helpers';
import type { FilterOperator, SortDirection } from './model';

export function QueryControls({
  fieldOptions,
  sortField,
  sortDirection,
  filterField,
  filterOperator,
  filterValue,
  onSortField,
  onSortDirection,
  onFilterField,
  onFilterOperator,
  onFilterValue,
}: {
  fieldOptions: Array<{ label: string; value: string }>;
  sortField?: string;
  sortDirection: SortDirection;
  filterField?: string;
  filterOperator: FilterOperator;
  filterValue: string;
  onSortField: (field?: string) => void;
  onSortDirection: (direction: SortDirection) => void;
  onFilterField: (field?: string) => void;
  onFilterOperator: (operator: FilterOperator) => void;
  onFilterValue: (value: string) => void;
}) {
  return (
    <div className="mt-4 space-y-4 border-t border-[#edf0f3] pt-4">
      <div>
        <div className="mb-1 text-[11px] text-[#667085]">排序</div>
        <div className="flex gap-2">
          <Select allowClear size="small" className="min-w-0 flex-1" placeholder="排序字段" value={sortField} options={fieldOptions} onChange={onSortField} />
          <Select size="small" className="w-[76px]" disabled={!sortField} value={sortDirection} options={[{ label: '升序', value: 'asc' }, { label: '降序', value: 'desc' }]} onChange={onSortDirection} />
        </div>
      </div>
      <div>
        <div className="mb-1 text-[11px] text-[#667085]">过滤</div>
        <div className="grid grid-cols-[1fr_90px] gap-2">
          <Select allowClear size="small" placeholder="字段" value={filterField} options={fieldOptions} onChange={onFilterField} />
          <Select size="small" disabled={!filterField} value={filterOperator} options={FILTER_OPERATOR_OPTIONS} onChange={onFilterOperator} />
        </div>
        <Input size="small" className="mt-2" disabled={!filterField} placeholder="过滤值" value={filterValue} onChange={(event) => onFilterValue(event.target.value)} />
      </div>
    </div>
  );
}
