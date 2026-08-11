import { Select, message } from 'antd';
import { Database } from 'lucide-react';
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

interface SqlMetadataContextPanelProps {
  nodeId: DevelopmentId;
  nodeName: string;
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const SqlMetadataContextPanel = ({
  nodeId,
  nodeName,
}: SqlMetadataContextPanelProps) => {
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

  const contextPath = useMemo(
    () =>
      [context.dataSourceName, context.database, context.schema]
        .filter(Boolean)
        .join(' / '),
    [context.dataSourceName, context.database, context.schema],
  );

  return (
    <div className="text-[12px] text-[#667085]">
      <div className="flex items-center gap-2 font-medium text-[#344054]">
        <Database size={14} strokeWidth={1.8} />
        SQL 元数据上下文
      </div>
      <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
        为 {nodeName} 选择数据源上下文，编辑器会使用 Yak Ops Catalog 提供表和字段补全。
      </div>

      <div className="mt-4 space-y-4">
        <label className="block">
          <span className="mb-1.5 block text-[11px] text-[#667085]">数据源</span>
          <Select
            allowClear
            showSearch
            size="small"
            optionFilterProp="label"
            placeholder="选择数据源"
            loading={dataSourceLoading}
            value={context.dataSourceId}
            options={dataSources.map((item) => ({
              label: item.dbType ? `${item.label} · ${item.dbType}` : item.label,
              value: item.value,
            }))}
            className="w-full"
            onChange={(value) => {
              const selected = dataSources.find((item) => item.value === value);
              selectSqlDataSourceContext(
                nodeId,
                selected
                  ? {
                      id: selected.value,
                      name: selected.label,
                      dbType: selected.dbType,
                    }
                  : undefined,
              );
            }}
          />
        </label>

        <label className="block">
          <span className="mb-1.5 block text-[11px] text-[#667085]">Database</span>
          <Select
            allowClear
            showSearch
            size="small"
            placeholder={context.dataSourceId ? '自动 / 选择 Database' : '请先选择数据源'}
            disabled={!context.dataSourceId}
            loading={databaseLoading}
            value={context.database}
            options={databases.map((value) => ({ label: value, value }))}
            className="w-full"
            onChange={(value) => selectSqlDatabaseContext(nodeId, value)}
          />
        </label>

        <label className="block">
          <span className="mb-1.5 block text-[11px] text-[#667085]">Schema</span>
          <Select
            allowClear
            showSearch
            size="small"
            placeholder={context.dataSourceId ? '自动 / 选择 Schema' : '请先选择数据源'}
            disabled={!context.dataSourceId}
            loading={schemaLoading}
            value={context.schema}
            options={schemas.map((value) => ({ label: value, value }))}
            className="w-full"
            onChange={(value) => selectSqlSchemaContext(nodeId, value)}
          />
        </label>
      </div>

      <div className="mt-4 border-t border-[#eef0f2] pt-3 text-[11px] leading-5 text-[#98a2b3]">
        {context.dataSourceId ? (
          <>
            当前补全上下文：
            <span className="ml-1 break-all text-[#475467]">
              {contextPath || `数据源 ${context.dataSourceId}`}
            </span>
          </>
        ) : (
          '未选择数据源时，仅保留 SQL 关键字和内置函数补全。'
        )}
      </div>
    </div>
  );
};

export default SqlMetadataContextPanel;
