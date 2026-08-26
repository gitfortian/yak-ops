import { YakButton, YakTab } from '@/components/ui';
import { FilterOutlined, SearchOutlined } from '@ant-design/icons';
import { DatePicker, Input, Popover, Select } from 'antd';
import { useState } from 'react';

import { OFFLINE_SYNC_STATUS_TABS } from '../constants';
import type {
  OfflineSyncConnectorOption,
  OfflineSyncSearchField,
  OfflineSyncSearchState,
} from '../types';

const { RangePicker } = DatePicker;

interface OfflineSyncFilterBarProps {
  filterDraft: OfflineSyncSearchState;
  currentStatus: string;
  connectorOptions: OfflineSyncConnectorOption[];
  advancedFilterCount: number;
  onDraftChange: (
    field: OfflineSyncSearchField,
    value: OfflineSyncSearchState[OfflineSyncSearchField],
  ) => void;
  onQuickFilterChange: (
    field: OfflineSyncSearchField,
    value: OfflineSyncSearchState[OfflineSyncSearchField],
  ) => void;
  onStatusChange: (value: string) => void;
  onSearch: () => void;
  onReset: () => void;
  onAdvancedReset: () => void;
}

const OfflineSyncFilterBar = ({
  filterDraft,
  currentStatus,
  connectorOptions,
  advancedFilterCount,
  onDraftChange,
  onQuickFilterChange,
  onStatusChange,
  onSearch,
  onReset,
  onAdvancedReset,
}: OfflineSyncFilterBarProps) => {
  const [advancedOpen, setAdvancedOpen] = useState(false);

  const applyAdvancedFilters = () => {
    onSearch();
    setAdvancedOpen(false);
  };

  return (
    <div className="border-b border-[#f0f0f0]">
      <div className="flex min-h-[54px] items-center justify-between gap-4 py-2">
        <div className="h-9 shrink-0">
          <YakTab
            size="small"
            activeKey={currentStatus}
            items={OFFLINE_SYNC_STATUS_TABS.map((item) => ({
              key: item.value,
              label: item.label,
            }))}
            onChange={onStatusChange}
          />
        </div>

        <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
          <Input
            allowClear
            variant="filled"
            value={filterDraft.jobName}
            prefix={<SearchOutlined className="text-[#98a2b3]" />}
            placeholder="搜索任务名称"
            className="!h-9 !w-[220px] !min-w-[180px]"
            onChange={(event) =>
              onDraftChange('jobName', event.target.value || undefined)
            }
            onPressEnter={onSearch}
          />

          <Select
            allowClear
            showSearch
            variant="filled"
            value={filterDraft.sourceType}
            options={connectorOptions}
            placeholder="来源类型"
            className="!h-9 !w-[150px] !min-w-[140px]"
            optionFilterProp="value"
            onChange={(value) => onQuickFilterChange('sourceType', value)}
          />

          <RangePicker
            allowClear
            variant="filled"
            value={filterDraft.createTime as never}
            format="YYYY-MM-DD"
            placeholder={['开始日期', '结束日期']}
            className="!h-9 !w-[250px] !min-w-[230px]"
            onChange={(value) =>
              onQuickFilterChange(
                'createTime',
                (value || undefined) as OfflineSyncSearchState['createTime'],
              )
            }
          />

          <YakButton className="!h-9 !px-4" onClick={onSearch}>
            查询
          </YakButton>

          <YakButton type="text" className="!h-9 !px-2" onClick={onReset}>
            重置
          </YakButton>

          <Popover
            trigger="click"
            placement="bottomRight"
            open={advancedOpen}
            onOpenChange={setAdvancedOpen}
            overlayClassName="sync-task-advanced-filter"
            content={
              <div className="w-[430px]">
                <div className="mb-4">
                  <div className="text-[14px] font-semibold text-[#101828]">
                    高级搜索
                  </div>
                  <div className="mt-1 text-[12px] text-[#98a2b3]">
                    按任务标识、目标类型和同步表信息进一步筛选
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-x-3 gap-y-4">
                  <div>
                    <div className="mb-1.5 text-[12px] text-[#667085]">
                      任务 ID
                    </div>
                    <Input
                      allowClear
                      variant="filled"
                      value={filterDraft.id}
                      placeholder="请输入任务 ID"
                      onChange={(event) =>
                        onDraftChange('id', event.target.value || undefined)
                      }
                      onPressEnter={applyAdvancedFilters}
                    />
                  </div>

                  <div>
                    <div className="mb-1.5 text-[12px] text-[#667085]">
                      目标类型
                    </div>
                    <Select
                      allowClear
                      showSearch
                      variant="filled"
                      value={filterDraft.sinkType}
                      options={connectorOptions}
                      placeholder="请选择目标类型"
                      optionFilterProp="value"
                      className="w-full"
                      onChange={(value) => onDraftChange('sinkType', value)}
                    />
                  </div>

                  <div>
                    <div className="mb-1.5 text-[12px] text-[#667085]">
                      来源表
                    </div>
                    <Input
                      allowClear
                      variant="filled"
                      value={filterDraft.sourceTable}
                      placeholder="请输入来源表"
                      onChange={(event) =>
                        onDraftChange(
                          'sourceTable',
                          event.target.value || undefined,
                        )
                      }
                      onPressEnter={applyAdvancedFilters}
                    />
                  </div>

                  <div>
                    <div className="mb-1.5 text-[12px] text-[#667085]">
                      目标表
                    </div>
                    <Input
                      allowClear
                      variant="filled"
                      value={filterDraft.sinkTable}
                      placeholder="请输入目标表"
                      onChange={(event) =>
                        onDraftChange(
                          'sinkTable',
                          event.target.value || undefined,
                        )
                      }
                      onPressEnter={applyAdvancedFilters}
                    />
                  </div>
                </div>

                <div className="mt-5 flex items-center justify-end gap-2 border-t border-[#f0f0f0] pt-4">
                  <YakButton
                    size="small"
                    onClick={() => {
                      onAdvancedReset();
                      setAdvancedOpen(false);
                    }}
                  >
                    重置
                  </YakButton>
                  <YakButton
                    type="primary"
                    size="small"
                    onClick={applyAdvancedFilters}
                  >
                    应用筛选
                  </YakButton>
                </div>
              </div>
            }
          >
            <YakButton
              size="small"
              icon={<FilterOutlined />}
              className={[
                '!h-9 !px-3',
                advancedFilterCount > 0
                  ? '!border-[#ffccc7] !bg-[#fff1f0] !text-[#ff4d4f]'
                  : '',
              ].join(' ')}
            >
              高级搜索
              {advancedFilterCount > 0 ? (
                <span className="ml-1.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[#ff4d4f] px-1 text-[10px] leading-[18px] text-white">
                  {advancedFilterCount}
                </span>
              ) : null}
            </YakButton>
          </Popover>
        </div>
      </div>
    </div>
  );
};

export default OfflineSyncFilterBar;
