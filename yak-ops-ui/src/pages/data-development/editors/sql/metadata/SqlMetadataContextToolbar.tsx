import { Select, Tooltip, message } from 'antd';
import { Database } from 'lucide-react';
import { useEffect, useState } from 'react';

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

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const contextControlClassName =
  'flex h-7 shrink-0 items-center rounded-[3px] border border-[#dfe3e8] bg-white transition-colors hover:border-[#cfd4dc]';

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

  return (
    <div className="flex min-w-0 shrink-0 items-center gap-1.5">
      <Tooltip title="SQL 数据源" mouseEnterDelay={0.35}>
        <div className={`${contextControlClassName} w-[176px]`}>
          <Database
            size={13}
            strokeWidth={1.8}
            className="ml-2 shrink-0 text-[#667085]"
          />
          <Select
            allowClear
            showSearch
            size="small"
            variant="borderless"
            optionFilterProp="label"
            placeholder="选择数据源"
            loading={dataSourceLoading}
            value={context.dataSourceId}
            options={dataSources.map((item) => ({
              label: `@${item.label}`,
              value: item.value,
              title: item.dbType ? `${item.label} · ${item.dbType}` : item.label,
            }))}
            popupMatchSelectWidth={260}
            className="min-w-0 flex-1"
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
        </div>
      </Tooltip>

      <Tooltip title="Database" mouseEnterDelay={0.35}>
        <div className={`${contextControlClassName} w-[140px]`}>
          <span className="ml-2 shrink-0 text-[10px] font-medium text-[#98a2b3]">
            DB
          </span>
          <Select
            allowClear
            showSearch
            size="small"
            variant="borderless"
            placeholder="<database>"
            disabled={!context.dataSourceId}
            loading={databaseLoading}
            value={context.database}
            options={databases.map((value) => ({ label: value, value }))}
            popupMatchSelectWidth={220}
            className="min-w-0 flex-1"
            onChange={(value) => selectSqlDatabaseContext(nodeId, value)}
          />
        </div>
      </Tooltip>

      <Tooltip title="Schema" mouseEnterDelay={0.35}>
        <div className={`${contextControlClassName} w-[124px]`}>
          <span className="ml-2 shrink-0 text-[10px] font-medium text-[#98a2b3]">
            S
          </span>
          <Select
            allowClear
            showSearch
            size="small"
            variant="borderless"
            placeholder="<schema>"
            disabled={!context.dataSourceId}
            loading={schemaLoading}
            value={context.schema}
            options={schemas.map((value) => ({ label: value, value }))}
            popupMatchSelectWidth={200}
            className="min-w-0 flex-1"
            onChange={(value) => selectSqlSchemaContext(nodeId, value)}
          />
        </div>
      </Tooltip>
    </div>
  );
};

export default SqlMetadataContextToolbar;
