import {
  cancelWorkflowBackfill,
  listWorkflowBackfills,
  type WorkflowBackfill,
  type WorkflowBackfillStatus,
} from '@/services/workflow/schedules';
import { ReloadOutlined } from '@ant-design/icons';
import { Button, Drawer, Modal, Select, Table, message } from 'antd';
import { History, ListTree, XCircle } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

interface BackfillHistoryDrawerProps {
  open: boolean;
  workflowId?: string;
  scheduleId?: string;
  onClose: () => void;
  onOpenTriggers: (backfill: WorkflowBackfill) => void;
}

const STATUS_LABEL: Record<WorkflowBackfillStatus, string> = {
  CREATED: '已创建',
  RUNNING: '运行中',
  SUCCEEDED: '成功',
  PARTIAL_SUCCESS: '部分成功',
  FAILED: '失败',
  CANCELED: '已取消',
};

const OPERATION_LABEL: Record<string, string> = {
  BACKFILL: '历史补数',
  BUSINESS_DATE_RERUN: '实例运维补跑',
};

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const BackfillHistoryDrawer = ({
  open,
  workflowId,
  scheduleId,
  onClose,
  onOpenTriggers,
}: BackfillHistoryDrawerProps) => {
  const [records, setRecords] = useState<WorkflowBackfill[]>([]);
  const [status, setStatus] = useState<WorkflowBackfillStatus>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!open) return;
    setLoading(true);
    try {
      setRecords(await listWorkflowBackfills({ workflowId, scheduleId, status }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补数/补跑批次加载失败');
    } finally {
      setLoading(false);
    }
  }, [open, scheduleId, status, workflowId]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  const cancel = (record: WorkflowBackfill) => {
    Modal.confirm({
      centered: true,
      title: '确认取消批次吗？',
      content: '尚未启动的计划会被标记为已跳过；已经运行中的 WorkflowExecution 会继续完成。',
      okText: '取消批次',
      cancelText: '关闭',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await cancelWorkflowBackfill(record.id);
          message.success('批次已取消');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '取消批次失败');
        }
      },
    });
  };

  return (
    <Drawer
      open={open}
      width={1240}
      destroyOnClose
      title={
        <div>
          <div className="flex items-center gap-2 text-[14px] font-semibold text-[#344054]">
            <History size={15} /> 补数 / 运维补跑记录
          </div>
          <div className="mt-0.5 text-[11px] font-normal text-[#98a2b3]">
            Backfill 与指定 businessDate 重跑共享 Trigger Ledger，但保留独立 operationType 和来源实例血缘
          </div>
        </div>
      }
      onClose={onClose}
      extra={
        <div className="flex items-center gap-2">
          <Select
            allowClear
            placeholder="全部状态"
            className="w-[130px]"
            value={status}
            onChange={setStatus}
            options={Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))}
          />
          <Button icon={<ReloadOutlined spin={loading} />} onClick={() => void load()} />
        </div>
      }
    >
      <Table
        rowKey="id"
        size="small"
        bordered
        loading={loading}
        dataSource={records}
        scroll={{ x: 1710 }}
        pagination={{ pageSize: 15, showSizeChanger: false, showTotal: (total) => `共 ${total} 个批次` }}
        columns={[
          {
            title: '批次',
            dataIndex: 'name',
            width: 235,
            fixed: 'left',
            render: (value: string, record: WorkflowBackfill) => (
              <div>
                <div className="font-medium text-[#344054]">{value}</div>
                <div className="mt-1 text-[10px] text-[#98a2b3]">{record.id}</div>
              </div>
            ),
          },
          {
            title: '类型 / 来源',
            width: 215,
            render: (_: unknown, record: WorkflowBackfill) => (
              <div>
                <div className="text-[12px] font-medium text-[#475467]">
                  {OPERATION_LABEL[record.operationType] || record.operationType}
                </div>
                <div
                  className="mt-1 max-w-[190px] truncate font-mono text-[10px] text-[#98a2b3]"
                  title={record.sourceExecutionId}
                >
                  {record.sourceExecutionId ? `source: ${record.sourceExecutionId}` : '普通 Backfill'}
                </div>
              </div>
            ),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (value: WorkflowBackfillStatus) => (
              <span className="text-[12px] font-medium text-[#475467]">{STATUS_LABEL[value] || value}</span>
            ),
          },
          {
            title: '业务日期',
            width: 190,
            render: (_: unknown, record: WorkflowBackfill) => (
              <div className="text-[11px] text-[#667085]">
                {record.startBusinessDate} ~ {record.endBusinessDate}
              </div>
            ),
          },
          {
            title: '工作流版本',
            dataIndex: 'workflowVersionNo',
            width: 125,
            render: (value: number, record: WorkflowBackfill) => (
              <div>
                <div className="text-[12px] text-[#475467]">V{value}</div>
                <div className="mt-1 max-w-[105px] truncate text-[10px] text-[#98a2b3]" title={record.workflowVersionId}>
                  {record.workflowVersionId}
                </div>
              </div>
            ),
          },
          {
            title: '执行策略',
            dataIndex: 'executionStrategy',
            width: 115,
            render: (value: string) => <code className="text-[11px] text-[#475467]">{value}</code>,
          },
          {
            title: '进度',
            width: 250,
            render: (_: unknown, record: WorkflowBackfill) => (
              <div className="text-[11px] leading-5 text-[#667085]">
                <div>总数 {record.totalCount} · 等待 {record.waitingCount} · 运行 {record.runningCount}</div>
                <div>成功 {record.succeededCount} · 失败 {record.failedCount} · 跳过 {record.skippedCount}</div>
              </div>
            ),
          },
          {
            title: 'Cron / 时区',
            width: 180,
            render: (_: unknown, record: WorkflowBackfill) => (
              <div>
                <code className="text-[11px] text-[#475467]">{record.cronExpression}</code>
                <div className="mt-1 text-[10px] text-[#98a2b3]">{record.timezone}</div>
              </div>
            ),
          },
          {
            title: '创建时间',
            dataIndex: 'createTime',
            width: 165,
            render: (value: string) => <span className="text-[11px] text-[#98a2b3]">{formatTime(value)}</span>,
          },
          {
            title: '操作',
            width: 170,
            fixed: 'right',
            render: (_: unknown, record: WorkflowBackfill) => (
              <div className="flex items-center gap-1 whitespace-nowrap">
                <Button type="text" size="small" icon={<ListTree size={13} />} onClick={() => onOpenTriggers(record)}>
                  明细
                </Button>
                {record.status === 'RUNNING' ? (
                  <Button danger type="text" size="small" icon={<XCircle size={13} />} onClick={() => cancel(record)}>
                    取消
                  </Button>
                ) : null}
              </div>
            ),
          },
        ]}
        className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!text-[#667085]"
      />
    </Drawer>
  );
};

export default BackfillHistoryDrawer;
