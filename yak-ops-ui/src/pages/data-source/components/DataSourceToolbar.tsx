import { YakButton, YakTab } from "@/components/ui";
import { Input, Select } from "antd";
import { motion } from "framer-motion";
import { Grid2X2, LayoutList, Search } from "lucide-react";

import {
  COMMON_DB_OPTIONS,
  DATA_SOURCE_ENVIRONMENT_TABS,
  PAGE_ANIMATION,
} from "../constants";
import type { DataSourceViewMode } from "../types";

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
    className="flex min-h-9 items-end justify-between gap-6 border-b border-solid border-[#eceef2] max-xl:flex-col max-xl:items-stretch max-xl:gap-3"
  >
    <div className="flex items-end">
      <YakTab
        size="small"
        activeKey={environment || "all"}
        className={[
          "[&_.ant-tabs-nav]:!mb-0",
          "[&_.ant-tabs-nav::before]:!hidden",
          "[&_.ant-tabs-tab]:!px-0",
          "[&_.ant-tabs-tab]:!pb-[10px]",
          "[&_.ant-tabs-tab+.ant-tabs-tab]:!ml-8",
          "[&_.ant-tabs-tab]:!text-[13px]",
          "[&_.ant-tabs-tab]:!text-[#8c919b]",
          "[&_.ant-tabs-tab.ant-tabs-tab-active_.ant-tabs-tab-btn]:!font-semibold",
          "[&_.ant-tabs-tab.ant-tabs-tab-active_.ant-tabs-tab-btn]:!text-[#292c35]",
          "[&_.ant-tabs-tab::after]:!bottom-[-1px]",
          "[&_.ant-tabs-tab::after]:!h-0.5",
          "[&_.ant-tabs-tab::after]:!bg-[#252832]",
        ].join(" ")}
        items={DATA_SOURCE_ENVIRONMENT_TABS.map((item) => ({
          key: item.key,
          label: item.label,
        }))}
        onChange={(key) => {
          const target = DATA_SOURCE_ENVIRONMENT_TABS.find(
            (item) => item.key === key
          );
          onEnvironmentChange(target?.value);
        }}
      />
    </div>

    <div className="flex flex-wrap items-center justify-end gap-2 pb-px max-xl:justify-start">
      <Select
        allowClear
        variant="filled"
        value={dbType}
        className={[
          "!w-[132px]",
          "[&_.ant-select-selector]:!h-9",
          "[&_.ant-select-selector]:!rounded-[10px]",
          "[&_.ant-select-selector]:!border-0",
          "[&_.ant-select-selector]:!bg-[#f6f7f9]",
          "[&_.ant-select-selection-item]:!text-[12px]",
          "[&_.ant-select-selection-item]:!leading-[36px]",
          "[&_.ant-select-selection-placeholder]:!text-[12px]",
          "[&_.ant-select-selection-placeholder]:!leading-[36px]",
        ].join(" ")}
        placeholder="数据源类型"
        options={COMMON_DB_OPTIONS}
        popupMatchSelectWidth={180}
        onChange={onDbTypeChange}
      />

      <Input
        allowClear
        variant="filled"
        value={keyword}
        prefix={
          <Search size={15} strokeWidth={1.8} className="text-[#8f949e]" />
        }
        className={[
          "!w-[292px] max-md:!w-[220px]",
          "[&.ant-input-affix-wrapper]:!h-9",
          "[&.ant-input-affix-wrapper]:!rounded-[10px]",
          "[&.ant-input-affix-wrapper]:!border-0",
          "[&.ant-input-affix-wrapper]:!bg-[#f6f7f9]",
          "[&_.ant-input]:!text-[12px]",
        ].join(" ")}
        placeholder="搜索名称或连接地址"
        onChange={(event) => onKeywordChange(event.target.value)}
      />

      {hasActiveFilters ? (
        <YakButton
          type="text"
          size="small"
          className="!h-9 !rounded-[9px] !px-2.5 !text-[12px] !text-[#777c86]"
          onClick={onReset}
        >
          重置
        </YakButton>
      ) : null}

      <div className="flex h-9 items-center gap-0.5 rounded-[10px] bg-[#f4f5f7] p-[3px]">
        <YakButton
          type="text"
          iconOnly
          title="卡片视图"
          className={[
            "!h-[30px] !w-[30px] !rounded-[7px] !border-0 !p-0",
            viewMode === "grid"
              ? "!bg-white !text-[#2d313a] !shadow-[0_1px_4px_rgba(31,35,41,0.10)]"
              : "!bg-transparent !text-[#92969f] hover:!text-[#555b66]",
          ].join(" ")}
          icon={<Grid2X2 size={15} strokeWidth={1.8} />}
          onClick={() => onViewModeChange("grid")}
        />

        <YakButton
          type="text"
          iconOnly
          title="列表视图"
          className={[
            "!h-[30px] !w-[30px] !rounded-[7px] !border-0 !p-0",
            viewMode === "list"
              ? "!bg-white !text-[#2d313a] !shadow-[0_1px_4px_rgba(31,35,41,0.10)]"
              : "!bg-transparent !text-[#92969f] hover:!text-[#555b66]",
          ].join(" ")}
          icon={<LayoutList size={16} strokeWidth={1.8} />}
          onClick={() => onViewModeChange("list")}
        />
      </div>
    </div>
  </motion.section>
);

export default DataSourceToolbar;
