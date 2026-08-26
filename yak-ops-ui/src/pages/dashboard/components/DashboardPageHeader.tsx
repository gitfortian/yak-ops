import { YakButton, YakTab } from '@/components/ui';
import { Input, Select } from 'antd';
import {
  CalendarDays,
  ChevronDown,
  Plus,
  RefreshCw,
  Search,
} from 'lucide-react';

import {
  DASHBOARD_STATUS_FILTERS,
  DASHBOARD_TIME_RANGE_OPTIONS,
} from '../constants';
import type {
  DashboardLifecycleCounts,
  DashboardStatusFilter,
  DashboardTimeRange,
} from '../types';

interface DashboardPageHeaderProps {
  total: number;
  lifecycleCounts: DashboardLifecycleCounts;
  status: DashboardStatusFilter;
  timeRange: DashboardTimeRange;
  keyword: string;
  loading: boolean;
  onStatusChange: (status: DashboardStatusFilter) => void;
  onTimeRangeChange: (timeRange: DashboardTimeRange) => void;
  onKeywordChange: (keyword: string) => void;
  onRefresh: () => void;
  onCreate: () => void;
}

const DashboardPageHeader = ({
  total,
  lifecycleCounts,
  status,
  timeRange,
  keyword,
  loading,
  onStatusChange,
  onTimeRangeChange,
  onKeywordChange,
  onRefresh,
  onCreate,
}: DashboardPageHeaderProps) => {
  const statusCount = (key: DashboardStatusFilter) =>
    key === 'all' ? total : lifecycleCounts[key];

  return (
    <header>
      <h1 className="m-0 text-[18px] font-semibold leading-7 text-[#161823]">
        仪表盘管理
      </h1>

      <div className="mt-2 flex min-h-[48px] items-center justify-between gap-5 max-xl:flex-col max-xl:items-stretch">
        <div className="flex shrink-0 items-center gap-2">
          <span className="inline-flex h-9 items-center rounded-[8px] bg-[#f0f1f2] px-4 text-[13px] font-medium text-[#161823]">
            仪表盘 ({total})
          </span>
        </div>

        <div className="flex min-w-0 flex-1 items-center justify-end gap-2 max-xl:flex-wrap">
          <YakTab
            size="small"
            activeKey={status}
            className="mr-1 !mb-0 [&_.ant-tabs-nav]:!mb-0"
            items={DASHBOARD_STATUS_FILTERS.map((item) => ({
              key: item.key,
              label: (
                <span>
                  {item.label}
                  {item.key !== 'all' && statusCount(item.key) > 0 ? (
                    <span className="ml-1 text-[10px] font-normal text-[#b0b5bd]">
                      {statusCount(item.key)}
                    </span>
                  ) : null}
                </span>
              ),
            }))}
            onChange={(key) => onStatusChange(key as DashboardStatusFilter)}
          />

          <Select<DashboardTimeRange>
            value={timeRange}
            onChange={onTimeRangeChange}
            suffixIcon={<ChevronDown size={14} />}
            options={DASHBOARD_TIME_RANGE_OPTIONS}
            className="w-[122px]"
            size="middle"
            variant="filled"
            prefix={
              <CalendarDays size={13} className="text-[#667085]" />
            }
          />

          <Input
            allowClear
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            prefix={<Search size={15} className="text-[#8a9099]" />}
            placeholder="搜索仪表盘"
            className="w-[190px]"
            size="middle"
            variant="filled"
          />

          <YakButton
            size="middle"
            type="text"
            icon={<RefreshCw size={14} />}
            loading={loading}
            onClick={onRefresh}
            className="!bg-[#f5f6f7]"
          >
            刷新
          </YakButton>

          <YakButton
            type="primary"
            size="middle"
            icon={<Plus size={15} />}
            onClick={onCreate}
          >
            新建仪表盘
          </YakButton>
        </div>
      </div>
    </header>
  );
};

export default DashboardPageHeader;
