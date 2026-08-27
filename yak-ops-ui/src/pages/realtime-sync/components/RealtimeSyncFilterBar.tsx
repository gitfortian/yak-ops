import { YakButton } from '@/components/ui';
import { FilterOutlined, SearchOutlined } from '@ant-design/icons';
import { Input, Popover, Segmented, Select } from 'antd';
import { useState } from 'react';

import {
  REALTIME_SYNC_RELEASE_OPTIONS,
  REALTIME_SYNC_STATUS_TABS,
} from '../constants';
import type {
  RealtimeFilterField,
  RealtimeFilterState,
  RealtimePageStateGroup,
} from '../types';

const STATUS_SEGMENT_CLASS = [
  '!h-9 !rounded-[10px] !bg-[#f4f5f7] !p-[3px]',
  '[&_.ant-segmented-group]:!h-[30px]',
  '[&_.ant-segmented-group]:!gap-1',
  '[&_.ant-segmented-item]:!min-w-[72px]',
  '[&_.ant-segmented-item]:!rounded-[7px]',
  '[&_.ant-segmented-item]:!px-0',
  '[&_.ant-segmented-item]:!text-[13px]',
  '[&_.ant-segmented-item]:!font-medium',
  '[&_.ant-segmented-item]:!text-[#747985]',
  '[&_.ant-segmented-item-label]:!min-h-0',
  '[&_.ant-segmented-item-label]:!px-3',
  '[&_.ant-segmented-item-label]:!leading-[30px]',
  '[&_.ant-segmented-thumb]:!rounded-[7px]',
  '[&_.ant-segmented-thumb]:!bg-white',
  '[&_.ant-segmented-thumb]:!shadow-[0_1px_4px_rgba(31,35,41,0.10)]',
  '[&_.ant-segmented-item-selected]:!bg-white',
  '[&_.ant-segmented-item-selected]:!font-semibold',
  '[&_.ant-segmented-item-selected]:!text-[#252832]',
  '[&_.ant-segmented-item-selected]:!shadow-[0_1px_4px_rgba(31,35,41,0.10)]',
].join(' ');

interface RealtimeSyncFilterBarProps {
  filterDraft: RealtimeFilterState;
  activeStateGroup: RealtimePageStateGroup;
  advancedFilterCount: number;
  onDraftChange: <Field extends RealtimeFilterField>(
    field: Field,
    value: RealtimeFilterState[Field],
  ) => void;
  onStateGroupChange: (value: RealtimePageStateGroup) => void;
  onReleaseStateChange: (
    value: RealtimeFilterState['releaseState'],
  ) => void;
  onSearch: () => boolean;
  onReset: () => void;
}

const RealtimeSyncFilterBar = ({
  filterDraft,
  activeStateGroup,
  advancedFilterCount,
  onDraftChange,
  onStateGroupChange,
  onReleaseStateChange,
  onSearch,
  onReset,
}: RealtimeSyncFilterBarProps) => {
  const [advancedOpen, setAdvancedOpen] = useState(false);

  const applyAdvancedFilter = () => {
    if (onSearch()) setAdvancedOpen(false);
  };

  const resetFilters = () => {
    onReset();
    setAdvancedOpen(false);
  };

  return (
    <div className="flex min-h-[44px] items-center justify-between gap-4">
      <Segmented
        size="small"
        value={activeStateGroup}
        options={REALTIME_SYNC_STATUS_TABS.map((item) => ({
          value: item.value,
          label: item.label,
        }))}
        className={STATUS_SEGMENT_CLASS}
        onChange={(value) =>
          onStateGroupChange(value as RealtimePageStateGroup)
        }
      />

      <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
        <Input
          allowClear
          variant="filled"
          value={filterDraft.keyword}
          prefix={<SearchOutlined className="text-[#98a2b3]" />}
          placeholder="搜索任务名称 / 描述"
          className="!h-9 !w-[240px] !min-w-[190px]"
          onChange={(event) =>
            onDraftChange('keyword', event.target.value || undefined)
          }
          onPressEnter={() => void onSearch()}
        />

        <Select
          allowClear
          variant="filled"
          value={filterDraft.releaseState}
          options={REALTIME_SYNC_RELEASE_OPTIONS.map((item) => ({
            ...item,
          }))}
          placeholder="发布状态"
          className="!h-9 !w-[135px] !min-w-[125px]"
          onChange={onReleaseStateChange}
        />

        <YakButton className="!h-9 !px-4" onClick={() => void onSearch()}>
          查询
        </YakButton>

        <Popover
          trigger="click"
          placement="bottomRight"
          open={advancedOpen}
          onOpenChange={setAdvancedOpen}
          content={
            <div className="w-[320px]">
              <div className="text-[14px] font-semibold text-[#101828]">
                高级搜索
              </div>
              <div className="mt-1 text-[12px] text-[#98a2b3]">
                按任务 ID 精确定位实时任务
              </div>

              <div className="mt-4">
                <div className="mb-1.5 text-[12px] text-[#667085]">
                  任务 ID
                </div>
                <Input
                  allowClear
                  variant="filled"
                  value={filterDraft.id}
                  placeholder="请输入数字任务 ID"
                  onChange={(event) =>
                    onDraftChange('id', event.target.value || undefined)
                  }
                  onPressEnter={applyAdvancedFilter}
                />
              </div>

              <div className="mt-5 flex items-center justify-end gap-2 border-t border-[#f0f0f0] pt-4">
                <YakButton size="small" onClick={resetFilters}>
                  重置全部
                </YakButton>
                <YakButton
                  type="primary"
                  danger
                  size="small"
                  onClick={applyAdvancedFilter}
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
  );
};

export default RealtimeSyncFilterBar;
