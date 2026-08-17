import { listWorkflowDefinitions, type WorkflowDefinition } from '@/services/workflow/definitions';
import {
  deleteWorkflowSchedule,
  listWorkflowSchedules,
  offlineWorkflowSchedule,
  onlineWorkflowSchedule,
  type WorkflowBackfill,
  type WorkflowSchedule,
} from '@/services/workflow/schedules';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, ConfigProvider, Input, Modal, Select, Table, Tooltip, message } from 'antd';
import { ArrowLeft, CalendarClock, DatabaseBackup, History, Pencil, Power, PowerOff, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import BackfillDrawer from './BackfillDrawer';
import BackfillHistoryDrawer from './BackfillHistoryDrawer';
import ScheduleEditorDrawer from './ScheduleEditorDrawer';
import TriggerLedgerDrawer from './TriggerLedgerDrawer';

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
  const scopedWorkflowId = useMemo(() => {
    const params = new URLSearchParams(history.location.search);
    return params.get('workflowId') || undefined;
  }, []);
  const scoped = Boolean(scopedWorkflowId);

  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [schedules, setSchedules] = useState<WorkflowSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [workflowId, setWorkflowId] = useState<string | undefined>(() => scopedWorkflowId);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<WorkflowSchedule>();
  const [ledgerOpen, setLedgerOpen] = useState(false);
  const [ledgerSchedule, setLedgerSchedule] = useState<WorkflowSchedule>();
  const [ledgerBackfill, setLedgerBackfill] = useState<WorkflowBackfill>();
  const [backfillOpen, setBackfillOpen] = useState(false);
  const [backfillSchedule, setBackfillSchedule] = useState<WorkflowSchedule>();
  const [backfillHistoryOpen, setBackfillHistoryOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [workflowData, scheduleData] = await Promise.all([
        listWorkflowDefinitions(),
        listWorkflowSchedules(scopedWorkflowId ? { workflowId: scopedWorkflowId } : undefined),
      ]);
      setDefinitions(workflowData || []);
      setSchedules(scheduleData || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '调度数据加载失败');
    } finally {
      setLoading(false);
    }
  }, [scopedWorkflowId]);

  useEffect(() => {
    void load();
  }, [load]);

  const workflowMap = useMemo(
    () => new Map(definitions.map((item) => [item.id, item])),
    [definitions],
  );

  const scopedWorkflow = scopedWorkflowId
    ? workflowMap.get(scopedWorkflowId)
    : undefined;

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
        message.success('调度已注册到 Yak Schedule');
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
      content: `即将删除「${schedule.name}」。调度定义会删除，历史 Trigger Ledger 与已创建 Backfill 批次会保留用于审计。`,
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

  const openNormalLedger = (schedule: WorkflowSchedule) => {
    setLedgerBackfill(undefined);
    setLedgerSchedule(schedule);
    setLedgerOpen(true);
  };

  const openBackfillLedger = (backfill: WorkflowBackfill) => {
    setLedgerBackfill(backfill);
    setLedgerSchedule(schedules.find((item) => item.id === backfill.scheduleId));
    setLedgerOpen(true);
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
      title: '最近 / 下次运行', dataIndex: 'nextFireTime', width: 215,
      render: (_: unknown, record: WorkflowSchedule) => (
        <div className="text-[11px] leading-5 text-[#667085]">
          <div>最近：{record.lastFireTime ? formatTime(record.lastFireTime) : '尚未触发'}</div>
          <div className={record.nextFireTime ? 'text-[#475467]' : 'text-[#98a2b3]'}>
            下次：{record.nextFireTime
              ? formatTime(record.nextFireTime)
              : record.status === 'ONLINE' ? '暂无可执行时间' : '未启用'}
          </div>
        </div>
      ),
    },
    {
      title: '更新时间', dataIndex: 'updateTime', width: 165,
      render: (value?: string) => <span className="text-[12px] text-[#98a2b3]">{formatTime(value)}</span>,
    },
    {
      title: '操作', dataIndex: 'operate', width: 380, fixed: 'right' as const,
      render: (_: unknown, record: WorkflowSchedule) => {
        const workflowOnline = workflowMap.get(record.workflowId)?.status === 'ONLINE';
        const online = record.status === 'ONLINE';
        return (
          <div className="flex items-center gap-0.5 whitespace-nowrap">
            <Tooltip title={!workflowOnline ? '工作流需先上线；调度本身可以处于停用状态' : undefined}>
              <span>
                <Button
                  type="text"
                  size="small"
                  disabled={!workflowOnline}
                  icon={<DatabaseBackup size={13} />}
                  onClick={() => { setBackfillSchedule(record); setBackfillOpen(true); }}
                >
                  补数
                </Button>
              </span>
            </Tooltip>
            <Button
              type="text"
              size="small"
              icon={<History size={13} />}
              onClick={() => openNormalLedger(record)}
            >
              触发记录
            </Button>
            <Tooltip title={online ? '已启用调度请先停用后再修改配置' : undefined}>
              <span>
                <Button
                  type="text"
                  size="small"
                  disabled={online}
                  icon={<Pencil size={13} />}
                  onClick={() => { setEditing(record); setEditorOpen(true); }}
                >
                  编辑
                </Button>
              </span>
            </Tooltip>
            <Tooltip title={!workflowOnline && !online ? '工作流需先上线' : undefined}>
              <span>
                <Button
                  type="text"
                  size="small"
                  disabled={!workflowOnline && !online}
                  icon={online ? <PowerOff size={13} /> : <Power size={13} />}
                  onClick={() => void changeStatus(record)}
                >
                  {online ? '停用' : '启用'}
                </Button>
              </span>
            </Tooltip>
            {!online && (
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
        <div className="flex items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-2.5">
            {scoped && (
              <Tooltip title="返回工作流定义">
                <Button
                  type="text"
                  size="small"
                  icon={<ArrowLeft size={15} />}
                  className="!h-8 !w-8 !px-0"
                  onClick={() => history.push('/workflow/definitions')}
                />
              </Tooltip>
            )}
            <div className="min-w-0">
              <h1 className="m-0 truncate text-[17px] font-semibold leading-8">
                {scoped ? '调度配置' : '调度管理'}
              </h1>
              <div className="truncate text-[11px] text-[#98a2b3]">
                {scoped
                  ? `${scopedWorkflow?.name || scopedWorkflowId} · ${scopedWorkflow?.status === 'ONLINE' ? '工作流已上线，可启用调度' : '工作流未上线，调度可配置但不能启用'}`
                  : 'Yak Schedule / Quartz · Trigger Ledger、Backfill、调度参数与恢复'}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button size="small" icon={<History size={14} />} onClick={() => setBackfillHistoryOpen(true)}>
              补数记录
            </Button>
            <Button danger type="primary" size="small" icon={<CalendarClock size={14} />} onClick={() => { setEditing(undefined); setEditorOpen(true); }}>
              新建调度
            </Button>
          </div>
        </div>

        <div className="mt-4 flex min-h-[52px] items-center justify-between gap-3 border-y border-[#f0f0f0]">
          <div className="flex items-center gap-2">
            <Select
              allowClear={!scoped}
              showSearch
              disabled={scoped}
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
            <Tooltip title="刷新调度运行时间">
              <Button icon={<ReloadOutlined spin={loading} />} onClick={() => void load()} />
            </Tooltip>
          </div>
        </div>

        <div className="mt-3 flex min-h-9 items-center rounded-sm bg-[#f8f9fb] px-3 text-[12px] text-[#475467]">
          <span><b>【调度参数】</b> 工作流上线后才能启用调度；Cron 与 Backfill 均按逻辑计划时间注入 businessDate / scheduleTime。</span>
        </div>

        <div className="mt-4 flex-1">
          <Table
            rowKey="id"
            bordered
            size="small"
            loading={loading}
            columns={columns as any}
            dataSource={filtered}
            scroll={{ x: 1740 }}
            pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: (total) => `共 ${total} 条` }}
            className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!text-[#667085] [&_.ant-table-tbody>tr>td]:!py-2.5"
          />
        </div>

        <ScheduleEditorDrawer
          open={editorOpen}
          definitions={definitions}
          schedule={editing}
          workflowId={scopedWorkflowId || workflowId}
          lockWorkflow={scoped}
          onClose={() => { setEditorOpen(false); setEditing(undefined); }}
          onSaved={load}
        />
        <BackfillDrawer
          open={backfillOpen}
          schedule={backfillSchedule}
          onClose={() => { setBackfillOpen(false); setBackfillSchedule(undefined); }}
          onCreated={async () => {
            await load();
            setBackfillHistoryOpen(true);
          }}
        />
        <BackfillHistoryDrawer
          open={backfillHistoryOpen}
          workflowId={workflowId}
          onClose={() => setBackfillHistoryOpen(false)}
          onOpenTriggers={openBackfillLedger}
        />
        <TriggerLedgerDrawer
          open={ledgerOpen}
          schedule={ledgerSchedule}
          backfillId={ledgerBackfill?.id}
          backfillName={ledgerBackfill?.name}
          onClose={() => {
            setLedgerOpen(false);
            setLedgerSchedule(undefined);
            setLedgerBackfill(undefined);
          }}
        />
      </div>
    </ConfigProvider>
  );
};

export default WorkflowSchedulesPage;