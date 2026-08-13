import { SearchOutlined } from '@ant-design/icons';
import { Empty, Input } from 'antd';
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
  const [selectedGroupName, setSelectedGroupName] =
    useState<string | null>(null);

  const keyword = query.trim().toLowerCase();

  const totalDatasourceCount = useMemo(
    () =>
      dataSourceGroups.reduce(
        (total, group) => total + group.datasourceList.length,
        0,
      ),
    [dataSourceGroups],
  );

  const flatDatasourceList = useMemo(
    () =>
      dataSourceGroups.flatMap((group) =>
        group.datasourceList.map((item) => ({
          ...item,
          groupName: group.groupName,
          searchText: [
            item.dbType,
            item.connectorType,
            item.type,
            group.groupName,
          ]
            .filter(Boolean)
            .join(' ')
            .toLowerCase(),
        })),
      ),
    [dataSourceGroups],
  );

  const filteredDatasourceList = useMemo(
    () =>
      flatDatasourceList.filter((item) => {
        const matchGroup =
          selectedGroupName === null || item.groupName === selectedGroupName;
        const matchKeyword = !keyword || item.searchText.includes(keyword);
        return matchGroup && matchKeyword;
      }),
    [flatDatasourceList, keyword, selectedGroupName],
  );

  const suggestedDatasourceList = useMemo(
    () =>
      COMMON_DB_OPTIONS.map((common) => {
        const matched = flatDatasourceList.find((item) => {
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
    [flatDatasourceList],
  );

  const showSuggested =
    !keyword && selectedGroupName === null && suggestedDatasourceList.length > 0;

  const renderSourceItem = (
    item: (typeof filteredDatasourceList)[number],
  ) => (
    <button
      key={[item.groupName, item.dbType, item.connectorType || item.type || ''].join(
        '-',
      )}
      type="button"
      disabled={item.disabled}
      className={[
        'group flex min-h-[50px] min-w-0 items-center gap-3 rounded-lg',
        'border border-[#E8EAED] bg-white px-3 py-2.5 text-left',
        'transition-all duration-150',
        'hover:border-[var(--ant-color-primary-border)] hover:bg-[var(--ant-color-primary-bg)]',
        'disabled:cursor-not-allowed disabled:opacity-45',
      ].join(' ')}
      onClick={() => onSelect(item.dbType)}
    >
      <span
        className={[
          'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg',
          'border border-[#EEF0F3] bg-[#F7F8FA]',
          'transition-colors duration-150',
          'group-hover:border-[var(--ant-color-primary-border)]',
        ].join(' ')}
      >
        <DatabaseIcons dbType={item.dbType} width="16px" height="16px" />
      </span>

      <span className="min-w-0 flex-1">
        <span
          className={[
            'block truncate text-[13px] font-medium leading-5 text-[#344054]',
            'transition-colors group-hover:text-[var(--ant-color-primary)]',
          ].join(' ')}
          title={item.dbType}
        >
          {item.dbType}
        </span>
        <span className="mt-0.5 block truncate text-[11px] leading-4 text-[#98A2B3]">
          {item.groupName}
        </span>
      </span>
    </button>
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0">
        <div className="mb-3">
          <div className="text-sm font-semibold leading-6 text-[#161823]">
            选择数据源类型
          </div>
          <div className="mt-0.5 text-xs leading-5 text-[#8A8F99]">
            选择一个连接器，下一步配置连接地址、账号和运行环境。
          </div>
        </div>

        <Input
          allowClear
          variant="filled"
          prefix={<SearchOutlined className="text-[#98A2B3]" />}
          placeholder="搜索 MySQL、PostgreSQL、Oracle..."
          value={query}
          className="!h-9 !rounded-lg"
          onChange={(event) => setQuery(event.target.value)}
        />

        <div className="mt-3 flex items-center gap-2 overflow-x-auto pb-1">
          <button
            type="button"
            className={[
              'h-7 shrink-0 rounded-md px-2.5 text-xs font-medium transition-colors',
              selectedGroupName === null
                ? 'bg-[#161823] text-white'
                : 'bg-[#F2F4F7] text-[#667085] hover:bg-[#EAECF0]',
            ].join(' ')}
            onClick={() => setSelectedGroupName(null)}
          >
            全部 {totalDatasourceCount}
          </button>

          {dataSourceGroups.map((group) => {
            const active = selectedGroupName === group.groupName;
            return (
              <button
                key={group.groupName}
                type="button"
                className={[
                  'h-7 shrink-0 rounded-md px-2.5 text-xs font-medium transition-colors',
                  active
                    ? 'bg-[#161823] text-white'
                    : 'bg-[#F2F4F7] text-[#667085] hover:bg-[#EAECF0]',
                ].join(' ')}
                onClick={() =>
                  setSelectedGroupName((previous) =>
                    previous === group.groupName ? null : group.groupName,
                  )
                }
              >
                {group.groupName} {group.datasourceList.length}
              </button>
            );
          })}
        </div>
      </div>

      {showSuggested && (
        <section className="mt-4 shrink-0 border-b border-[#EEF0F3] pb-4">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold text-[#161823]">常用数据源</span>
            <span className="text-[11px] text-[#98A2B3]">点击直接配置</span>
          </div>

          <div className="grid grid-cols-3 gap-2">
            {suggestedDatasourceList.map((item) => (
              <button
                key={item.dbType}
                type="button"
                className={[
                  'group flex min-w-0 items-center gap-2 rounded-lg border',
                  'border-[#E8EAED] bg-white px-2.5 py-2 text-left',
                  'transition-colors hover:border-[var(--ant-color-primary-border)]',
                  'hover:bg-[var(--ant-color-primary-bg)]',
                ].join(' ')}
                onClick={() => onSelect(item.dbType)}
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#F7F8FA]">
                  <DatabaseIcons
                    dbType={item.dbType}
                    width="14px"
                    height="14px"
                  />
                </span>
                <span className="min-w-0 flex-1 truncate text-xs font-medium text-[#344054] group-hover:text-[var(--ant-color-primary)]">
                  {item.label}
                </span>
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="mt-4 flex min-h-0 flex-1 flex-col">
        <div className="mb-2 flex shrink-0 items-center justify-between">
          <span className="text-xs font-semibold text-[#161823]">全部连接器</span>
          <span className="text-[11px] text-[#98A2B3]">
            {filteredDatasourceList.length} 个
          </span>
        </div>

        {filteredDatasourceList.length === 0 ? (
          <div className="flex min-h-0 flex-1 items-center justify-center rounded-lg border border-dashed border-[#D0D5DD] bg-[#FCFCFD] px-5 py-8">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="未找到匹配的数据源类型"
            />
          </div>
        ) : (
          <div className="min-h-0 flex-1 overflow-y-auto pr-1">
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {filteredDatasourceList.map(renderSourceItem)}
            </div>
          </div>
        )}
      </section>
    </div>
  );
};

export default DataSourceTypeSelector;
