import {
  batchRetryWorkflowInstances,
  getWorkflowInstances,
  isWorkflowTerminal,
  type WorkflowInstance,
} from '@/services/workflow';
import {
  CopyOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  Button,
  ConfigProvider,
  DatePicker,
  Empty,
  Input,
  Modal,
  Pagination,
  Select,
  Table,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import InstanceDetailDrawer from './InstanceDetailDrawer';

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
};

const RUNNING_STATUSES = new Set(['CREATED', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING']);
const COMPLETED_STATUSES = new Set(['SUCCESS', 'SUCCESS_WITH_WARNINGS', 'WARNING', 'CANCELED']);
const FAILED_STATUSES = new Set(['FAILED', 'TIMED_OUT']);
const RETRYABLE_NODE_STATUSES = new Set(['FAILED', 'UPSTREAM_FAILED', 'SKIPPED', 'CANCELED']);

type StatusGroup = 'ALL' | 'RUNNING' | 'COMPLETED' | 'FAILED';

const statusTabs: Array<{ label: string; value: StatusGroup }> = [
  { label: '全部实例', value: 'ALL' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
];

const formatTime = (value?: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const formatDuration = (record: WorkflowInstance) => {
  if (!record.startedAt) return '-';
  const seconds = Math.max(0, dayjs(record.endedAt || undefined).diff(dayjs(record.startedAt), 'second'));
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`;
};

const statusBadgeClassName = (status: string) => {
  if (FAILED_STATUSES.has(status)) return 'border-[#ffd6d6] bg-[#fff5f5] text-[#d92d20]';
  if (status === 'RUNNING' || status === 'RESUMING') return 'border-[#d0d5dd] bg-[#f8f9fb] text-[#344054]';
  return 'border-[#eaecf0] bg-[#fafafa] text-[#667085]';
};

const isRetryableInstance = (instance: WorkflowInstance) =>
  isWorkflowTerminal(instance.status)
  && instance.status !== 'SUCCESS'
  && instance.nodes.some((node) => RETRYABLE_NODE_STATUSES.has(node.status));

const WorkflowInstancesPage = () => {
  const [instances, setInstances] = useState<WorkflowInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusGroup, setStatusGroup] = useState<StatusGroup>('ALL');
  const [keyword, setKeyword] = useState('');
  const [dateRange, setDateRange] = useState<Dayjs[]>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [detailId, setDetailId] = useState<string>();
  const [detailInitial, setDetailInitial] = useState<WorkflowInstance>();
  const [batchLoading, setBatchLoading] = useState(false);

  const load = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setInstances(await getWorkflowInstances());
    } catch (error) {
      if (showLoading) message.error(error instanceof Error ? error.message : '工作流实例加载失败');
    } finally {
      if (showLoading) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(true);
    const timer = window.setInterval(() => void load(false), 2500);
    return () => window.clearInterval(timer);
  }, [load]);

  const matchesGroup = (status: string) => {
    if (statusGroup === 'ALL') return true;
    if (statusGroup === 'RUNNING') return RUNNING_STATUSES.has(status);
    if (statusGroup === 'COMPLETED') return COMPLETED_STATUSES.has(status);
    return FAILED_STATUSES.has(status);
  };

  const filtered = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    const [start, end] = dateRange || [];
    return instances.filter((record) => {
      if (!matchesGroup(record.status)) return false;
      if (q && ![record.name, record.id, record.definitionId, String(record.input?.businessDate || '')]
        .some((value) => value?.toLowerCase().includes(q))) return false;
      if (start && dayjs(record.startedAt).isBefore(start.startOf('day'))) return false;
      if (end && dayjs(record.startedAt).isAfter(end.endOf('day'))) return false;
      return true;
    });
  }, [dateRange, instances, keyword, statusGroup]);

  useEffect(() => {
    const maxPage = Math.max(1, Math.ceil(filtered.length / pageSize));
    if (page > maxPage) setPage(maxPage);
  }, [filtered.length, page, pageSize]);

  const pageData = useMemo(() => {
    const offset = (page - 1) * pageSize;
    return filtered.slice(offset, offset + pageSize);
  }, [filtered, page, pageSize]);

  const openDetail = (record: WorkflowInstance) => {
    setDetailInitial(record);
    setDetailId(record.id);
  };

  const copyId = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      message.success('实例 ID 已复制');
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  const handleSnapshot = useCallback((snapshot?: WorkflowInstance) => {
    if (!snapshot) {
      void load(false);
      return;
    }
    setInstances((current) => {
      const found = current.some((item) => item.id === snapshot.id);
      return found
        ? current.map((item) => item.id === snapshot.id ? snapshot : item)
        : [snapshot, ...current];
    });
  }, [load]);

  const batchRetry = async () => {
    if (!selectedKeys.length || batchLoading) return;
    setBatchLoading(true);
    try {
      const result = await batchRetryWorkflowInstances(selectedKeys.map(String));
      setSelectedKeys([]);
      await load(false);
      if (result.failedCount === 0) {
        message.success(`已重新调度 ${result.acceptedCount} 个失败实例`);
      } else {
        Modal.info({
          title: `批量重试完成：成功 ${result.acceptedCount}，失败 ${result.failedCount}`,
          width: 680,
          content: (
            <div className="mt-3 max-h-[320px] overflow-auto space-y-2">
              {result.items.filter((item) => !item.accepted).map((item) => (
                <div key={item.executionId} className="rounded-md border border-[#fecdca] bg-[#fff6f5] px-3 py-2 text-[12px]">
                  <div className="font-mono text-[#b42318]">{item.executionId}</div>
                  <div className="mt-1 text-[#667085]">{item.message || '重试失败'}</div>
                </div>
              ))}
            </div>
          ),
        });
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '批量重试失败');
    } finally {
      setBatchLoading(false);
    }
  };

  const columns = useMemo<ColumnsType<WorkflowInstance>>(() => [
    {
      title: '名称 / ID', dataIndex: 'name', width: 315,
      render: (_: unknown, record) => (
        <div className="min-w-0 py-0.5">
          <div className="truncate text-[13px] font-medium text-[#344054]">{record.name || '-'}</div>
          <div className="mt-0.5 flex items-center gap-1 text-[11px] text-[#98a2b3]">
            <span className="truncate">{record.id}</span>
            <Tooltip title="复制实例 ID">
              <Button type="text" size="small" icon={<CopyOutlined />} className="!h-5 !w-5 !min-w-0 !p-0" onClick={(event) => { event.stopPropagation(); void copyId(record.id); }} />
            </Tooltip>
          </div>
          <div className="truncate text-[11px] text-[#b0b7c3]">definition：{record.definitionId || '-'}</div>
        </div>
      ),
    },
    {
      title: '状态', dataIndex: 'status', width: 120, align: 'center',
      render: (status: string) => (
        <span className={`inline-flex min-h-6 items-center rounded-md border px-2 text-[12px] font-medium ${statusBadgeClassName(status)}`}>
          {statusLabel[status] || status}
        </span>
      ),
    },
    {
      title: '调度上下文', width: 190,
      render: (_: unknown, record) => (
        <div className="text-[11px] leading-5 text-[#667085]">
          <div><span className="text-[#98a2b3]">businessDate：</span>{String(record.input?.businessDate || '-')}</div>
          <div><span className="text-[#98a2b3]">trigger：</span>{String(record.input?.triggerType || (record.testRun ? 'TEST' : 'MANUAL'))}</div>
          <div><span className="text-[#98a2b3]">version：</span>{record.workflowVersionNo ? `V${record.workflowVersionNo}` : '-'}</div>
        </div>
      ),
    },
    {
      title: '执行概况', width: 175,
      render: (_: unknown, record) => (
        <div className="text-[12px] leading-5 text-[#667085]">
          <div>节点 / 连线：{record.nodeCount} / {record.edgeCount}</div>
          <div>运行时长：{formatDuration(record)}</div>
        </div>
      ),
    },
    { title: '开始时间', dataIndex: 'startedAt', width: 170, render: formatTime },
    { title: '结束时间', dataIndex: 'endedAt', width: 170, render: formatTime },
    {
      title: '操作', width: 88, fixed: 'right',
      render: (_: unknown, record) => <Button type="link" size="small" className="!px-0 !text-[12px]" onClick={() => openDetail(record)}>运维</Button>,
    },
  ], []);

  return (
    <ConfigProvider theme={{ token: { borderRadius: 9, colorBorder: '#eaecf0' }, components: { Input: { activeShadow: 'none' } } }}>
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4 text-[#161823]">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="m-0 text-[17px] font-semibold leading-8">工作流实例</h1>
            <div className="text-[11px] text-[#98a2b3]">运行实例 DAG、失败恢复、节点重跑与 businessDate 运维补跑</div>
          </div>
          <div className="flex items-center gap-2">
            {selectedKeys.length > 0 ? (
              <Button icon={<RefreshCw size={13} />} loading={batchLoading} onClick={() => void batchRetry()}>
                批量重试失败实例（{selectedKeys.length}）
              </Button>
            ) : null}
            <Button icon={<ReloadOutlined spin={loading} />} loading={loading} onClick={() => void load(true)}>刷新</Button>
          </div>
        </div>

        <div className="mt-4 flex min-h-[52px] items-center justify-between gap-3 border-y border-[#f0f0f0]">
          <div className="flex shrink-0 items-center gap-1 rounded-lg bg-[#f5f5f6] p-1">
            {statusTabs.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => { setStatusGroup(item.value); setPage(1); setSelectedKeys([]); }}
                className={`h-8 rounded-md px-3.5 text-[13px] font-medium ${statusGroup === item.value ? 'bg-white text-[#ff4d4f] shadow-[0_1px_4px_rgba(16,24,40,.08)]' : 'text-[#667085] hover:bg-white/70'}`}
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-2">
            <Input
              allowClear
              variant="filled"
              prefix={<SearchOutlined className="text-[#98a2b3]" />}
              placeholder="名称 / 实例 ID / businessDate"
              className="!w-[280px]"
              value={keyword}
              onChange={(event) => { setKeyword(event.target.value); setPage(1); }}
            />
            <RangePicker
              allowClear
              variant="filled"
              value={dateRange as any}
              onChange={(value) => { setDateRange(value ? value as unknown as Dayjs[] : undefined); setPage(1); }}
            />
          </div>
        </div>

        <div className="mt-3 flex min-h-9 items-center rounded-sm bg-[#f8f9fb] px-3 text-[12px] text-[#475467]">
          <span><b>【实例运维】</b> 单节点 Retry 在原实例恢复；“从此节点重跑”创建新实例并复用成功祖先结果；指定 businessDate 重跑固定来源发布版本。</span>
        </div>

        <div className="mt-4 flex-1">
          <Table<WorkflowInstance>
            rowKey="id"
            bordered
            size="small"
            pagination={false}
            loading={loading}
            dataSource={pageData}
            columns={columns}
            rowSelection={{
              selectedRowKeys: selectedKeys,
              onChange: setSelectedKeys,
              getCheckboxProps: (record) => ({
                disabled: !isRetryableInstance(record),
                title: isRetryableInstance(record) ? '加入批量失败恢复' : '当前实例没有可批量恢复的失败/阻断节点',
              }),
            }}
            scroll={{ x: 1260 }}
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无工作流实例" /> }}
            className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!text-[#667085] [&_.ant-table-tbody>tr>td]:!py-2.5"
          />
        </div>

        <div className="mt-auto flex min-h-[56px] items-center justify-between border-t border-[#eaecf0] bg-white py-3">
          <div className="text-[12px] text-[#98a2b3]">当前筛选 {filtered.length} 条 · 可批量恢复 {filtered.filter(isRetryableInstance).length} 条</div>
          <div className="flex items-center gap-3">
            <Pagination size="small" total={filtered.length} current={page} pageSize={pageSize} showSizeChanger={false} onChange={setPage} />
            <Select size="small" value={pageSize} className="w-[78px]" onChange={(value) => { setPageSize(value); setPage(1); }} options={[10, 20, 50].map((value) => ({ value, label: `${value} / 页` }))} />
          </div>
        </div>

        <InstanceDetailDrawer
          open={Boolean(detailId)}
          executionId={detailId}
          initial={detailInitial}
          onClose={() => { setDetailId(undefined); setDetailInitial(undefined); }}
          onChanged={handleSnapshot}
        />
      </div>
    </ConfigProvider>
  );
};

export default WorkflowInstancesPage;
