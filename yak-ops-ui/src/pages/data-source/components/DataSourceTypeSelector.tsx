import { YakButton } from '@/components/ui';
import { SearchOutlined } from '@ant-design/icons';
import { Empty, Input, Select } from 'antd';
import { useMemo, useState } from 'react';

import { COMMON_DB_OPTIONS } from '../constants';
import DatabaseIcons from '../icon/DatabaseIcons';
import type { DataSourceGroup } from '../types';

interface DataSourceTypeSelectorProps {
  dataSourceGroups: DataSourceGroup[];
  onSelect: (dbType: string) => void;
}

const DataSourceTypeSelector = ({
  dataSourceGroups,
  onSelect,
}: DataSourceTypeSelectorProps) => {
  const [query, setQuery] = useState('');
  const [selectedGroupName, setSelectedGroupName] = useState<string | null>(null);
  const keyword = query.trim().toLowerCase();

  const flatDataSources = useMemo(
    () =>
      dataSourceGroups.flatMap((group) =>
        group.datasourceList.map((item) => ({
          ...item,
          groupName: group.groupName,
          searchText: [item.dbType, item.connectorType, item.type]
            .filter(Boolean)
            .join(' ')
            .toLowerCase(),
        })),
      ),
    [dataSourceGroups],
  );

  const filteredDataSources = useMemo(
    () =>
      flatDataSources.filter((item) => {
        const matchesGroup =
          selectedGroupName === null || item.groupName === selectedGroupName;
        const matchesKeyword = !keyword || item.searchText.includes(keyword);
        return matchesGroup && matchesKeyword;
      }),
    [flatDataSources, keyword, selectedGroupName],
  );

  const groupedDataSources = useMemo(
    () =>
      dataSourceGroups
        .filter(
          (group) =>
            selectedGroupName === null || group.groupName === selectedGroupName,
        )
        .map((group) => ({
          groupName: group.groupName,
          items: filteredDataSources.filter(
            (item) => item.groupName === group.groupName,
          ),
        }))
        .filter((group) => group.items.length > 0),
    [dataSourceGroups, filteredDataSources, selectedGroupName],
  );

  const categoryOptions = useMemo(
    () => [
      { value: 'ALL', label: '全部分类' },
      ...dataSourceGroups.map((group) => ({
        value: group.groupName,
        label: group.groupName,
      })),
    ],
    [dataSourceGroups],
  );

  const suggestedDataSources = useMemo(
    () =>
      COMMON_DB_OPTIONS.map((common) => {
        const matched = flatDataSources.find((item) => {
          const dbType = item.dbType?.toLowerCase();
          const value = common.value?.toLowerCase();
          const label = common.label?.toLowerCase();
          return dbType === value || dbType === label;
        });

        return {
          ...common,
          dbType: matched?.dbType || common.value,
        };
      })
        .filter((item) => Boolean(item.dbType))
        .slice(0, 3),
    [flatDataSources],
  );

  const renderSourceItem = (item: (typeof filteredDataSources)[number]) => (
    <YakButton
      key={[item.groupName, item.dbType, item.connectorType || item.type || ''].join(
        '-',
      )}
      htmlType="button"
      disabled={item.disabled}
      className="!h-auto !min-h-[46px] !min-w-0 !justify-start !px-3 !py-2 !text-left"
      onClick={() => onSelect(item.dbType)}
    >
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white">
        <DatabaseIcons dbType={item.dbType} width="15px" height="15px" />
      </span>
      <span
        className="min-w-0 flex-1 truncate text-[13px] font-medium"
        title={item.dbType}
      >
        {item.dbType}
      </span>
    </YakButton>
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0">
        <div className="mb-3 text-sm font-semibold leading-6 text-[#161823]">
          选择数据源
        </div>

        <div className="flex gap-2">
          <Input
            allowClear
            variant="filled"
            prefix={<SearchOutlined className="text-[#98A2B3]" />}
            placeholder="搜索数据源"
            value={query}
            className="!h-9 !min-w-0 !flex-1 !rounded-lg"
            onChange={(event) => setQuery(event.target.value)}
          />

          <Select
            variant="filled"
            value={selectedGroupName || 'ALL'}
            options={categoryOptions}
            className="!h-9 !w-[150px] shrink-0"
            popupMatchSelectWidth={false}
            onChange={(value) =>
              setSelectedGroupName(value === 'ALL' ? null : value)
            }
          />
        </div>
      </div>

      {!keyword && selectedGroupName === null && suggestedDataSources.length > 0 ? (
        <section className="mt-4 shrink-0">
          <div className="mb-2 text-xs font-semibold text-[#161823]">常用</div>
          <div className="grid grid-cols-3 gap-2">
            {suggestedDataSources.map((item) => (
              <YakButton
                key={item.dbType}
                htmlType="button"
                className="!h-auto !min-w-0 !justify-start !px-2.5 !py-2 !text-left"
                onClick={() => onSelect(item.dbType)}
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white">
                  <DatabaseIcons
                    dbType={item.dbType}
                    width="14px"
                    height="14px"
                  />
                </span>
                <span className="min-w-0 flex-1 truncate text-xs font-medium">
                  {item.label}
                </span>
              </YakButton>
            ))}
          </div>
        </section>
      ) : null}

      <section className="mt-5 flex min-h-0 flex-1 flex-col">
        <div className="mb-2 flex shrink-0 items-center justify-between">
          <span className="text-xs font-semibold text-[#161823]">全部数据源</span>
          <span className="text-[11px] text-[#98A2B3]">
            {filteredDataSources.length}
          </span>
        </div>

        {filteredDataSources.length === 0 ? (
          <div className="flex min-h-0 flex-1 items-center justify-center px-5 py-8">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="没有匹配的数据源"
            />
          </div>
        ) : (
          <div className="min-h-0 flex-1 overflow-y-auto pr-1">
            <div className="space-y-4">
              {groupedDataSources.map((group) => (
                <section key={group.groupName}>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-[11px] font-medium text-[#667085]">
                      {group.groupName}
                    </span>
                    <span className="text-[10px] text-[#B0B7C3]">
                      {group.items.length}
                    </span>
                  </div>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {group.items.map(renderSourceItem)}
                  </div>
                </section>
              ))}
            </div>
          </div>
        )}
      </section>
    </div>
  );
};

export default DataSourceTypeSelector;
