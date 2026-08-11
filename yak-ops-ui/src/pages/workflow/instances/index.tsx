import {
  activateWorkflowInstance,
  cancelWorkflowInstance,
  getWorkflowInstance,
  getWorkflowInstances,
  isWorkflowTerminal,
  pauseWorkflowInstance,
  rerunWorkflowFromNode,
  restartWorkflowInstance,
  resumeWorkflowInstance,
  retryWorkflowFailedNodes,
  subscribeWorkflowEvents,
  type WorkflowAttempt,
  type WorkflowInstance,
  type WorkflowNodeInstance,
} from '@/services/workflow';
import {
  CopyOutlined,
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  Button,
  ConfigProvider,
  DatePicker,
  Divider,
  Drawer,
  Empty,
  Input,
  Pagination,
  Popconfirm,
  Popover,
  Select,
  Table,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import {
  Pause,
  Play,
  RefreshCw,
  RotateCcw,
  Square,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

const { RangePicker } = DatePicker;

const statusLabel: Record<string, string> = {
  CREATED: '已创建',
  RUNNING: '运行中',
  PAUSING: '暂停中',
  PAUSED: '已暂停',
  RESUMING: '恢复中',
  SUCCESS: '成功',
  SUCCESS_WITH_WARNINGS: '完成（有告警）',
  FAILED: '失败',
  WARNING: '告警',
  CANCELED: '已取消',
  TIMED_OUT: '已超时',
  WAITING: '等待中',
  READY: '就绪',
  SUBMITTED: '已提交',
  UPSTREAM_FAILED: '已阻断',
  SKIPPED: '已跳过',
};

const failureReasonLabel: Record<string, string> = {
  EXECUTOR_FAILURE: '执行器失败',
  DISPATCH_TIMEOUT: '派发超时',
  EXECUTION_TIMEOUT: '执行超时',
};

const RUNNING_STATUSES = new Set([
  'CREATED',
  'RUNNING',
  'PAUSING',
  'PAUSED',
  'RESUMING',
  'WAITING',
  'READY',
  'SUBMITTED',
]);

const COMPLETED_STATUSES = new Set([
  'SUCCESS',
  'SUCCESS_WITH_WARNINGS',
  'WARNING',
  'CANCELED',
]);

const FAILED_STATUSES = new Set(['FAILED', 'TIMED_OUT']);

type StatusGroup = 'ALL' | 'RUNNING' | 'COMPLETED' | 'FAILED';

interface FilterState {
  keyword?: string;
  instanceId?: string;
  definitionId?: string;
  testRun?: 'true' | 'false';
  startedAt?: Dayjs[];
}

interface PaginationState {
  current: number;
  pageSize: number;
}

const statusTabs: Array<{ label: string; value: StatusGroup }> = [
  { label: '全部实例', value: 'ALL' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
];

const PAGE_SIZE_OPTIONS = [10, 20, 50];

const statusClassName = (status: string) => {
  if (status === 'FAILED' || status === 'TIMED_OUT') {
    return 'text-[#d92d20]';
  }
  if (status === 'UPSTREAM_FAILED' || status === 'PAUSED' || status === 'PAUSING') {
    return 'text-[rgba(22,24,35,.46)]';
  }
  if (
    status === 'RUNNING' ||
    status === 'RESUMING' ||
    status === 'SUBMITTED' ||
    status === 'READY'
  ) {
    return 'font-medium text-[#161823]';
  }
  return 'text-[rgba(22,24,35,.58)]';
};

const statusBadgeClassName = (status: string) => {
  if (FAILED_STATUSES.has(status)) {
    return 'border-[#ffd6d6] bg-[#fff5f5] text-[#d92d20]';
  }
  if (status === 'RUNNING' || status === 'RESUMING') {
    return 'border-[#e4e7ec] bg-[#f5f5f6] text-[#344054]';
  }
  if (status === 'PAUSED' || status === 'PAUSING') {
    return 'border-[#eaecf0] bg-[#f8f9fb] text-[#667085]';
  }
  return 'border-[#eaecf0] bg-[#fafafa] text-[#667085]';
};

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const formatDuration = (record: WorkflowInstance) => {
  if (!record.startedAt) return '-';
  const start = dayjs(record.startedAt);
  const end = record.endedAt ? dayjs(record.endedAt) : dayjs();
  const seconds = Math.max(0, end.diff(start, 'second'));
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`;
  const hours = Math.floor(minutes / 60);
  return `${hours} 小时 ${minutes % 60} 分`;
};

const matchesStatusGroup = (status: string, group: StatusGroup) => {
  if (group === 'ALL') return true;
  if (group === 'RUNNING') return RUNNING_STATUSES.has(status);
  if (group === 'COMPLETED') return COMPLETED_STATUSES.has(status);
  return FAILED_STATUSES.has(status);
};

const JsonBlock = ({ value }: { value?: unknown }) => (
  <pre className="m-0 max-h-[220px] overflow-auto rounded-md bg-[#f7f7f8] p-2.5 text-[11px] leading-5 text-[rgba(22,24,35,.72)]">
    {JSON.stringify(value ?? {}, null, 2)}
  </pre>
);

interface ListPaginationProps {
  total: number;
  current: number;
  pageSize: number;
  onChange: (page: number, pageSize: number) => void;
}

const ListPagination = ({
  total,
  current,
  pageSize,
  onChange,
}: ListPaginationProps) => (
  <div className="flex items-center gap-3 text-[13px] text-[#667085]">
    <Pagination
      size="small"
      total={total}
      current={current}
      pageSize={pageSize}
      showSizeChanger={false}
      showQuickJumper={false}
      onChange={(page) => onChange(page, pageSize)}
    />
    <span className="whitespace-nowrap">每页显示：</span>
    <Select
      size="small"
      value={pageSize}
      options={PAGE_SIZE_OPTIONS.map((value) => ({ label: value, value }))}
      onChange={(nextPageSize) => onChange(1, nextPageSize)}
      className="w-[64px]"
    />
    <span className="whitespace-nowrap text-[#344054]">总 {total} 行</span>
  </div>
);

const WorkflowInstancesPage = () => {
  const detailStreamRef = useRef<(() => void) | null>(null);
  const [instances, setInstances] = useState<WorkflowInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<WorkflowInstance>();
  const [detailLoading, setDetailLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string>();
  const [statusGroup, setStatusGroup] = useState<StatusGroup>('ALL');
  const [filterDraft, setFilterDraft] = useState<FilterState>({});
  const [filters, setFilters] = useState<FilterState>({});
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [pagination, setPagination] = useState<PaginationState>({
    current: 1,
    pageSize: 10,
  });

  const loadInstances = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setInstances(await getWorkflowInstances());
    } catch (error) {
      if (showLoading) {
        message.error(error instanceof Error ? error.message : '工作流实例加载失败');
      }
    } finally {
      if (showLoading) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadInstances(true);
    const timer = window.setInterval(() => void loadInstances(false), 2000);
    return () => {
      window.clearInterval(timer);
      detailStreamRef.current?.();
    };
  }, [loadInstances]);

  const applyDetailSnapshot = useCallback((snapshot: WorkflowInstance) => {
    setDetail(snapshot);
    setInstances((current) => {
      const exists = current.some((item) => item.id === snapshot.id);
      return exists
        ? current.map((item) => (item.id === snapshot.id ? snapshot : item))
        : [snapshot, ...current];
    });
  }, []);

  const attachDetailStream = useCallback((instance: WorkflowInstance) => {
    detailStreamRef.current?.();
    detailStreamRef.current = null;
    if (!isWorkflowTerminal(instance.status)) {
      detailStreamRef.current = subscribeWorkflowEvents(
        instance.id,
        applyDetailSnapshot,
      );
    }
  }, [applyDetailSnapshot]);

  const openDetail = useCallback(async (record: WorkflowInstance) => {
    detailStreamRef.current?.();
    detailStreamRef.current = null;
    setDetail(record);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const current = await getWorkflowInstance(record.id);
      applyDetailSnapshot(current);
      attachDetailStream(current);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '实例详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  }, [applyDetailSnapshot, attachDetailStream]);

  const closeDetail = () => {
    detailStreamRef.current?.();
    detailStreamRef.current = null;
    setDetailOpen(false);
  };

  const runAction = useCallback(async (
    action: string,
    operation: () => Promise<WorkflowInstance>,
    successMessage: string,
  ) => {
    if (actionLoading) return;
    setActionLoading(action);
    try {
      const snapshot = await operation();
      applyDetailSnapshot(snapshot);
      attachDetailStream(snapshot);
      message.success(successMessage);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '工作流操作失败');
    } finally {
      setActionLoading(undefined);
    }
  }, [actionLoading, applyDetailSnapshot, attachDetailStream]);

  const activateNewExecution = useCallback(async (
    prepared: WorkflowInstance,
    successMessage: string,
  ) => {
    applyDetailSnapshot(prepared);
    attachDetailStream(prepared);
    const activated = await activateWorkflowInstance(prepared.id);
    applyDetailSnapshot(activated);
    message.success(successMessage);
  }, [applyDetailSnapshot, attachDetailStream]);

  const copyToClipboard = useCallback(async (value: string) => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(value);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = value;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      message.success('实例 ID 已复制');
    } catch {
      message.error('复制失败，请手动复制');
    }
  }, []);

  const applyFilters = useCallback((nextFilters: FilterState) => {
    setFilterDraft(nextFilters);
    setFilters(nextFilters);
    setPagination((previous) => ({ ...previous, current: 1 }));
  }, []);

  const handleSearch = () => {
    applyFilters({ ...filterDraft });
  };

  const handleStatusChange = (value: StatusGroup) => {
    setStatusGroup(value);
    setPagination((previous) => ({ ...previous, current: 1 }));
  };

  const updateFilterDraft = (
    field: keyof FilterState,
    value: FilterState[keyof FilterState],
  ) => {
    setFilterDraft((previous) => ({ ...previous, [field]: value }));
  };

  const handleAdvancedReset = () => {
    const nextFilters: FilterState = {
      ...filterDraft,
      instanceId: undefined,
      definitionId: undefined,
      testRun: undefined,
    };
    applyFilters(nextFilters);
  };

  const advancedFilterCount = [
    filterDraft.instanceId,
    filterDraft.definitionId,
    filterDraft.testRun,
  ].filter(Boolean).length;

  const filteredInstances = useMemo(() => {
    const keyword = filters.keyword?.trim().toLowerCase();
    const instanceId = filters.instanceId?.trim().toLowerCase();
    const definitionId = filters.definitionId?.trim().toLowerCase();
    const [rangeStart, rangeEnd] = filters.startedAt || [];

    return instances.filter((record) => {
      if (!matchesStatusGroup(record.status, statusGroup)) return false;

      if (
        keyword &&
        !record.name.toLowerCase().includes(keyword) &&
        !record.id.toLowerCase().includes(keyword)
      ) {
        return false;
      }

      if (instanceId && !record.id.toLowerCase().includes(instanceId)) {
        return false;
      }

      if (
        definitionId &&
        !record.definitionId?.toLowerCase().includes(definitionId)
      ) {
        return false;
      }

      if (
        filters.testRun &&
        String(record.testRun) !== filters.testRun
      ) {
        return false;
      }

      if (rangeStart || rangeEnd) {
        const startedAt = dayjs(record.startedAt);
        if (rangeStart && startedAt.isBefore(rangeStart.startOf('day'))) {
          return false;
        }
        if (rangeEnd && startedAt.isAfter(rangeEnd.endOf('day'))) {
          return false;
        }
      }

      return true;
    });
  }, [filters, instances, statusGroup]);

  useEffect(() => {
    const maxPage = Math.max(
      1,
      Math.ceil(filteredInstances.length / pagination.pageSize),
    );
    if (pagination.current > maxPage) {
      setPagination((previous) => ({ ...previous, current: maxPage }));
    }
  }, [filteredInstances.length, pagination.current, pagination.pageSize]);

  const pageInstances = useMemo(() => {
    const start = (pagination.current - 1) * pagination.pageSize;
    return filteredInstances.slice(start, start + pagination.pageSize);
  }, [filteredInstances, pagination.current, pagination.pageSize]);

  const columns = useMemo<ColumnsType<WorkflowInstance>>(
    () => [
      {
        title: '名称 / ID',
        dataIndex: 'name',
        width: 320,
        render: (_, record) => (
          <div className="min-w-0 py-0.5">
            <div
              className="truncate text-[13px] font-medium leading-5 text-[#344054]"
              title={record.name}
            >
              {record.name || '-'}
            </div>
            <div className="mt-0.5 flex h-5 items-center gap-1 text-[11px] leading-5 text-[#98a2b3]">
              <span className="truncate">ID：{record.id}</span>
              <Tooltip title="复制实例 ID">
                <Button
                  type="text"
                  size="small"
                  icon={<CopyOutlined className="text-[11px]" />}
                  className="!flex !h-5 !w-5 !min-w-0 !items-center !justify-center !p-0 !text-[#98a2b3] hover:!bg-[#f2f4f7] hover:!text-[#475467]"
                  onClick={(event) => {
                    event.stopPropagation();
                    void copyToClipboard(record.id);
                  }}
                />
              </Tooltip>
            </div>
            <div
              className="truncate text-[11px] leading-5 text-[#b0b7c3]"
              title={record.definitionId}
            >
              工作流：{record.definitionId || '-'}
            </div>
          </div>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 120,
        align: 'center',
        render: (status: string) => (
          <span
            className={[
              'inline-flex min-h-6 items-center rounded-md border px-2 text-[12px] font-medium',
              statusBadgeClassName(status),
            ].join(' ')}
          >
            {statusLabel[status] || status}
          </span>
        ),
      },
      {
        title: '执行概况',
        width: 210,
        render: (_, record) => (
          <div className="text-[12px] leading-5 text-[#667085]">
            <div>
              <span className="text-[#98a2b3]">节点 / 连线：</span>
              <span className="text-[#475467]">
                {record.nodeCount} / {record.edgeCount}
              </span>
            </div>
            <div>
              <span className="text-[#98a2b3]">运行时长：</span>
              <span className="text-[#475467]">{formatDuration(record)}</span>
            </div>
          </div>
        ),
      },
      {
        title: '工作流超时',
        dataIndex: 'workflowTimeoutSeconds',
        width: 130,
        render: (value: number) => (
          <span className="text-[12px] text-[#667085]">
            {value > 0 ? `${value}s` : '-'}
          </span>
        ),
      },
      {
        title: '开始时间',
        dataIndex: 'startedAt',
        width: 175,
        render: (value?: string) => (
          <span className="whitespace-nowrap text-[12px] text-[#98a2b3]">
            {formatTime(value)}
          </span>
        ),
      },
      {
        title: '结束时间',
        dataIndex: 'endedAt',
        width: 175,
        render: (value?: string) => (
          <span className="whitespace-nowrap text-[12px] text-[#98a2b3]">
            {formatTime(value)}
          </span>
        ),
      },
      {
        title: '操作',
        width: 100,
        fixed: 'right',
        render: (_, record) => (
          <Button
            type="link"
            className="!h-7 !px-0 !text-[12px] !text-[#ff4d4f]"
            onClick={() => openDetail(record)}
          >
            查看
          </Button>
        ),
      },
    ],
    [copyToClipboard, openDetail],
  );

  const handleRerunFromNode = useCallback(async (nodeId: string) => {
    if (!detail || actionLoading) return;
    setActionLoading(`rerun:${nodeId}`);
    try {
      const prepared = await rerunWorkflowFromNode(detail.id, nodeId);
      await activateNewExecution(prepared, `已从节点「${nodeId}」创建新的运行实例`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '从节点重跑失败');
    } finally {
      setActionLoading(undefined);
    }
  }, [actionLoading, activateNewExecution, detail]);

  const nodeColumns = useMemo<ColumnsType<WorkflowNodeInstance>>(
    () => [
      {
        title: '节点',
        dataIndex: 'name',
        minWidth: 200,
        render: (_, record) => (
          <div>
            <div className="font-medium text-[#161823]">{record.name}</div>
            <div className="mt-0.5 text-[11px] text-[rgba(22,24,35,.42)]">
              {record.type} · {record.id}
            </div>
          </div>
        ),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (status: string) => (
          <span className={statusClassName(status)}>
            {statusLabel[status] || status}
          </span>
        ),
      },
      {
        title: 'Attempt',
        width: 100,
        render: (_, record) => (
          <span className="text-[12px] text-[rgba(22,24,35,.62)]">
            {record.currentAttemptNumber
              ? `${record.currentAttemptNumber} / ${record.retryMaxAttempts}`
              : `0 / ${record.retryMaxAttempts}`}
          </span>
        ),
      },
      {
        title: '失败原因',
        dataIndex: 'failureReason',
        width: 120,
        render: (value?: string, record?: WorkflowNodeInstance) => {
          if (record?.status === 'UPSTREAM_FAILED') return '未执行';
          return value ? failureReasonLabel[value] || value : '-';
        },
      },
      {
        title: '操作',
        width: 110,
        render: (_, record) => (
          detail && isWorkflowTerminal(detail.status) ? (
            <Button
              type="link"
              className="!px-0 !text-[12px]"
              loading={actionLoading === `rerun:${record.id}`}
              onClick={() => void handleRerunFromNode(record.id)}
            >
              从此节点重跑
            </Button>
          ) : '-'
        ),
      },
    ],
    [actionLoading, detail, handleRerunFromNode],
  );

  const attemptColumns = useMemo<ColumnsType<WorkflowAttempt>>(
    () => [
      { title: '#', dataIndex: 'attemptNumber', width: 48 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (status: string) => statusLabel[status] || status,
      },
      {
        title: '失败原因',
        dataIndex: 'failureReason',
        width: 120,
        render: (value?: string) => value ? failureReasonLabel[value] || value : '-',
      },
      {
        title: 'Attempt ID',
        dataIndex: 'id',
        render: (value: string) => (
          <span className="font-mono text-[10px] text-[rgba(22,24,35,.56)]">
            {value}
          </span>
        ),
      },
      {
        title: '开始',
        dataIndex: 'startedAt',
        width: 155,
        render: formatTime,
      },
      {
        title: '结束',
        dataIndex: 'endedAt',
        width: 155,
        render: formatTime,
      },
    ],
    [],
  );

  const renderNodeDetail = (record: WorkflowNodeInstance) => (
    <div className="space-y-4 bg-[#fafafa] p-3">
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-[11px]">
        <div>
          <span className="text-[rgba(22,24,35,.42)]">当前 Attempt：</span>
          <span className="font-mono text-[rgba(22,24,35,.68)]">
            {record.currentAttemptId || '-'}
          </span>
        </div>
        <div>
          <span className="text-[rgba(22,24,35,.42)]">Retry：</span>
          <span>{record.retryMaxAttempts} 次 / 延迟 {record.retryDelaySeconds}s</span>
        </div>
        <div>
          <span className="text-[rgba(22,24,35,.42)]">派发超时：</span>
          <span>
            {record.dispatchTimeoutSeconds > 0
              ? `${record.dispatchTimeoutSeconds}s`
              : '-'}
          </span>
        </div>
        <div>
          <span className="text-[rgba(22,24,35,.42)]">执行超时：</span>
          <span>
            {record.executionTimeoutSeconds > 0
              ? `${record.executionTimeoutSeconds}s`
              : '-'}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#161823]">
            Input Mapping
          </div>
          <JsonBlock value={record.inputMapping} />
        </div>
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#161823]">
            Resolved Node Input
          </div>
          <JsonBlock value={record.input} />
        </div>
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#161823]">
            Direct Predecessor Outputs
          </div>
          <JsonBlock value={record.predecessorOutputs} />
        </div>
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#161823]">
            Node Output
          </div>
          <JsonBlock value={record.output} />
        </div>
      </div>

      <div>
        <div className="mb-1.5 text-[11px] font-medium text-[#161823]">
          Attempt History
        </div>
        <Table<WorkflowAttempt>
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={record.attempts}
          columns={attemptColumns}
          scroll={{ x: 780 }}
        />
      </div>
    </div>
  );

  const canRetryFailed = Boolean(
    detail &&
    isWorkflowTerminal(detail.status) &&
    detail.status !== 'SUCCESS' &&
    detail.nodes.some((node) =>
      node.status === 'FAILED' ||
      node.status === 'UPSTREAM_FAILED' ||
      node.status === 'CANCELED',
    ),
  );

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 10,
          colorBorder: '#f0f0f0',
          colorBgContainer: '#ffffff',
        },
        components: {
          Button: {
            borderRadius: 8,
          },
          Input: {
            activeShadow: 'none',
          },
          Select: {
            activeOutlineColor: 'transparent',
          },
        },
      }}
    >
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
        <h1 className="m-0 text-[17px] font-semibold text-[#161823]">
          工作流实例
        </h1>

        <div className="mx-auto flex w-full max-w-full flex-1 flex-col">
          <div className="mb-3">
            <div className="border-b border-[#f0f0f0]">
              <div className="flex min-h-[54px] items-center justify-between gap-4 py-2">
                <div className="flex shrink-0 items-center gap-1 rounded-lg bg-[#f5f5f6] p-1">
                  {statusTabs.map((item) => {
                    const active = statusGroup === item.value;
                    return (
                      <button
                        key={item.value}
                        type="button"
                        onClick={() => handleStatusChange(item.value)}
                        className={[
                          'h-8 rounded-md px-3.5 text-[13px] font-medium transition-all',
                          active
                            ? 'bg-white text-[#ff4d4f] shadow-[0_1px_4px_rgba(16,24,40,0.08)]'
                            : 'text-[#667085] hover:bg-white/70 hover:text-[#344054]',
                        ].join(' ')}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>

                <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
                  <Input
                    allowClear
                    variant="filled"
                    value={filterDraft.keyword}
                    prefix={<SearchOutlined className="text-[#98a2b3]" />}
                    placeholder="搜索工作流名称 / 实例 ID"
                    className="!h-9 !w-[260px] !min-w-[220px]"
                    onChange={(event) =>
                      updateFilterDraft(
                        'keyword',
                        event.target.value || undefined,
                      )
                    }
                    onPressEnter={handleSearch}
                  />

                  <RangePicker
                    allowClear
                    variant="filled"
                    value={filterDraft.startedAt as any}
                    format="YYYY-MM-DD"
                    placeholder={['开始日期', '结束日期']}
                    className="!h-9 !w-[250px] !min-w-[230px]"
                    onChange={(value) => {
                      const nextFilters = {
                        ...filterDraft,
                        startedAt: value
                          ? (value as unknown as Dayjs[])
                          : undefined,
                      };
                      applyFilters(nextFilters);
                    }}
                  />

                  <Button
                    size="small"
                    className="!h-9 !px-4"
                    onClick={handleSearch}
                  >
                    查询
                  </Button>

                  <Popover
                    trigger="click"
                    placement="bottomRight"
                    open={advancedOpen}
                    onOpenChange={setAdvancedOpen}
                    content={
                      <div className="w-[430px]">
                        <div className="mb-4">
                          <div className="text-[14px] font-semibold text-[#101828]">
                            高级搜索
                          </div>
                          <div className="mt-1 text-[12px] text-[#98a2b3]">
                            按实例标识、工作流定义和运行类型进一步筛选
                          </div>
                        </div>

                        <div className="grid grid-cols-2 gap-x-3 gap-y-4">
                          <div>
                            <div className="mb-1.5 text-[12px] text-[#667085]">
                              实例 ID
                            </div>
                            <Input
                              allowClear
                              variant="filled"
                              value={filterDraft.instanceId}
                              placeholder="请输入实例 ID"
                              onChange={(event) =>
                                updateFilterDraft(
                                  'instanceId',
                                  event.target.value || undefined,
                                )
                              }
                              onPressEnter={() => {
                                handleSearch();
                                setAdvancedOpen(false);
                              }}
                            />
                          </div>

                          <div>
                            <div className="mb-1.5 text-[12px] text-[#667085]">
                              工作流定义 ID
                            </div>
                            <Input
                              allowClear
                              variant="filled"
                              value={filterDraft.definitionId}
                              placeholder="请输入工作流定义 ID"
                              onChange={(event) =>
                                updateFilterDraft(
                                  'definitionId',
                                  event.target.value || undefined,
                                )
                              }
                              onPressEnter={() => {
                                handleSearch();
                                setAdvancedOpen(false);
                              }}
                            />
                          </div>

                          <div>
                            <div className="mb-1.5 text-[12px] text-[#667085]">
                              运行类型
                            </div>
                            <Select
                              allowClear
                              variant="filled"
                              value={filterDraft.testRun}
                              placeholder="全部运行"
                              className="w-full"
                              options={[
                                { label: '正式运行', value: 'false' },
                                { label: '测试运行', value: 'true' },
                              ]}
                              onChange={(value) =>
                                updateFilterDraft('testRun', value)
                              }
                            />
                          </div>
                        </div>

                        <div className="mt-5 flex items-center justify-end gap-2 border-t border-[#f0f0f0] pt-4">
                          <Button
                            size="small"
                            className="!h-8"
                            onClick={handleAdvancedReset}
                          >
                            重置
                          </Button>
                          <Button
                            danger
                            type="primary"
                            size="small"
                            className="!h-8"
                            onClick={() => {
                              handleSearch();
                              setAdvancedOpen(false);
                            }}
                          >
                            应用筛选
                          </Button>
                        </div>
                      </div>
                    }
                  >
                    <Button
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
                      {advancedFilterCount > 0 && (
                        <span className="ml-1.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[#ff4d4f] px-1 text-[10px] leading-[18px] text-white">
                          {advancedFilterCount}
                        </span>
                      )}
                    </Button>
                  </Popover>
                </div>
              </div>
            </div>

            <div className="flex min-h-[48px] items-center justify-between">
              <div className="text-[12px] text-[#98a2b3]">
                当前筛选结果 {filteredInstances.length} 条
              </div>
              <Button
                size="small"
                icon={<ReloadOutlined spin={loading} />}
                className="!h-8"
                loading={loading}
                onClick={() => void loadInstances(true)}
              >
                刷新
              </Button>
            </div>

            <div className="flex min-h-9 items-center rounded-sm bg-[#fff7e6] px-3 text-[12px] text-[#475467]">
              <span className="mr-2 text-[14px] text-[#faad14]">▲</span>
              <span className="font-medium text-[#344054]">【提示】</span>
              <span>
                运行中的实例会自动刷新；进入详情后可执行暂停、恢复、取消、重试和重跑等操作。
              </span>
            </div>
          </div>

          <Divider style={{ marginTop: 4, marginBottom: 16 }} />

          <div className="flex-1">
            <Table<WorkflowInstance>
              rowKey="id"
              bordered
              size="small"
              pagination={false}
              loading={loading}
              dataSource={pageInstances}
              columns={columns}
              scroll={{ x: 'max-content' }}
              className={[
                'compact-workflow-instance-table',
                '[&_.ant-table]:!text-[13px]',
                '[&_.ant-table-container]:!border-[#eaecf0]',
                '[&_.ant-table-cell]:!align-middle',
                '[&_.ant-table-thead>tr>th]:!h-10',
                '[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb]',
                '[&_.ant-table-thead>tr>th]:!px-4',
                '[&_.ant-table-thead>tr>th]:!py-2',
                '[&_.ant-table-thead>tr>th]:!text-[12px]',
                '[&_.ant-table-thead>tr>th]:!font-medium',
                '[&_.ant-table-thead>tr>th]:!text-[#667085]',
                '[&_.ant-table-thead>tr>th]:!border-[#eaecf0]',
                '[&_.ant-table-tbody>tr>td]:!px-4',
                '[&_.ant-table-tbody>tr>td]:!py-2.5',
                '[&_.ant-table-tbody>tr>td]:!border-[#f0f2f5]',
                '[&_.ant-table-tbody>tr>td]:!text-[#667085]',
                '[&_.ant-table-tbody>tr:hover>td]:!bg-[#fafbfc]',
                '[&_.ant-table-cell-fix-right]:!bg-white',
                '[&_.ant-table-tbody>tr:hover_.ant-table-cell-fix-right]:!bg-[#fafbfc]',
                '[&_.ant-table-placeholder>td]:!h-[240px]',
              ].join(' ')}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      <span className="text-[12px] text-[#98a2b3]">
                        暂无工作流实例
                      </span>
                    }
                  />
                ),
              }}
            />
          </div>

          <div className="sticky bottom-0 z-20 mt-auto flex min-h-[56px] items-center justify-end border border-t-0 border-[#e5e7eb] bg-white px-5 py-3 shadow-[0_-4px_12px_rgba(16,24,40,0.04)]">
            <ListPagination
              total={filteredInstances.length}
              current={pagination.current}
              pageSize={pagination.pageSize}
              onChange={(current, pageSize) =>
                setPagination({ current, pageSize })
              }
            />
          </div>
        </div>

        <Drawer
          title={detail ? `${detail.name} · 实例详情` : '实例详情'}
          width={980}
          open={detailOpen}
          loading={detailLoading}
          onClose={closeDetail}
          extra={detail ? (
            <div className="flex items-center gap-2">
              {detail.status === 'RUNNING' ? (
                <Button
                  size="small"
                  icon={<Pause size={13} />}
                  loading={actionLoading === 'pause'}
                  onClick={() => void runAction(
                    'pause',
                    () => pauseWorkflowInstance(detail.id),
                    '已请求暂停工作流',
                  )}
                >
                  暂停
                </Button>
              ) : null}
              {detail.status === 'PAUSED' ? (
                <Button
                  size="small"
                  icon={<Play size={13} />}
                  loading={actionLoading === 'resume'}
                  onClick={() => void runAction(
                    'resume',
                    () => resumeWorkflowInstance(detail.id),
                    '已请求恢复工作流',
                  )}
                >
                  恢复
                </Button>
              ) : null}
              {!isWorkflowTerminal(detail.status) ? (
                <Popconfirm
                  title="取消当前工作流？"
                  onConfirm={() => void runAction(
                    'cancel',
                    () => cancelWorkflowInstance(detail.id),
                    '工作流已取消',
                  )}
                >
                  <Button
                    size="small"
                    danger
                    icon={<Square size={12} />}
                    loading={actionLoading === 'cancel'}
                  >
                    取消
                  </Button>
                </Popconfirm>
              ) : null}
              {canRetryFailed ? (
                <Button
                  size="small"
                  icon={<RefreshCw size={12} />}
                  loading={actionLoading === 'retryFailed'}
                  onClick={() => void runAction(
                    'retryFailed',
                    () => retryWorkflowFailedNodes(detail.id),
                    '已重新调度失败/阻断节点',
                  )}
                >
                  重试失败节点
                </Button>
              ) : null}
              {isWorkflowTerminal(detail.status) ? (
                <Button
                  size="small"
                  icon={<RotateCcw size={12} />}
                  loading={actionLoading === 'restart'}
                  onClick={() => void (async () => {
                    if (actionLoading) return;
                    setActionLoading('restart');
                    try {
                      const prepared = await restartWorkflowInstance(detail.id);
                      await activateNewExecution(
                        prepared,
                        '已创建并启动新的完整运行实例',
                      );
                    } catch (error) {
                      message.error(
                        error instanceof Error ? error.message : '整体重跑失败',
                      );
                    } finally {
                      setActionLoading(undefined);
                    }
                  })()}
                >
                  整体重跑
                </Button>
              ) : null}
            </div>
          ) : null}
        >
          {detail ? (
            <>
              <div className="mb-4 grid grid-cols-3 gap-x-8 gap-y-3 text-[12px]">
                <div className="col-span-2">
                  <span className="text-[rgba(22,24,35,.45)]">实例 ID：</span>
                  <span className="font-mono text-[#161823]">{detail.id}</span>
                </div>
                <div>
                  <span className="text-[rgba(22,24,35,.45)]">状态：</span>
                  <span className={statusClassName(detail.status)}>
                    {statusLabel[detail.status] || detail.status}
                  </span>
                </div>
                <div>
                  <span className="text-[rgba(22,24,35,.45)]">创建：</span>
                  <span>{formatTime(detail.startedAt)}</span>
                </div>
                <div>
                  <span className="text-[rgba(22,24,35,.45)]">
                    当前运行段：
                  </span>
                  <span>{formatTime(detail.runStartedAt)}</span>
                </div>
                <div>
                  <span className="text-[rgba(22,24,35,.45)]">结束：</span>
                  <span>{formatTime(detail.endedAt)}</span>
                </div>
                <div>
                  <span className="text-[rgba(22,24,35,.45)]">
                    工作流超时：
                  </span>
                  <span>
                    {detail.workflowTimeoutSeconds > 0
                      ? `${detail.workflowTimeoutSeconds}s`
                      : '-'}
                  </span>
                </div>
                {detail.sourceExecutionId ? (
                  <div className="col-span-2">
                    <span className="text-[rgba(22,24,35,.45)]">
                      来源实例：
                    </span>
                    <span className="font-mono text-[rgba(22,24,35,.68)]">
                      {detail.sourceExecutionId}
                    </span>
                  </div>
                ) : null}
              </div>

              <div className="mb-4">
                <div className="mb-1.5 text-[11px] font-medium text-[#161823]">
                  Workflow Input
                </div>
                <JsonBlock value={detail.input} />
              </div>

              <Table<WorkflowNodeInstance>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={detail.nodes}
                columns={nodeColumns}
                scroll={{ x: 760 }}
                expandable={{
                  expandedRowRender: renderNodeDetail,
                  rowExpandable: () => true,
                }}
              />
            </>
          ) : null}
        </Drawer>
      </div>
    </ConfigProvider>
  );
};

export default WorkflowInstancesPage;
