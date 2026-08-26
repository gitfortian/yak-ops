import { YakButton } from '@/components/ui';
import { Input } from 'antd';
import { Search } from 'lucide-react';

import { DATA_SERVICE_SEARCH_PLACEHOLDER } from '../constants';

interface DataServiceSearchBarProps {
  keyword: string;
  loading: boolean;
  compact?: boolean;
  onKeywordChange: (value: string) => void;
  onSearch: () => void;
}

const DataServiceSearchBar = ({
  keyword,
  loading,
  compact = false,
  onKeywordChange,
  onSearch,
}: DataServiceSearchBarProps) => {
  if (compact) {
    return (
      <div className="flex min-w-0 flex-1 items-center gap-2 rounded-lg bg-[#f6f7f8] p-1.5">
        <Input
          allowClear
          variant="borderless"
          value={keyword}
          prefix={<Search size={15} className="text-[#98a2b3]" />}
          placeholder={DATA_SERVICE_SEARCH_PLACEHOLDER}
          className="!h-8 !bg-transparent !px-2"
          onChange={(event) => onKeywordChange(event.target.value)}
          onPressEnter={onSearch}
        />
        <YakButton
          type="primary"
          loading={loading}
          className="!h-8 !px-4"
          onClick={onSearch}
        >
          搜索
        </YakButton>
      </div>
    );
  }

  return (
    <div className="w-full max-w-[720px] rounded-xl bg-white p-2 shadow-[0_10px_30px_rgba(16,24,40,.04)] ring-1 ring-[#e4e7ec] [&_.ant-input-affix-wrapper]:!bg-white [&_.ant-input-affix-wrapper>.ant-input]:!bg-white">
      <div className="flex items-center gap-2">
        <Search size={17} className="ml-2 shrink-0 text-[#98a2b3]" />
        <Input
          allowClear
          variant="borderless"
          value={keyword}
          placeholder="输入 API 名称、描述或 Endpoint"
          className="!h-11 !bg-white !px-1 !text-[13px]"
          onChange={(event) => onKeywordChange(event.target.value)}
          onPressEnter={onSearch}
        />
        <YakButton
          type="primary"
          loading={loading}
          className="!h-9 !px-5"
          onClick={onSearch}
        >
          搜索
        </YakButton>
      </div>
      <div className="px-3 pb-1 pt-1 text-[10px] text-[#a0a6af]">
        支持 API 名称、Endpoint、描述和数据源
      </div>
    </div>
  );
};

export default DataServiceSearchBar;
