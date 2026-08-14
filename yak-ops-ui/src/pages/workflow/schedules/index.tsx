import { listWorkflowDefinitions, type WorkflowDefinition } from '@/services/workflow/definitions';
import {
  deleteWorkflowSchedule,
  listWorkflowSchedules,
  offlineWorkflowSchedule,
  onlineWorkflowSchedule,
  type WorkflowSchedule,
} from '@/services/workflow/schedules';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, ConfigProvider, Input, Modal, Select, Table, Tooltip, message } from 'antd';
import { CalendarClock, Pencil, Power, PowerOff, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import ScheduleEditorDrawer from './ScheduleEditorDrawer';

type StatusFilter = 'ALL' | 'ONLINE' | 'OFFLINE';

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const STRATEGY_LABEL: Record<string, string> = {
  SERIAL_WAIT: '串行等待',
  SERIAL_DISCARD: '串行跳过',
  PARALLEL: '并行',
};

const WorkflowSchedulesPage = () => {
  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [schedules, setSchedules] = useState<WorkflowSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [workflowId, setWorkflowId] = useState<string>();
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<WorkflowSchedule>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [workflowData, scheduleData] = await Promise.all([
        listWorkflowDefinitions(),
        listWorkflowSchedules(),
      ]);
      setDefinitions(workflowData || []);
      setSchedules(scheduleData || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '调度数据加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const workflowMap = useMemo(
    () => new Map(definitions.map((item) => [item.id, item])),
    [definitions],
  );

  const filtered = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return schedules.filter((item) => {
      if (workflowId && item.workflowId !== workflowId) return false;
      if (status !== 'ALL' && item.status !== status) return false;
      if (!q) return true;
      const workflow = workflowMap.get(item.workflowId);
      return [item.name, item.cronExpression, item.timezone, workflow?.name || '']
        .some((value) => value.toLowerCase().includes(q));
    });
  }, [keyword, schedules, status, workflowId, workflowMap]);

  const changeStatus = async (schedule: WorkflowSchedule) => {
    try {
      if (schedule.status === 'ONLINE') {
        await offlineWorkflowSchedule(schedule.id);
        message.success('调度已停用');
      } else {
        await onlineWorkflowSchedule(schedule.id);
        message.success('调度定义已启用');
      }
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '调度状态变更失败');
    }
  };

  const removeSchedule = (schedule: WorkflowSchedule) => {
    Modal.confirm({
      centered: true,
      title: '确认删除调度吗？',
      content: `即将删除「${schedule.name}」，删除后无法恢复。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await deleteWorkflowSchedule(schedule.id);
          message.success('调度已删除');
          await load();
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除调度失败');
        }
      },
    });
  };

  const columns = [
    {
      title: '调度名称', dataIndex: 'name', width: 220,
      render: (value: string, record: WorkflowSchedule) => (
        <div><div className="font-medium text-[#344054]">{value}</div><div className="mt-1 text-[11px] text-[#98a2b3]">{record.id}</div></div>
      ),
    },
    {
      title: '工作流', dataIndex: 'workflowId', width: 210,
      render: (value: string) => {
        const workflow = workflowMap.get(value);
        return <div><div className="text-[13px] text-[#475467]">{workflow?.name || value}</div><div className="mt-1 text-[11px] text-[#98a2b3]">{workflow?.status || 'UNKNOWN'}</div></div>;
      },
    },
    {
      title: 'Cron / 时区', dataIndex: 'cronExpression', width: 190,
      render: (value: string, record: WorkflowSchedule) => (
        <div><code className="text-[12px] text-[#344054]">{value}</code><div className="mt-1 text-[11px] text-[#98a2b3]">{record.timezone}</div></div>
      ),
    },
    {
      title: '调度状态', dataIndex: 'status', width: 100, align: 'center' as const,
      render: (value: string) => (
        <span className={value === 'ONLINE' ? 'text-[12px] font-medium text-[#fe2c55]' : 'text-[12px] text-[#98a2b3]'}>
          {value === 'ONLINE' ? '已启用' : '已停用'}
        </span>
      ),
    },
    {
      title: '实例策略', dataIndex: 'executionStrategy', width: 110,
      render: (value: string) => <span className="text-[12px] text-[#667085]">{STRATEGY_LABEL[value] || value}</span>,
    },
    {
      title: '生效区间', dataIndex: 'startTime', width: 210,
      render: (_: unknown, record: WorkflowSchedule) => (
        <div className="text-[11px] leading-5 text-[#667085]">
          <div>{record.startTime ? formatTime(record.startTime) : '立即生效'}</div>
          <div>{record.endTime ? `至 ${formatTime(record.endTime)}` : '长期有效'}</div>
        </div>
      ),
    },
    {
      title: '下次运行', dataIndex: 'nextFireTime', width: 165,
      render: (value?: string) => value
        ? <span className="text-[12px] text-[#667085]">{formatTime(value)}</span>
        : <span className="text-[11px] text-[#98a2b3]">Stage 3 接入后计算</span>,
    },
    {
      title: '更新时间', dataIndex: 'updateTime', width: 165,
      render: (value?: string) => <span className="text-[12px] text-[#98a2b3]">{formatTime(value)}</span>,
    },
    {
      title: '操作', dataIndex: 'operate', width: 210, fixed: 'right' as const,
      render: (_: unknown, record: WorkflowSchedule) => {
        const workflowOnline = workflowMap.get(record.workflowId)?.status === 'ONLINE';
        return (
          <div className="flex items-center gap-0.5 whitespace-nowrap">
            <Button type="text" size="small" icon={<Pencil size={13} />} onClick={() => { setEditing(record); setEditorOpen(true); }}>编辑</Button>
            <Tooltip title={!workflowOnline && record.status !== 'ONLINE' ? '工作流需先上线' : undefined}>
              <span>
                <Button
                  type="text"
                  size="small"
                  disabled={!workflowOnline && record.status !== 'ONLINE'}
                  icon={record.status === 'ONLINE' ? <PowerOff size={13} /> : <Power size={13} />}
                  onClick={() => void changeStatus(record)}
                >
                  {record.status === 'ONLINE' ? '停用' : '启用'}
                </Button>
              </span>
            </Tooltip>
            {record.status === 'OFFLINE' && (
              <Button danger type="text" size="small" icon={<Trash2 size={13} />} onClick={() => removeSchedule(record)}>删除</Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <ConfigProvider theme={{ token: { borderRadius: 9, colorBorder: '#eaecf0' }, components: { Input: { activeShadow: 'none' } } }}>
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4 text-[#161823]">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="m-0 text-[17px] font-semibold leading-8">调度管理</h1>
            <div className="text-[11px] text-[#98a2b3]">管理工作流 Cron、时区、生效区间与实例策略</div>
          </div>
          <Button danger type="primary" size="small" icon={<CalendarClock size={14} />} onClick={() => { setEditing(undefined); setEditorOpen(true); }}>
            新建调度
          </Button>
        </div>

        <div className="mt-4 flex min-h-[52px] items-center justify-between gap-3 border-y border-[#f0f0f0]">
          <div className="flex items-center gap-2">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="全部工作流"
              className="w-[220px]"
              value={workflowId}
              onChange={setWorkflowId}
              options={definitions.map((item) => ({ value: item.id, label: item.name }))}
            />
            <Select
              value={status}
              className="w-[120px]"
              onChange={(value) => setStatus(value as StatusFilter)}
              options={[{ value: 'ALL', label: '全部状态' }, { value: 'ONLINE', label: '已启用' }, { value: 'OFFLINE', label: '已停用' }]}
            />
          </div>
          <div className="flex items-center gap-2">
            <Input allowClear variant="filled" prefix={<SearchOutlined className="text-[#98a2b3]" />} placeholder="搜索名称、Cron、时区" className="!w-[260px]" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
            <Button icon={<ReloadOutlined spin={loading} />} onClick={() => void load()} />
          </div>
        </div>

        <div className="mt-3 flex min-h-9 items-center rounded-sm bg-[#fff7e6] px-3 text-[12px] text-[#475467]">
          <span className="mr-2 text-[#faad14]">▲</span>
          <span><b>【Stage 2】</b> 当前启用/停用仅管理调度定义；自动触发、nextFireTime 与 Misfire 执行在 Stage 3 接入 Scheduler 后生效。</span>
        </div>

        <div className="mt-4 flex-1">
          <Table
            rowKey="id"
            bordered
            size="small"
            loading={loading}
            columns={columns as any}
            dataSource={filtered}
            scroll={{ x: 1480 }}
            pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: (total) => `共 ${total} 条` }}
            className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!text-[#667085] [&_.ant-table-tbody>tr>td]:!py-2.5"
          />
        </div>

        <ScheduleEditorDrawer
          open={editorOpen}
          definitions={definitions}
          schedule={editing}
          workflowId={workflowId}
          onClose={() => { setEditorOpen(false); setEditing(undefined); }}
          onSaved={load}
        />
      </div>
    </ConfigProvider>
  );
};

export default WorkflowSchedulesPage;
