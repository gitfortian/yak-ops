import {
  activateWorkflowInstance,
  cancelWorkflowInstance,
  getWorkflowInstance,
  getWorkflowInstanceOperations,
  isWorkflowTerminal,
  pauseWorkflowInstance,
  rerunWorkflowBusinessDate,
  rerunWorkflowFromNode,
  restartWorkflowInstance,
  resumeWorkflowInstance,
  retryWorkflowFailedNode,
  retryWorkflowFailedNodes,
  subscribeWorkflowEvents,
  type WorkflowAttempt,
  type WorkflowInstance,
  type WorkflowInstanceOperations,
  type WorkflowNodeInstance,
} from '@/services/workflow';
import { BRAND_THEME } from '@/styles/brand';
import { history, useParams } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  DatePicker,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tabs,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  ArrowLeft,
  CalendarDays,
  Pause,
  Play,
  RefreshCw,
  RotateCcw,
  Square,
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type Key,
  type ReactNode,
} from 'react';
import WorkflowDagView from '../WorkflowDagView';

type DetailTabKey = 'overview' | 'dag' | 'nodes' | 'input';

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

const statusClassName = (status: string) => {
  if (status === 'FAILED' || status === 'TIMED_OUT') return 'text-[#d92d20]';
  if (status === 'RUNNING' || status === 'RESUMING' || status === 'SUBMITTED' || status === 'READY') {
    return 'font-medium text-[#344054]';
  }
  return 'text-[#667085]';
};

const statusDotClassName = (status: string) => {
  if (status === 'FAILED' || status === 'TIMED_OUT') return 'bg-[#f04438]';
  if (status === 'WARNING' || status === 'SUCCESS_WITH_WARNINGS') return 'bg-[#f79009]';
  if (status === 'RUNNING' || status === 'RESUMING' || status === 'SUCCESS') return 'bg-[#20c77a]';
  return 'bg-[#b0b5bd]';
};

const formatTime = (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const formatScheduleTime = (value?: string) => {
  if (!value) return '-';
  return value.replace('T', ' ');
};

const formatDuration = (record?: WorkflowInstance) => {
  if (!record?.startedAt) return '-';
  const seconds = Math.max(
    0,
    dayjs(record.endedAt || undefined).diff(dayjs(record.startedAt), 'second'),
  );
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`;
};

const JsonBlock = ({ value }: { value?: unknown }) => (
  <pre className="m-0 max-h-[420px] overflow-auto rounded-md bg-[#f7f7f8] p-3 text-[11px] leading-5 text-[rgba(22,24,35,.72)]">
    {JSON.stringify(value ?? {}, null, 2)}
  </pre>
);

const MetricTile = ({ label, value }: { label: string; value: ReactNode }) => (
  <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
    <div className="text-[12px] leading-4 text-[#7c828c]">{label}</div>
    <div className="mt-2 truncate text-[20px] font-semibold leading-7 tracking-[-0.02em] text-[#161823]">
      {value}
    </div>
  </div>
);

const InfoField = ({
  label,
  children,
  className = '',
}: {
  label: string;
  children: ReactNode;
  className?: string;
}) => (
  <div className={className}>
    <div className="text-[12px] text-[#8a8f98]">{label}</div>
    <div className="mt-2 min-w-0 break-words text-[14px] font-medium text-[#161823]">
      {children}
    </div>
  </div>
);

const SectionCard = ({
  title,
  extra,
  children,
  className = '',
}: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section className={`min-w-0 rounded-lg bg-white ${className}`}>
    <div className="flex min-h-[52px] items-center justify-between gap-4 px-5">
      <div className="text-[15px] font-semibold text-[#161823]">{title}</div>
      {extra ? <div className="text-[11px] text-[#98a2b3]">{extra}</div> : null}
    </div>
    {children}
  </section>
);

const WorkflowIllustration = () => (
  <div className="relative flex h-[116px] w-[116px] shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white">
    <svg
      width="80"
      height="80"
      viewBox="0 0 80 80"
      fill="none"
      aria-hidden="true"
      className="relative z-10 -translate-y-1"
      shapeRendering="crispEdges"
    >
      <path d="M24 24H40V28H24V24Z" fill="#161823" />
      <path d="M40 24H56V28H40V24Z" fill="#161823" />
      <path d="M38 28H42V42H38V28Z" fill="#161823" />
      <path d="M20 28H24V38H20V28Z" fill="#161823" />
      <path d="M56 28H60V38H56V28Z" fill="#161823" />
      <path d="M20 38H34V42H20V38Z" fill="#161823" />
      <path d="M46 38H60V42H46V38Z" fill="#161823" />
      <rect x="12" y="14" width="20" height="14" rx="2" fill="#F3F4F6" stroke="#161823" strokeWidth="4" />
      <rect x="30" y="40" width="20" height="14" rx="2" fill="#F3F4F6" stroke="#161823" strokeWidth="4" />
      <rect x="48" y="14" width="20" height="14" rx="2" fill="#FFF1F3" stroke="#FE2C55" strokeWidth="4" />
      <rect x="20" y="18" width="4" height="4" fill="#161823" />
      <rect x="38" y="44" width="4" height="4" fill="#FE2C55" />
      <rect x="56" y="18" width="4" height="4" fill="#FE2C55" />
      <path d="M40 54H44V66H40V54Z" fill="#161823" />
      <path d="M34 64H50V68H34V64Z" fill="#161823" />
    </svg>
    <div className="pointer-events-none absolute inset-x-0 bottom-0 z-20 h-[46px] bg-gradient-to-b from-transparent via-black/10 to-black/25" />
  </div>
);

export default function WorkflowInstanceDetailPage() {
  const params = useParams<{ executionId?: string }>();
  const executionId = params.executionId;
  const streamRef = useRef<(() => void) | null>(null);
  const [detail, setDetail] = useState<WorkflowInstance>();
  const [operations, setOperations] = useState<WorkflowInstanceOperations>();
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string>();
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [expandedNodeIds, setExpandedNodeIds] = useState<Key[]>([]);
  const [activeTab, setActiveTab] = useState<DetailTabKey>('overview');
  const [rerunOpen, setRerunOpen] = useState(false);
  const [rerunDate, setRerunDate] = useState(dayjs());
  const [rerunStrategy, setRerunStrategy] = useState<'SERIAL_WAIT' | 'PARALLEL'>('SERIAL_WAIT');
  const [rerunInput, setRerunInput] = useState('{}');

  const applySnapshot = useCallback((snapshot: WorkflowInstance) => {
    setDetail(snapshot);
  }, []);

  const attachStream = useCallback((snapshot: WorkflowInstance) => {
    streamRef.current?.();
    streamRef.current = null;
    if (!isWorkflowTerminal(snapshot.status)) {
      streamRef.current = subscribeWorkflowEvents(snapshot.id, applySnapshot);
    }
  }, [applySnapshot]);

  const load = useCallback(async () => {
    if (!executionId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [instance, ops] = await Promise.all([
        getWorkflowInstance(executionId),
        getWorkflowInstanceOperations(executionId),
      ]);
      applySnapshot(instance);
      setOperations(ops);
      setRerunDate(dayjs(ops.businessDate || undefined));
      setSelectedNodeId(undefined);
      setExpandedNodeIds([]);
      attachStream(instance);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '实例运维详情加载失败');
      setDetail(undefined);
    } finally {
      setLoading(false);
    }
  }, [applySnapshot, attachStream, executionId]);

  useEffect(() => {
    void load();
    return () => {
      streamRef.current?.();
      streamRef.current = null;
    };
  }, [load]);

  const runAction = useCallback(async (
    key: string,
    operation: () => Promise<WorkflowInstance>,
    success: string,
  ) => {
    if (actionLoading) return;
    setActionLoading(key);
    try {
      const snapshot = await operation();
      applySnapshot(snapshot);
      attachStream(snapshot);
      message.success(success);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '工作流运维操作失败');
    } finally {
      setActionLoading(undefined);
    }
  }, [actionLoading, applySnapshot, attachStream]);

  const activatePrepared = useCallback(async (prepared: WorkflowInstance, success: string) => {
    const activated = await activateWorkflowInstance(prepared.id);
    message.success(`${success}：${activated.id}`);
    history.push(`/workflow/instances/${encodeURIComponent(activated.id)}`);
  }, []);

  const handleRestart = async () => {
    if (!detail || actionLoading) return;
    setActionLoading('restart');
    try {
      await activatePrepared(await restartWorkflowInstance(detail.id), '已创建完整重跑实例');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '整体重跑失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const handleRerunFromNode = async (nodeId: string) => {
    if (!detail || actionLoading) return;
    setActionLoading(`rerun:${nodeId}`);
    try {
      await activatePrepared(
        await rerunWorkflowFromNode(detail.id, nodeId),
        `已从节点「${nodeId}」创建重跑实例`,
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '从指定节点重跑失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const handleRetryNode = async (nodeId: string) => {
    if (!detail) return;
    await runAction(
      `retryNode:${nodeId}`,
      () => retryWorkflowFailedNode(detail.id, nodeId),
      `失败节点「${nodeId}」已在原实例重新调度`,
    );
  };

  const handleBusinessDateRerun = async () => {
    if (!detail || !rerunDate || actionLoading) return;
    let input: Record<string, unknown> = {};
    try {
      input = rerunInput.trim() ? JSON.parse(rerunInput) : {};
    } catch {
      message.error('补跑参数必须是合法 JSON');
      return;
    }
    setActionLoading('businessDate');
    try {
      const batch = await rerunWorkflowBusinessDate(detail.id, {
        businessDate: rerunDate.format('YYYY-MM-DD'),
        executionStrategy: rerunStrategy,
        input,
      });
      message.success(`businessDate 补跑批次已创建，共 ${batch.totalCount} 个逻辑实例`);
      setRerunOpen(false);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '指定 businessDate 重跑失败');
    } finally {
      setActionLoading(undefined);
    }
  };

  const selectDagNode = (nodeId: string) => {
    setSelectedNodeId(nodeId);
    setExpandedNodeIds((current) => current.includes(nodeId) ? current : [...current, nodeId]);
    setActiveTab('nodes');
  };

  const attemptColumns = useMemo<ColumnsType<WorkflowAttempt>>(() => [
    { title: '#', dataIndex: 'attemptNumber', width: 48 },
    { title: '状态', dataIndex: 'status', width: 105, render: (value: string) => statusLabel[value] || value },
    {
      title: '失败原因', dataIndex: 'failureReason', width: 120,
      render: (value?: string) => value ? failureReasonLabel[value] || value : '-',
    },
    {
      title: 'Attempt ID', dataIndex: 'id',
      render: (value: string) => <span className="font-mono text-[10px] text-[#667085]">{value}</span>,
    },
    { title: '开始', dataIndex: 'startedAt', width: 155, render: formatTime },
    { title: '结束', dataIndex: 'endedAt', width: 155, render: formatTime },
  ], []);

  const renderNodeDetail = (record: WorkflowNodeInstance) => (
    <div className="space-y-4 bg-[#fafafa] p-3">
      {record.errorMessage ? (
        <div className="rounded-md border border-[#fecdca] bg-[#fff6f5] px-3 py-2 text-[11px] text-[#b42318]">
          {record.errorMessage}
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-x-6 gap-y-2 text-[11px] text-[#667085] sm:grid-cols-2">
        <div>当前 Attempt：<span className="font-mono">{record.currentAttemptId || '-'}</span></div>
        <div>Retry：{record.retryMaxAttempts} 次 / 延迟 {record.retryDelaySeconds}s</div>
        <div>派发超时：{record.dispatchTimeoutSeconds > 0 ? `${record.dispatchTimeoutSeconds}s` : '-'}</div>
        <div>执行超时：{record.executionTimeoutSeconds > 0 ? `${record.executionTimeoutSeconds}s` : '-'}</div>
      </div>
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Input Mapping</div><JsonBlock value={record.inputMapping} /></div>
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Resolved Node Input</div><JsonBlock value={record.input} /></div>
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Predecessor Outputs</div><JsonBlock value={record.predecessorOutputs} /></div>
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Node Output</div><JsonBlock value={record.output} /></div>
      </div>
      <div>
        <div className="mb-1.5 text-[11px] font-medium text-[#344054]">Attempt History</div>
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

  const nodeColumns = useMemo<ColumnsType<WorkflowNodeInstance>>(() => [
    {
      title: '节点', dataIndex: 'name', minWidth: 190,
      render: (_: unknown, record) => (
        <div>
          <div className="font-medium text-[#344054]">{record.name}</div>
          <div className="mt-0.5 text-[11px] text-[#98a2b3]">{record.type} · {record.id}</div>
        </div>
      ),
    },
    {
      title: '状态', dataIndex: 'status', width: 115,
      render: (value: string) => <span className={statusClassName(value)}>{statusLabel[value] || value}</span>,
    },
    {
      title: 'Attempt', width: 95,
      render: (_: unknown, record) => <span className="text-[12px] text-[#667085]">{record.currentAttemptNumber || 0} / {record.retryMaxAttempts}</span>,
    },
    {
      title: '失败原因', dataIndex: 'failureReason', width: 125,
      render: (value?: string, record?: WorkflowNodeInstance) => record?.status === 'UPSTREAM_FAILED' ? '未执行' : value ? failureReasonLabel[value] || value : '-',
    },
    {
      title: '运维操作', width: 220, fixed: 'right',
      render: (_: unknown, record) => {
        if (!detail || !isWorkflowTerminal(detail.status)) return '-';
        return (
          <div className="flex items-center gap-1">
            {record.status === 'FAILED' ? (
              <Button
                type="link"
                size="small"
                className="!px-1 !text-[12px]"
                loading={actionLoading === `retryNode:${record.id}`}
                onClick={() => void handleRetryNode(record.id)}
              >
                重试失败节点
              </Button>
            ) : null}
            <Button
              type="link"
              size="small"
              className="!px-1 !text-[12px]"
              loading={actionLoading === `rerun:${record.id}`}
              onClick={() => void handleRerunFromNode(record.id)}
            >
              从此节点重跑
            </Button>
          </div>
        );
      },
    },
  ], [actionLoading, detail]);

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Spin size="large" />
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到工作流实例">
          <Button onClick={() => history.push('/workflow/instances')}>返回工作流实例</Button>
        </Empty>
      </div>
    );
  }

  const canRetryFailed = Boolean(
    isWorkflowTerminal(detail.status)
    && detail.status !== 'SUCCESS'
    && detail.nodes.some((node) => ['FAILED', 'UPSTREAM_FAILED', 'CANCELED', 'SKIPPED'].includes(node.status)),
  );

  const sourceExecutionId = String(detail.input?.sourceExecutionId || detail.sourceExecutionId || '');
  const successfulNodes = detail.nodes.filter((node) => node.status === 'SUCCESS').length;
  const activeNodes = detail.nodes.filter((node) => ['RUNNING', 'READY', 'SUBMITTED', 'WAITING'].includes(node.status)).length;
  const failedNodes = detail.nodes.filter((node) => ['FAILED', 'UPSTREAM_FAILED', 'TIMED_OUT'].includes(node.status)).length;
  const totalAttempts = detail.nodes.reduce(
    (sum, node) => sum + (node.attemptCount || node.attempts?.length || 0),
    0,
  );
  const triggerType = operations?.triggerType || (detail.testRun ? 'TEST' : 'MANUAL');

  const overviewContent = (
    <div className="grid gap-3 xl:grid-cols-2">
      <SectionCard title="运行概览">
        <div className="grid grid-cols-2 gap-3 p-5 md:grid-cols-3">
          <MetricTile label="节点数" value={detail.nodeCount || detail.nodes.length} />
          <MetricTile label="成功节点" value={successfulNodes} />
          <MetricTile label="运行中节点" value={activeNodes} />
          <MetricTile label="异常节点" value={failedNodes} />
          <MetricTile label="Attempts" value={totalAttempts} />
          <MetricTile label="运行时长" value={formatDuration(detail)} />
        </div>
      </SectionCard>

      <SectionCard title="实例信息">
        <div className="grid grid-cols-1 gap-x-10 gap-y-6 p-5 sm:grid-cols-2">
          <InfoField label="实例 ID" className="sm:col-span-2">
            <span className="font-mono text-[12px] font-normal text-[#475467]">{detail.id}</span>
          </InfoField>
          <InfoField label="Definition ID">
            <span className="font-mono text-[12px] font-normal text-[#475467]">{detail.definitionId || '-'}</span>
          </InfoField>
          <InfoField label="版本">{detail.workflowVersionNo ? `V${detail.workflowVersionNo}` : '-'}</InfoField>
          <InfoField label="触发来源">{triggerType}</InfoField>
          <InfoField label="businessDate">{operations?.businessDate || String(detail.input?.businessDate || '-')}</InfoField>
          <InfoField label="scheduleTime">{formatScheduleTime(operations?.scheduleTime)}</InfoField>
          <InfoField label="时区">{operations?.scheduleTimezone || '-'}</InfoField>
          <InfoField label="开始时间">{formatTime(detail.startedAt)}</InfoField>
          <InfoField label="结束时间">{formatTime(detail.endedAt)}</InfoField>
          <InfoField label="失败策略">{detail.failureStrategy || '-'}</InfoField>
          <InfoField label="工作流超时">
            {detail.workflowTimeoutSeconds > 0 ? `${detail.workflowTimeoutSeconds}s` : '-'}
          </InfoField>
          {sourceExecutionId ? (
            <InfoField label="来源实例" className="sm:col-span-2">
              <span className="font-mono text-[12px] font-normal text-[#475467]">{sourceExecutionId}</span>
            </InfoField>
          ) : null}
        </div>
      </SectionCard>
    </div>
  );

  const dagContent = (
    <SectionCard
      title="运行实例 DAG"
      extra="点击节点会自动切换到节点明细并展开对应运行记录"
    >
      <div className="p-5 pt-1">
        <WorkflowDagView
          instance={detail}
          operations={operations}
          selectedNodeId={selectedNodeId}
          onSelectNode={selectDagNode}
        />
      </div>
    </SectionCard>
  );

  const nodesContent = (
    <SectionCard title="节点运行明细">
      <div className="p-5">
        <Table<WorkflowNodeInstance>
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={detail.nodes}
          columns={nodeColumns}
          rowClassName={(record) => record.id === selectedNodeId ? '!bg-[#f8f9fb]' : ''}
          expandable={{
            expandedRowRender: renderNodeDetail,
            rowExpandable: () => true,
            expandedRowKeys: expandedNodeIds,
            onExpandedRowsChange: (keys) => setExpandedNodeIds([...keys]),
          }}
          scroll={{ x: 820 }}
          className="[&_.ant-table-container]:!rounded-md [&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid [&_.ant-table-container]:!border-[#eceef1] [&_.ant-table-thead>tr>th]:!bg-[#f7f7f8] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-tbody>tr>td]:!py-3 [&_.ant-table-tbody>tr>td]:!text-[12px]"
        />
      </div>
    </SectionCard>
  );

  const inputContent = (
    <SectionCard title="Workflow Input">
      <div className="p-5 pt-1">
        <JsonBlock value={detail.input} />
      </div>
    </SectionCard>
  );

  const tabItems: Array<{
    key: DetailTabKey;
    label: string;
    children: ReactNode;
  }> = [
    { key: 'overview', label: '总览', children: overviewContent },
    { key: 'dag', label: '运行 DAG', children: dagContent },
    { key: 'nodes', label: '节点明细', children: nodesContent },
    { key: 'input', label: 'Workflow Input', children: inputContent },
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f7f8] text-[#161823]">
        <div className="mx-auto w-full max-w-[1800px] px-4 pb-8 pt-0 lg:px-5">
          <div className="mb-2 flex h-10 items-center">
            <Button
              type="text"
              icon={<ArrowLeft size={15} />}
              className="!h-9 !px-1 !text-[14px] !font-semibold !text-[#30343b]"
              onClick={() => history.push('/workflow/instances')}
            >
              返回工作流实例
            </Button>
          </div>

          <section className="rounded-lg bg-white">
            <div className="grid min-h-[176px] gap-6 px-5 py-6 lg:px-6 xl:grid-cols-[116px_minmax(0,1fr)_minmax(280px,auto)] xl:items-center">
              <WorkflowIllustration />
              <div className="min-w-0">
                <div className="max-w-[680px] truncate text-[14px] font-medium leading-5 text-[#161823]">
                  {detail.name || '未命名工作流实例'}
                </div>
                <div className="mt-1 text-[12px] leading-4 text-[#8a8f98]">
                  {formatTime(detail.startedAt)}
                </div>
                <div className="mt-1 flex items-center gap-1 text-[11px] leading-4 text-[#667085]">
                  <span className={`inline-block h-[10px] w-[10px] rounded-full ${statusDotClassName(detail.status)}`} />
                  <span>{statusLabel[detail.status] || detail.status}</span>
                </div>
                <div className="mt-2 flex min-w-0 items-center gap-2 text-[11px] leading-4 text-[#8a8f98]">
                  <span>实例</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span className="truncate font-mono">{detail.id}</span>
                </div>
                <div className="mt-1.5 flex min-w-0 items-center gap-1.5 text-[11px] leading-4 text-[#8a8f98]">
                  <span className="truncate">{detail.definitionId || 'Workflow'}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span>{detail.workflowVersionNo ? `V${detail.workflowVersionNo}` : 'V-'}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span>{triggerType}</span>
                  {operations?.businessDate ? (
                    <>
                      <span className="text-[#d0d5dd]">·</span>
                      <span>{operations.businessDate}</span>
                    </>
                  ) : null}
                </div>
              </div>

              <div className="flex min-w-0 flex-wrap items-center gap-2 xl:justify-end">
                {detail.status === 'RUNNING' ? (
                  <Button
                    icon={<Pause size={13} />}
                    loading={actionLoading === 'pause'}
                    onClick={() => void runAction('pause', () => pauseWorkflowInstance(detail.id), '已请求暂停工作流')}
                  >
                    暂停
                  </Button>
                ) : null}
                {detail.status === 'PAUSED' ? (
                  <Button
                    icon={<Play size={13} />}
                    loading={actionLoading === 'resume'}
                    onClick={() => void runAction('resume', () => resumeWorkflowInstance(detail.id), '已请求恢复工作流')}
                  >
                    恢复
                  </Button>
                ) : null}
                {!isWorkflowTerminal(detail.status) ? (
                  <Popconfirm
                    title="取消当前工作流？"
                    onConfirm={() => void runAction('cancel', () => cancelWorkflowInstance(detail.id), '工作流已取消')}
                  >
                    <Button danger icon={<Square size={12} />} loading={actionLoading === 'cancel'}>
                      取消
                    </Button>
                  </Popconfirm>
                ) : null}
                {canRetryFailed ? (
                  <Button
                    icon={<RefreshCw size={12} />}
                    loading={actionLoading === 'retryFailed'}
                    onClick={() => void runAction('retryFailed', () => retryWorkflowFailedNodes(detail.id), '已在原实例重新调度失败/阻断节点')}
                  >
                    重试失败节点
                  </Button>
                ) : null}
                {operations?.businessDateRerunSupported ? (
                  <Button icon={<CalendarDays size={12} />} onClick={() => setRerunOpen(true)}>
                    指定日期重跑
                  </Button>
                ) : operations ? (
                  <Tooltip title={operations.businessDateRerunUnavailableReason}>
                    <span><Button disabled icon={<CalendarDays size={12} />}>指定日期重跑</Button></span>
                  </Tooltip>
                ) : null}
                {isWorkflowTerminal(detail.status) ? (
                  <Button
                    type="primary"
                    icon={<RotateCcw size={12} />}
                    loading={actionLoading === 'restart'}
                    onClick={() => void handleRestart()}
                  >
                    整体重跑
                  </Button>
                ) : null}
              </div>
            </div>
          </section>

          <div className="px-5 lg:px-6">
            <Tabs
              activeKey={activeTab}
              onChange={(key) => setActiveTab(key as DetailTabKey)}
              items={tabItems.map(({ key, label }) => ({ key, label }))}
              className="[&_.ant-tabs-nav]:!mb-0 [&_.ant-tabs-nav]:!min-h-[50px] [&_.ant-tabs-tab]:!py-3.5"
            />
          </div>

          <div className="mt-3">
            {tabItems.find((item) => item.key === activeTab)?.children}
          </div>
        </div>
      </div>

      <Modal
        open={rerunOpen}
        title="指定 businessDate 重跑"
        okText="创建补跑"
        cancelText="取消"
        confirmLoading={actionLoading === 'businessDate'}
        onCancel={() => setRerunOpen(false)}
        onOk={() => void handleBusinessDateRerun()}
      >
        <div className="mb-4 rounded-md bg-[#f8f9fb] px-3 py-2 text-[11px] leading-5 text-[#667085]">
          使用来源实例固定的 Workflow Version、Cron 和时区生成新的历史逻辑时间；不会修改旧实例，也不会跟随当前 activeVersion。
        </div>
        <div className="space-y-4">
          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">businessDate</div>
            <DatePicker className="w-full" value={rerunDate} onChange={(value) => value && setRerunDate(value)} />
          </div>
          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">执行策略</div>
            <Select
              className="w-full"
              value={rerunStrategy}
              onChange={setRerunStrategy}
              options={[
                { value: 'SERIAL_WAIT', label: '串行等待（推荐）' },
                { value: 'PARALLEL', label: '允许并行' },
              ]}
            />
          </div>
          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">覆盖参数 JSON</div>
            <Input.TextArea
              rows={6}
              spellCheck={false}
              className="font-mono text-[12px]"
              value={rerunInput}
              onChange={(event) => setRerunInput(event.target.value)}
            />
          </div>
        </div>
      </Modal>
    </ConfigProvider>
  );
}
