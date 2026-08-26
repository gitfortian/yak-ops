import { YakButton, YakTab } from '@/components/ui';
import { Input, Select } from 'antd';
import { motion } from 'framer-motion';
import { Grid2X2, LayoutList, Search } from 'lucide-react';

import {
  COMMON_DB_OPTIONS,
  DATA_SOURCE_ENVIRONMENT_TABS,
  PAGE_ANIMATION,
} from '../constants';
import type { DataSourceViewMode } from '../types';

interface DataSourceToolbarProps {
  environment?: string;
  dbType?: string;
  keyword: string;
  viewMode: DataSourceViewMode;
  hasActiveFilters: boolean;
  onEnvironmentChange: (value?: string) => void;
  onDbTypeChange: (value?: string) => void;
  onKeywordChange: (value: string) => void;
  onViewModeChange: (value: DataSourceViewMode) => void;
  onReset: () => void;
}

const DataSourceToolbar = ({
  environment,
  dbType,
  keyword,
  viewMode,
  hasActiveFilters,
  onEnvironmentChange,
  onDbTypeChange,
  onKeywordChange,
  onViewModeChange,
  onReset,
}: DataSourceToolbarProps) => (
  <motion.section
    variants={PAGE_ANIMATION.fadeUp}
    className="mt-[26px] flex min-h-[62px] items-end justify-between gap-6 border-b border-black/[0.075] max-xl:flex-col max-xl:items-stretch max-xl:gap-3"
  >
    <div className="h-[35px]">
      <YakTab
        size="small"
        activeKey={environment || 'all'}
        className={[
          '[&_.ant-tabs-tab.ant-tabs-tab-active_.ant-tabs-tab-btn]:!text-[#292c35]',
          '[&_.ant-tabs-tab::after]:!bg-[#252832]',
        ].join(' ')}
        items={DATA_SOURCE_ENVIRONMENT_TABS.map((item) => ({
          key: item.key,
          label: item.label,
        }))}
        onChange={(key) => {
          const target = DATA_SOURCE_ENVIRONMENT_TABS.find(
            (item) => item.key === key,
          );
          onEnvironmentChange(target?.value);
        }}
      />
    </div>

    <div className="flex items-center gap-2 pb-[11px] max-xl:justify-end max-xl:pb-3">
      <Select
        allowClear
        variant="filled"
        value={dbType}
        className={[
          '!w-[132px]',
          '[&_.ant-select-selector]:!h-9',
          '[&_.ant-select-selector]:!rounded-lg',
          '[&_.ant-select-selection-item]:!leading-[36px]',
          '[&_.ant-select-selection-placeholder]:!leading-[36px]',
        ].join(' ')}
        placeholder="数据源类型"
        options={COMMON_DB_OPTIONS}
        popupMatchSelectWidth={180}
        onChange={onDbTypeChange}
      />

      <Input
        allowClear
        variant="filled"
        value={keyword}
        prefix={<Search size={15} strokeWidth={1.8} />}
        className={[
          '!w-[300px]',
          '[&.ant-input-affix-wrapper]:!h-9',
          '[&.ant-input-affix-wrapper]:!rounded-lg',
          '[&_.ant-input]:!text-xs',
        ].join(' ')}
        placeholder="搜索名称或连接地址"
        onChange={(event) => onKeywordChange(event.target.value)}
      />

      {hasActiveFilters ? (
        <YakButton type="text" size="small" onClick={onReset}>
          重置
        </YakButton>
      ) : null}

      <div className="flex overflow-hidden rounded-[7px] border border-black/[0.09] bg-white">
        <YakButton
          type="text"
          iconOnly
          title="卡片视图"
          className={[
            '!h-[34px] !w-[34px] !rounded-none !border-0 !p-0',
            viewMode === 'grid'
              ? '!bg-[#f7f8fa] !text-[#252832]'
              : '!bg-white !text-black/[0.53]',
          ].join(' ')}
          icon={<Grid2X2 size={16} strokeWidth={1.8} />}
          onClick={() => onViewModeChange('grid')}
        />

        <YakButton
          type="text"
          iconOnly
          title="列表视图"
          className={[
            '!h-[34px] !w-[34px] !rounded-none !border-0 !border-l !border-l-black/[0.09] !p-0',
            viewMode === 'list'
              ? '!bg-[#f7f8fa] !text-[#252832]'
              : '!bg-white !text-black/[0.53]',
          ].join(' ')}
          icon={<LayoutList size={17} strokeWidth={1.8} />}
          onClick={() => onViewModeChange('list')}
        />
      </div>
    </div>
  </motion.section>
);

export default DataSourceToolbar;
