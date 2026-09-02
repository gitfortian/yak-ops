import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Button,
  DatePicker,
  Input,
  Select,
  Space,
  Tag,
  Typography,
  message,
  type TableProps,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import {
  getAuditFilterOptions,
  queryAuditOperations,
  type AuditFilterOption,
  type AuditFilterOptions,
  type AuditOperationQuery,
  type AuditOperationSummary,
} from '@/services/audit';

import AuditOperationDetailDrawer from './AuditOperationDetailDrawer';

interface FilterState {
  keyword?: string;
  actor?: string;
  projectId?: string;
  operationType?: string;
  resourceType?: string;
  status?: string;
  source?: string;
  timeRange?: [Dayjs, Dayjs];
}

const EMPTY_OPTIONS: AuditFilterOptions = {
  actors: [],
  projects: [],
  operationTypes: [],
  resourceTypes: [],
  statuses: [],
  sources: [],
};

const statusMeta = (status?: string) => {
  if (status === 'FAILED') return { label: '失败', color: 'error' as const };
  if (status === 'SUCCEEDED') return { label: '成功', color: 'success' as const };
  if (status === 'RUNNING') return { label: '运行中', color: 'processing' as const };
  return { label: status || '-', color: 'default' as const };
};

const formatDuration = (durationMillis?: number) => {
  if (durationMillis == null) return '-';
  if (durationMillis < 1000) return `${durationMillis} ms`;
  if (durationMillis < 60_000) return `${(durationMillis / 1000).toFixed(1)} s`;
  const minutes = Math.floor(durationMillis / 60_000);
  const seconds = Math.floor((durationMillis % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
};

const selectOptions = (
  options: AuditFilterOption[],
  labeler?: (option: AuditFilterOption) => string,
) =>
  options.map((option) => ({
    value: option.value,
    label: labeler ? labeler(option) : option.label,
  }));

const buildQuery = (
  filters: FilterState,
  page: number,
  size: number,
): AuditOperationQuery => ({
  page,
  size,
  keyword: filters.keyword?.trim() || undefined,
  actor: filters.actor,
  projectId: filters.projectId ? Number(filters.projectId) : undefined,
  operationType: filters.operationType,
  resourceType: filters.resourceType,
  status: filters.status,
  source: filters.source,
  startTime: filters.timeRange?.[0]
    .startOf('day')
    .format('YYYY-MM-DDTHH:mm:ss'),
  endTime: filters.timeRange?.[1]
    .endOf('day')
    .format('YYYY-MM-DDTHH:mm:ss'),
});

export default function BusinessAuditPanel() {
  const [rows, setRows] = useState<AuditOperationSummary[]>([]);
  const [options, setOptions] = useState<AuditFilterOptions>(EMPTY_OPTIONS);
  const [filters, setFilters] = useState<FilterState>({});
  const [loading, setLoading] = useState(false);
  const [selectedOperationId, setSelectedOperationId] = useState<string>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });

  const loadOptions = useCallback(async () => {
    try {
      setOptions(await getAuditFilterOptions());
    } catch {
      void message.error('加载审计筛选项失败');
    }
  }, []);

  const loadPage = useCallback(
    async (
      page: number,
      pageSize: number,
      nextFilters: FilterState,
    ) => {
      setLoading(true);
      try {
        const data = await queryAuditOperations(buildQuery(nextFilters, page, pageSize));
        setRows(data.bizData ?? []);
        setPagination({
          current: Number(data.pagination?.pageNo ?? page),
          pageSize: Number(data.pagination?.pageSize ?? pageSize),
          total: Number(data.pagination?.total ?? 0),
        });
      } catch {
        void message.error('加载业务审计记录失败');
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void loadOptions();
    void loadPage(1, 20, {});

    const deepLinkOperationId = new URLSearchParams(history.location.search)
      .get('operationId')
      ?.trim();
    if (deepLinkOperationId) {
      setSelectedOperationId(deepLinkOperationId);
    }
  }, [loadOptions, loadPage]);

  const columns: TableProps<AuditOperationSummary>['columns'] = useMemo(
    () => [
      {
        title: '时间',
        dataIndex: 'startedAt',
        width: 170,
        render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm:ss'),
      },
      {
        title: '操作',
        key: 'operation',
        width: 250,
        render: (_, record) => (
          <div className="min-w-0">
            <div className="truncate font-medium text-slate-800">
              {record.operationName || record.operationType}
            </div>
            <Typography.Text type="secondary" className="text-xs">
              {record.operationType}
            </Typography.Text>
          </div>
        ),
      },
      {
        title: '操作人',
        key: 'actor',
        width: 150,
        render: (_, record) => (
          <div>
            <div>{record.actorName || record.actorId || '-'}</div>
            {record.actorName && record.actorId ? (
              <div className="text-xs text-slate-400">{record.actorId}</div>
            ) : null}
          </div>
        ),
      },
      {
        title: '项目空间',
        key: 'project',
        width: 160,
        render: (_, record) =>
          record.projectName || (record.projectId ? `#${record.projectId}` : '全局'),
      },
      {
        title: '资源',
        key: 'resource',
        width: 220,
        render: (_, record) => (
          <div className="min-w-0">
            <div className="truncate">
              {record.resourceName || record.resourceId || '-'}
            </div>
            {record.resourceType ? (
              <div className="text-xs text-slate-400">{record.resourceType}</div>
            ) : null}
          </div>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: string) => {
          const meta = statusMeta(value);
          return (
            <Tag bordered={false} color={meta.color} className="m-0">
              {meta.label}
            </Tag>
          );
        },
      },
      {
        title: '来源',
        dataIndex: 'source',
        width: 100,
      },
      {
        title: '耗时',
        dataIndex: 'durationMillis',
        width: 100,
        render: (value?: number) => formatDuration(value),
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 80,
        render: (_, record) => (
          <Button
            type="link"
            size="small"
            onClick={() => setSelectedOperationId(record.operationId)}
          >
            查看
          </Button>
        ),
      },
    ],
    [],
  );

  const search = () => {
    void loadPage(1, pagination.pageSize, filters);
  };

  const reset = () => {
    const empty: FilterState = {};
    setFilters(empty);
    void loadPage(1, pagination.pageSize, empty);
  };

  const refresh = () => {
    void loadOptions();
    void loadPage(pagination.current, pagination.pageSize, filters);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="mb-3 rounded-lg border border-slate-200 bg-white p-3">
        <div className="flex flex-wrap items-center gap-2">
          <Input
            allowClear
            value={filters.keyword}
            onChange={(event) =>
              setFilters((current) => ({ ...current, keyword: event.target.value }))
            }
            onPressEnter={search}
            placeholder="操作名 / Operation ID / 资源 / 摘要"
            className="w-[260px]"
          />
          <DatePicker.RangePicker
            value={filters.timeRange}
            onChange={(value) =>
              setFilters((current) => ({
                ...current,
                timeRange:
                  value?.[0] && value?.[1] ? [value[0], value[1]] : undefined,
              }))
            }
            className="w-[260px]"
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={filters.projectId}
            options={selectOptions(options.projects)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, projectId: value }))
            }
            placeholder="项目空间"
            className="w-[150px]"
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={filters.actor}
            options={selectOptions(options.actors)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, actor: value }))
            }
            placeholder="操作人"
            className="w-[140px]"
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={filters.operationType}
            options={selectOptions(options.operationTypes)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, operationType: value }))
            }
            placeholder="操作类型"
            className="w-[170px]"
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            value={filters.resourceType}
            options={selectOptions(options.resourceTypes)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, resourceType: value }))
            }
            placeholder="资源类型"
            className="w-[150px]"
          />
          <Select
            allowClear
            value={filters.status}
            options={selectOptions(options.statuses, (option) => statusMeta(option.value).label)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, status: value }))
            }
            placeholder="状态"
            className="w-[110px]"
          />
          <Select
            allowClear
            value={filters.source}
            options={selectOptions(options.sources)}
            onChange={(value) =>
              setFilters((current) => ({ ...current, source: value }))
            }
            placeholder="来源"
            className="w-[110px]"
          />
          <Space size={6}>
            <Button type="primary" icon={<SearchOutlined />} onClick={search}>
              查询
            </Button>
            <Button onClick={reset}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={refresh} loading={loading}>
              刷新
            </Button>
          </Space>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-hidden rounded-lg border border-slate-200 bg-white">
        <SecurityQueryTable<AuditOperationSummary>
          rowKey="operationId"
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={false}
          scroll={{ x: 'max-content' }}
        />
      </div>

      <SecurityPagination
        current={pagination.current}
        pageSize={pagination.pageSize}
        total={pagination.total}
        disabled={loading}
        onChange={(page, pageSize) => {
          void loadPage(page, pageSize, filters);
        }}
      />

      <AuditOperationDetailDrawer
        operationId={selectedOperationId}
        open={Boolean(selectedOperationId)}
        onClose={() => setSelectedOperationId(undefined)}
      />
    </div>
  );
}
