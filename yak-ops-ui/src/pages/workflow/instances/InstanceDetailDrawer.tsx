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
import {
  Button,
  DatePicker,
  Drawer,
  Input,
  Modal,
  Popconfirm,
  Select,
  Table,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  CalendarDays,
  Pause,
  Play,
  RefreshCw,
  RotateCcw,
  Square,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import WorkflowDagView from './WorkflowDagView';

interface Props {
  open: boolean;
  executionId?: string;
  initial?: WorkflowInstance;
  onClose: () => void;
  onChanged?: (instance?: WorkflowInstance) => void;
}

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

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const JsonBlock = ({ value }: { value?: unknown }) => (
  <pre className="m-0 max-h-[220px] overflow-auto rounded-md bg-[#f7f7f8] p-2.5 text-[11px] leading-5 text-[rgba(22,24,35,.72)]">
    {JSON.stringify(value ?? {}, null, 2)}
  </pre>
);

const InstanceDetailDrawer = ({
  open,
  executionId,
  initial,
  onClose,
  onChanged,
}: Props) => {
  const streamRef = useRef<(() => void) | null>(null);
  const [detail, setDetail] = useState<WorkflowInstance>();
  const [operations, setOperations] = useState<WorkflowInstanceOperations>();
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string>();
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [rerunOpen, setRerunOpen] = useState(false);
  const [rerunDate, setRerunDate] = useState(dayjs());
  const [rerunStrategy, setRerunStrategy] = useState<'SERIAL_WAIT' | 'PARALLEL'>('SERIAL_WAIT');
  const [rerunInput, setRerunInput] = useState('{}');

  const applySnapshot = useCallback((snapshot: WorkflowInstance) => {
    setDetail(snapshot);
    onChanged?.(snapshot);
  }, [onChanged]);

  const attachStream = useCallback((snapshot: WorkflowInstance) => {
    streamRef.current?.();
    streamRef.current = null;
    if (!isWorkflowTerminal(snapshot.status)) {
      streamRef.current = subscribeWorkflowEvents(snapshot.id, applySnapshot);
    }
  }, [applySnapshot]);

  const load = useCallback(async () => {
    if (!open || !executionId) return;
    setLoading(true);
    try {
      const [instance, ops] = await Promise.all([
        getWorkflowInstance(executionId),
        getWorkflowInstanceOperations(executionId),
      ]);
      applySnapshot(instance);
      setOperations(ops);
      setRerunDate(dayjs(ops.businessDate || undefined));
      attachStream(instance);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '实例运维详情加载失败');
    } finally {
      setLoading(false);
    }
  }, [applySnapshot, attachStream, executionId, open]);

  useEffect(() => {
    if (!open) return;
    setDetail(initial);
    setOperations(undefined);
    setSelectedNodeId(undefined);
    void load();
    return () => {
      streamRef.current?.();
      streamRef.current = null;
    };
  }, [initial, load, open]);

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

  const activatePrepared = useCallback(async (prepared: WorkflowInstance, messageText: string) => {
    const activated = await activateWorkflowInstance(prepared.id);
    onChanged?.(activated);
    message.success(`${messageText}：${activated.id}`);
  }, [onChanged]);

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
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-[11px] text-[#667085]">
        <div>当前 Attempt：<span className="font-mono">{record.currentAttemptId || '-'}</span></div>
        <div>Retry：{record.retryMaxAttempts} 次 / 延迟 {record.retryDelaySeconds}s</div>
        <div>派发超时：{record.dispatchTimeoutSeconds > 0 ? `${record.dispatchTimeoutSeconds}s` : '-'}</div>
        <div>执行超时：{record.executionTimeoutSeconds > 0 ? `${record.executionTimeoutSeconds}s` : '-'}</div>
      </div>
      <div className="grid grid-cols-2 gap-3">
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

  const canRetryFailed = Boolean(
    detail &&
    isWorkflowTerminal(detail.status) &&
    detail.status !== 'SUCCESS' &&
    detail.nodes.some((node) => ['FAILED', 'UPSTREAM_FAILED', 'CANCELED', 'SKIPPED'].includes(node.status)),
  );

  return (
    <>
      <Drawer
        open={open}
        width={1120}
        loading={loading}
        title={detail ? `${detail.name} · 实例运维` : '实例运维'}
        onClose={() => {
          streamRef.current?.();
          streamRef.current = null;
          onClose();
        }}
        extra={detail ? (
          <div className="flex items-center gap-2">
            {detail.status === 'RUNNING' ? (
              <Button size="small" icon={<Pause size={13} />} loading={actionLoading === 'pause'} onClick={() => void runAction('pause', () => pauseWorkflowInstance(detail.id), '已请求暂停工作流')}>暂停</Button>
            ) : null}
            {detail.status === 'PAUSED' ? (
              <Button size="small" icon={<Play size={13} />} loading={actionLoading === 'resume'} onClick={() => void runAction('resume', () => resumeWorkflowInstance(detail.id), '已请求恢复工作流')}>恢复</Button>
            ) : null}
            {!isWorkflowTerminal(detail.status) ? (
              <Popconfirm title="取消当前工作流？" onConfirm={() => void runAction('cancel', () => cancelWorkflowInstance(detail.id), '工作流已取消')}>
                <Button danger size="small" icon={<Square size={12} />} loading={actionLoading === 'cancel'}>取消</Button>
              </Popconfirm>
            ) : null}
            {canRetryFailed ? (
              <Button size="small" icon={<RefreshCw size={12} />} loading={actionLoading === 'retryFailed'} onClick={() => void runAction('retryFailed', () => retryWorkflowFailedNodes(detail.id), '已在原实例重新调度失败/阻断节点')}>
                重试失败节点
              </Button>
            ) : null}
            {operations?.businessDateRerunSupported ? (
              <Button size="small" icon={<CalendarDays size={12} />} onClick={() => setRerunOpen(true)}>
                指定日期重跑
              </Button>
            ) : operations ? (
              <Tooltip title={operations.businessDateRerunUnavailableReason}>
                <span><Button disabled size="small" icon={<CalendarDays size={12} />}>指定日期重跑</Button></span>
              </Tooltip>
            ) : null}
            {isWorkflowTerminal(detail.status) ? (
              <Button size="small" icon={<RotateCcw size={12} />} loading={actionLoading === 'restart'} onClick={() => void handleRestart()}>
                整体重跑
              </Button>
            ) : null}
          </div>
        ) : null}
      >
        {detail ? (
          <div className="space-y-5">
            <div className="grid grid-cols-4 gap-x-6 gap-y-3 rounded-lg border border-[#eaecf0] bg-[#fcfcfd] px-4 py-3 text-[12px]">
              <div className="col-span-2"><span className="text-[#98a2b3]">实例 ID：</span><span className="font-mono text-[#344054]">{detail.id}</span></div>
              <div><span className="text-[#98a2b3]">状态：</span><span className={statusClassName(detail.status)}>{statusLabel[detail.status] || detail.status}</span></div>
              <div><span className="text-[#98a2b3]">版本：</span><span>V{detail.workflowVersionNo || '-'}</span></div>
              <div><span className="text-[#98a2b3]">触发来源：</span><span>{operations?.triggerType || '-'}</span></div>
              <div><span className="text-[#98a2b3]">businessDate：</span><span>{operations?.businessDate || '-'}</span></div>
              <div><span className="text-[#98a2b3]">scheduleTime：</span><span>{operations?.scheduleTime ? formatTime(operations.scheduleTime) : '-'}</span></div>
              <div><span className="text-[#98a2b3]">时区：</span><span>{operations?.scheduleTimezone || '-'}</span></div>
              <div><span className="text-[#98a2b3]">创建：</span><span>{formatTime(detail.startedAt)}</span></div>
              <div><span className="text-[#98a2b3]">结束：</span><span>{formatTime(detail.endedAt)}</span></div>
              {detail.sourceExecutionId ? (
                <div className="col-span-2"><span className="text-[#98a2b3]">来源实例：</span><span className="font-mono text-[#667085]">{detail.sourceExecutionId}</span></div>
              ) : null}
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <div className="text-[13px] font-medium text-[#344054]">运行实例 DAG</div>
                <div className="text-[11px] text-[#98a2b3]">点击节点可定位下方节点详情</div>
              </div>
              <WorkflowDagView
                instance={detail}
                operations={operations}
                selectedNodeId={selectedNodeId}
                onSelectNode={setSelectedNodeId}
              />
            </div>

            <div>
              <div className="mb-2 text-[13px] font-medium text-[#344054]">节点运行明细</div>
              <Table<WorkflowNodeInstance>
                rowKey="id"
                size="small"
                bordered
                pagination={false}
                dataSource={detail.nodes}
                columns={nodeColumns}
                rowClassName={(record) => record.id === selectedNodeId ? '!bg-[#f8f9fb]' : ''}
                expandable={{ expandedRowRender: renderNodeDetail, rowExpandable: () => true }}
                scroll={{ x: 820 }}
              />
            </div>

            <div>
              <div className="mb-2 text-[13px] font-medium text-[#344054]">Workflow Input</div>
              <JsonBlock value={detail.input} />
            </div>
          </div>
        ) : null}
      </Drawer>

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
            <Input.TextArea rows={6} spellCheck={false} className="font-mono text-[12px]" value={rerunInput} onChange={(event) => setRerunInput(event.target.value)} />
          </div>
        </div>
      </Modal>
    </>
  );
};

export default InstanceDetailDrawer;
