import {
  listWorkflowScheduleTriggers,
  type WorkflowSchedule,
  type WorkflowScheduleTrigger,
  type WorkflowScheduleTriggerStatus,
} from '@/services/workflow/schedules';
import { ReloadOutlined } from '@ant-design/icons';
import { Button, Drawer, Select, Table, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';

interface TriggerLedgerDrawerProps {
  open: boolean;
  schedule?: WorkflowSchedule;
  onClose: () => void;
}

const STATUS_LABEL: Record<WorkflowScheduleTriggerStatus, string> = {
  RECEIVED: '已接收',
  WAITING: '等待中',
  LAUNCHING: '启动中',
  RUNNING: '运行中',
  SUCCEEDED: '成功',
  FAILED: '失败',
  CANCELED: '已取消',
  SKIPPED: '已跳过',
};

const SOURCE_LABEL: Record<string, string> = {
  CRON: 'Cron',
  MANUAL: '手动触发',
  MISFIRE_RECOVERY: 'Misfire 恢复',
};

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const TriggerLedgerDrawer = ({ open, schedule, onClose }: TriggerLedgerDrawerProps) => {
  const [records, setRecords] = useState<WorkflowScheduleTrigger[]>([]);
  const [status, setStatus] = useState<WorkflowScheduleTriggerStatus>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!open || !schedule?.id) return;
    setLoading(true);
    try {
      setRecords(await listWorkflowScheduleTriggers({
        scheduleId: schedule.id,
        status,
        limit: 200,
      }));
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Trigger Ledger 加载失败');
    } finally {
      setLoading(false);
    }
  }, [open, schedule?.id, status]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  return (
    <Drawer
      open={open}
      width={980}
      title={
        <div>
          <div className="text-[14px] font-semibold text-[#344054]">Trigger 记录</div>
          <div className="mt-0.5 text-[11px] font-normal text-[#98a2b3]">
            {schedule?.name || '-'} · 每个计划时间最多产生一条 Ledger 记录
          </div>
        </div>
      }
      onClose={onClose}
      destroyOnClose
      extra={
        <div className="flex items-center gap-2">
          <Select
            allowClear
            placeholder="全部状态"
            className="w-[120px]"
            value={status}
            onChange={(value) => setStatus(value)}
            options={Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))}
          />
          <Button icon={<ReloadOutlined spin={loading} />} onClick={() => void load()} />
        </div>
      }
    >
      <div className="mb-3 rounded-sm bg-[#f8f9fb] px-3 py-2 text-[11px] leading-5 text-[#667085]">
        SERIAL_WAIT 会先进入 WAITING，前序 WorkflowExecution 终态提交后自动推进；
        SERIAL_DISCARD 会记为 SKIPPED；Misfire 的 FIRE_ONCE / SKIP 同样保留审计记录。
      </div>
      <Table
        rowKey="id"
        size="small"
        bordered
        loading={loading}
        dataSource={records}
        scroll={{ x: 1280 }}
        pagination={{ pageSize: 20, showSizeChanger: false, showTotal: (total) => `共 ${total} 条` }}
        columns={[
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            fixed: 'left',
            render: (value: WorkflowScheduleTriggerStatus) => (
              <span className="text-[12px] font-medium text-[#475467]">{STATUS_LABEL[value] || value}</span>
            ),
          },
          {
            title: '计划 / 实际触发',
            width: 205,
            render: (_: unknown, record: WorkflowScheduleTrigger) => (
              <div className="text-[11px] leading-5 text-[#667085]">
                <div>计划：{formatTime(record.plannedFireTime)}</div>
                <div>实际：{formatTime(record.actualFireTime)}</div>
              </div>
            ),
          },
          {
            title: '来源',
            dataIndex: 'triggerSource',
            width: 105,
            render: (value: string) => (
              <span className="text-[12px] text-[#667085]">{SOURCE_LABEL[value] || value}</span>
            ),
          },
          {
            title: '实例策略',
            dataIndex: 'executionStrategy',
            width: 120,
            render: (value: string) => <code className="text-[11px] text-[#475467]">{value}</code>,
          },
          {
            title: 'WorkflowExecution',
            dataIndex: 'workflowExecutionId',
            width: 230,
            render: (value?: string, record?: WorkflowScheduleTrigger) => (
              value
                ? <div><code className="text-[11px] text-[#475467]">{value}</code><div className="mt-1 text-[11px] text-[#98a2b3]">{record?.executionStatus || '-'}</div></div>
                : <span className="text-[11px] text-[#98a2b3]">尚未创建</span>
            ),
          },
          {
            title: '说明',
            dataIndex: 'message',
            width: 280,
            render: (value?: string, record?: WorkflowScheduleTrigger) => (
              <div className="text-[11px] leading-5 text-[#667085]">
                <div>{value || '-'}</div>
                {record?.errorMessage ? <div className="mt-1 text-[#b42318]">{record.errorMessage}</div> : null}
              </div>
            ),
          },
          {
            title: '完成时间',
            dataIndex: 'completedAt',
            width: 165,
            render: (value?: string) => <span className="text-[11px] text-[#98a2b3]">{formatTime(value)}</span>,
          },
        ]}
        className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!text-[#667085]"
      />
    </Drawer>
  );
};

export default TriggerLedgerDrawer;
