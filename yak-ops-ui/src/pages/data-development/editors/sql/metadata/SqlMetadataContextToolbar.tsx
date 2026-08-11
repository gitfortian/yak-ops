import { Popover, Spin, message } from 'antd';
import { ChevronDown, Database, Layers3, Search, Server } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';

import type { DevelopmentId } from '../../../types';
import {
  selectSqlDatabaseContext,
  selectSqlDataSourceContext,
  selectSqlSchemaContext,
  useSqlMetadataContext,
} from './sqlMetadataContextStore';
import {
  listSqlDatabases,
  listSqlDataSources,
  listSqlSchemas,
  type SqlDataSourceOption,
} from './sqlMetadataService';

interface SqlMetadataContextToolbarProps {
  nodeId: DevelopmentId;
}

interface ContextPickerItem {
  value: string;
  label: string;
  searchText?: string;
  icon?: ReactNode;
}

interface ContextPickerProps {
  ariaLabel: string;
  value?: string;
  displayValue?: string;
  placeholder: string;
  icon: ReactNode;
  items: ContextPickerItem[];
  loading?: boolean;
  disabled?: boolean;
  popupWidth?: number;
  minWidthClassName?: string;
  onSelect: (value: string) => void;
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const ContextPicker = ({
  ariaLabel,
  value,
  displayValue,
  placeholder,
  icon,
  items,
  loading = false,
  disabled = false,
  popupWidth = 210,
  minWidthClassName = 'min-w-[108px]',
  onSelect,
}: ContextPickerProps) => {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const normalizedKeyword = keyword.trim().toLowerCase();

  const filteredItems = useMemo(
    () =>
      normalizedKeyword
        ? items.filter((item) =>
            `${item.label} ${item.searchText || ''}`
              .toLowerCase()
              .includes(normalizedKeyword),
          )
        : items,
    [items, normalizedKeyword],
  );

  const popup = (
    <div style={{ width: popupWidth }}>
      <div className="flex h-8 items-center gap-1.5 border-b border-[#e5e7eb] px-2.5">
        <Search size={13} strokeWidth={1.8} className="shrink-0 text-[#6b7280]" />
        <input
          autoFocus
          value={keyword}
          placeholder="搜索"
          onChange={(event) => setKeyword(event.target.value)}
          className="h-full min-w-0 flex-1 border-0 bg-transparent p-0 text-[12px] text-[#30323b] outline-none placeholder:text-[#9ca3af]"
        />
      </div>

      <div className="max-h-[240px] overflow-y-auto p-1">
        {loading ? (
          <div className="flex h-10 items-center justify-center">
            <Spin size="small" />
          </div>
        ) : filteredItems.length ? (
          filteredItems.map((item) => {
            const selected = item.value === value;
            return (
              <button
                key={item.value}
                type="button"
                title={item.label}
                onClick={() => {
                  onSelect(item.value);
                  setOpen(false);
                  setKeyword('');
                }}
                className={[
                  'flex h-8 w-full items-center gap-2 rounded-[2px] px-2 text-left text-[12px] transition-colors',
                  selected
                    ? 'bg-[#f2f3f5] text-[#161823]'
                    : 'text-[#30323b] hover:bg-[#f5f5f6]',
                ].join(' ')}
              >
                <span className="flex h-4 w-4 shrink-0 items-center justify-center">
                  {item.icon || icon}
                </span>
                <span className="min-w-0 flex-1 truncate">{item.label}</span>
              </button>
            );
          })
        ) : (
          <div className="flex h-10 items-center justify-center text-[11px] text-[#98a2b3]">
            暂无匹配项
          </div>
        )}
      </div>
    </div>
  );

  return (
    <Popover
      trigger="click"
      placement="bottomLeft"
      arrow={false}
      open={open}
      onOpenChange={(nextOpen) => {
        if (disabled) return;
        setOpen(nextOpen);
        if (!nextOpen) setKeyword('');
      }}
      overlayClassName="sql-metadata-context-popover"
      content={popup}
    >
      <button
        type="button"
        aria-label={ariaLabel}
        disabled={disabled}
        className={[
          'flex h-7 max-w-[176px] items-center gap-1.5 rounded-[3px] px-2 text-[12px] outline-none transition-colors',
          minWidthClassName,
          disabled
            ? 'cursor-not-allowed text-[#b7bcc5]'
            : open
              ? 'bg-[#f1f2f4] text-[#161823]'
              : 'text-[#30323b] hover:bg-[#f5f5f6]',
        ].join(' ')}
      >
        <span className="flex h-4 w-4 shrink-0 items-center justify-center">{icon}</span>
        <span
          className={[
            'min-w-0 flex-1 truncate text-left',
            displayValue ? 'text-[#30323b]' : 'text-[#7b808a]',
          ].join(' ')}
        >
          {displayValue || placeholder}
        </span>
        <ChevronDown
          size={12}
          strokeWidth={1.8}
          className={[
            'shrink-0 transition-transform duration-150',
            open ? 'rotate-180' : '',
          ].join(' ')}
        />
      </button>
    </Popover>
  );
};

const SqlMetadataContextToolbar = ({
  nodeId,
}: SqlMetadataContextToolbarProps) => {
  const context = useSqlMetadataContext(nodeId);
  const [dataSources, setDataSources] = useState<SqlDataSourceOption[]>([]);
  const [databases, setDatabases] = useState<string[]>([]);
  const [schemas, setSchemas] = useState<string[]>([]);
  const [dataSourceLoading, setDataSourceLoading] = useState(false);
  const [databaseLoading, setDatabaseLoading] = useState(false);
  const [schemaLoading, setSchemaLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setDataSourceLoading(true);
    listSqlDataSources()
      .then((values) => {
        if (active) setDataSources(values || []);
      })
      .catch((error) => {
        if (active) message.error(errorText(error, '查询数据源失败'));
      })
      .finally(() => {
        if (active) setDataSourceLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    if (!context.dataSourceId) {
      setDatabases([]);
      setSchemas([]);
      return () => {
        active = false;
      };
    }

    setDatabaseLoading(true);
    listSqlDatabases(context.dataSourceId)
      .then((values) => {
        if (!active) return;
        const next = values || [];
        setDatabases(next);
        if (context.database && !next.includes(context.database)) {
          selectSqlDatabaseContext(nodeId, undefined);
        }
      })
      .catch((error) => {
        if (active) message.error(errorText(error, '查询数据库失败'));
      })
      .finally(() => {
        if (active) setDatabaseLoading(false);
      });

    return () => {
      active = false;
    };
  }, [context.dataSourceId, context.database, nodeId]);

  useEffect(() => {
    let active = true;
    if (!context.dataSourceId) {
      setSchemas([]);
      return () => {
        active = false;
      };
    }

    setSchemaLoading(true);
    listSqlSchemas(context.dataSourceId, context.database)
      .then((values) => {
        if (!active) return;
        const next = values || [];
        setSchemas(next);
        if (context.schema && !next.includes(context.schema)) {
          selectSqlSchemaContext(nodeId, undefined);
        }
      })
      .catch((error) => {
        if (active) message.error(errorText(error, '查询 Schema 失败'));
      })
      .finally(() => {
        if (active) setSchemaLoading(false);
      });

    return () => {
      active = false;
    };
  }, [context.dataSourceId, context.database, context.schema, nodeId]);

  const normalizedDbType = context.dbType?.trim().toUpperCase();
  const showSchemaPicker = Boolean(
    context.dataSourceId &&
      normalizedDbType &&
      !['MYSQL', 'MARIADB', 'SQLITE'].includes(normalizedDbType),
  );

  const dataSourceItems = dataSources.map((item) => ({
    value: item.value,
    label: `@${item.label}`,
    searchText: item.dbType,
    icon: <Server size={13} strokeWidth={1.8} className="text-[#1677ff]" />,
  }));
  const databaseItems = databases.map((value) => ({
    value,
    label: value,
    icon: <Database size={13} strokeWidth={1.8} className="text-[#475467]" />,
  }));
  const schemaItems = schemas.map((value) => ({
    value,
    label: value,
    icon: <Layers3 size={13} strokeWidth={1.8} className="text-[#667085]" />,
  }));

  return (
    <>
      <div className="flex min-w-0 shrink-0 items-center gap-0.5">
        <ContextPicker
          ariaLabel="选择 SQL 数据源"
          value={context.dataSourceId}
          displayValue={context.dataSourceName ? `@${context.dataSourceName}` : undefined}
          placeholder="@datasource"
          icon={<Server size={13} strokeWidth={1.8} className="text-[#1677ff]" />}
          items={dataSourceItems}
          loading={dataSourceLoading}
          popupWidth={210}
          minWidthClassName="min-w-[112px]"
          onSelect={(value) => {
            const selected = dataSources.find((item) => item.value === value);
            if (!selected) return;
            selectSqlDataSourceContext(nodeId, {
              id: selected.value,
              name: selected.label,
              dbType: selected.dbType,
            });
          }}
        />

        <ContextPicker
          ariaLabel="选择 Database"
          value={context.database}
          displayValue={context.database}
          placeholder="<database>"
          icon={<Database size={13} strokeWidth={1.8} className="text-[#475467]" />}
          items={databaseItems}
          loading={databaseLoading}
          disabled={!context.dataSourceId}
          popupWidth={210}
          minWidthClassName="min-w-[116px]"
          onSelect={(value) => selectSqlDatabaseContext(nodeId, value)}
        />

        {showSchemaPicker ? (
          <ContextPicker
            ariaLabel="选择 Schema"
            value={context.schema}
            displayValue={context.schema}
            placeholder="<schema>"
            icon={<Layers3 size={13} strokeWidth={1.8} className="text-[#667085]" />}
            items={schemaItems}
            loading={schemaLoading}
            disabled={!context.dataSourceId}
            popupWidth={210}
            minWidthClassName="min-w-[108px]"
            onSelect={(value) => selectSqlSchemaContext(nodeId, value)}
          />
        ) : null}
      </div>

      <style>{`
        .sql-metadata-context-popover .ant-popover-inner {
          padding: 0;
          overflow: hidden;
          border: 1px solid #dfe3e8;
          border-radius: 3px;
          box-shadow: 0 4px 12px rgba(16, 24, 40, 0.10);
        }
        .sql-metadata-context-popover .ant-popover-inner-content {
          padding: 0;
        }
      `}</style>
    </>
  );
};

export default SqlMetadataContextToolbar;
