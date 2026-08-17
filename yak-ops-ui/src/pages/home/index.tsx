import { history } from '@umijs/max';
import { Button, Skeleton, Tooltip } from 'antd';
import dayjs from 'dayjs';
import {
  Activity,
  Bell,
  Cable,
  CalendarClock,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Database,
  PauseCircle,
  PlayCircle,
  RefreshCw,
  Server,
  Sparkles,
  Workflow,
  XCircle,
  type LucideIcon,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import type {
  HomeDataSourceKey,
  HomeOverview,
  HomeRunItem,
  HomeScheduleItem,
} from './model';
import { fetchHomeOverview } from './service';

type ActionTone = 'sync' | 'realtime' | 'workflow' | 'resource';

interface QuickAction {
  title: string;
  description: string;
  tone: ActionTone;
  icon: LucideIcon;
  path: string;
}

const HOME_CARD_CLASS =
  'rounded-[10px] border border-[rgba(22,24,35,0.055)] bg-white';

const actionToneClasses: Record<ActionTone, string> = {
  sync: 'bg-[#fff1f3] text-[#fe2c55]',
  realtime: 'bg-[#eef7ff] text-[#1677ff]',
  workflow: 'bg-[#f3f0ff] text-[#7652ff]',
  resource: 'bg-[#fff7e8] text-[#f59e0b]',
};

const quickActions: QuickAction[] = [
  {
    title: '新建离线同步',
    description: '配置来源、目标与字段映射',
    tone: 'sync',
    icon: Database,
    path: '/sync/batch-link-up/create',
  },
  {
    title: '新建实时同步',
    description: '创建持续运行的实时数据链路',
    tone: 'realtime',
    icon: Activity,
    path: '/sync/realtime-link-up/create',
  },
  {
    title: '新建工作流',
    description: '编排任务依赖、节点与执行顺序',
    tone: 'workflow',
    icon: Workflow,
    path: '/workflow-management/create',
  },
  {
    title: '接入数据源',
    description: '统一管理数据库与外部系统连接',
    tone: 'resource',
    icon: Cable,
    path: '/data-source/create',
  },
];

const sourceLabels: Record<HomeDataSourceKey, string> = {
  dataSource: '数据源',
  client: '客户端',
  alarm: '告警',
  execution: '运行实例',
  schedule: '调度计划',
};

const navigate = (path: string) => history.push(path);

const formatCount = (value?: number) =>
  typeof value === 'number' ? value.toLocaleString('zh-CN') : '--';

const formatTime = (value?: string) => {
  if (!value) return '-';
  const time = dayjs(value);
  return time.isValid() ? time.format('YYYY-MM-DD HH:mm:ss') : value;
};

const formatPlanTime = (value?: string) => {
  if (!value) return '-';
  const time = dayjs(value);
  if (!time.isValid()) return value;
  const today = dayjs();
  if (time.isSame(today, 'day')) return `今天 ${time.format('HH:mm')}`;
  if (time.isSame(today.add(1, 'day'), 'day')) return `明天 ${time.format('HH:mm')}`;
  return time.format('MM-DD HH:mm');
};

const formatDuration = (value?: number) => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) return '-';
  if (value < 1000) return `${Math.round(value)} ms`;
  const seconds = Math.round(value / 1000);
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  if (minutes < 60) return restSeconds ? `${minutes} 分 ${restSeconds} 秒` : `${minutes} 分`;
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  return restMinutes ? `${hours} 小时 ${restMinutes} 分` : `${hours} 小时`;
};

const getRunStatusMeta = (status?: string) => {
  const normalized = status?.trim().toUpperCase() || '';
  if (['SUCCESS', 'SUCCEEDED', 'SUCCESS_WITH_WARNINGS', 'COMPLETED', 'FINISHED'].includes(normalized)) {
    return { label: '成功', className: 'bg-[#f2f4f7] text-[#344054]' };
  }
  if (['FAILED', 'ERROR', 'TIMED_OUT', 'TIMEOUT'].includes(normalized)) {
    return { label: '失败', className: 'bg-[#fff1f3] text-[#d92d50]' };
  }
  if (['RUNNING', 'STARTING', 'SUBMITTED', 'DISPATCHING', 'QUEUED', 'PENDING', 'WAITING', 'RETRYING', 'PAUSED'].includes(normalized)) {
    return {
      label: normalized === 'PAUSED' ? '已暂停' : '运行中',
      className: 'bg-[#eef7ff] text-[#175cd3]',
    };
  }
  if (['CANCELED', 'CANCELLED'].includes(normalized)) {
    return { label: '已取消', className: 'bg-[#f5f5f6] text-[rgba(22,24,35,0.55)]' };
  }
  return {
    label: status || '状态未知',
    className: 'bg-[#f5f5f6] text-[rgba(22,24,35,0.55)]',
  };
};

function SectionHeader({
  title,
  description,
  extra,
}: {
  title: string;
  description?: string;
  extra?: ReactNode;
}) {
  return (
    <div className="flex min-h-8 items-center justify-between gap-4">
      <div className="min-w-0">
        <h2 className="m-0 text-lg font-[650] tracking-[-0.2px] text-[#161823]">{title}</h2>
        {description ? (
          <p className="mb-0 mt-1 text-xs leading-5 text-[rgba(22,24,35,0.46)]">{description}</p>
        ) : null}
      </div>
      {extra}
    </div>
  );
}

function DataState({
  unavailable,
  loading,
  children,
}: {
  unavailable: boolean;
  loading: boolean;
  children: ReactNode;
}) {
  if (loading) return <Skeleton.Input active size="small" style={{ width: 72 }} />;
  if (unavailable) {
    return (
      <Tooltip title="当前接口未能返回有效数据">
        <span className="text-[13px] font-medium text-[rgba(22,24,35,0.38)]">暂不可用</span>
      </Tooltip>
    );
  }
  return <>{children}</>;
}

function MetricCard({
  icon: Icon,
  label,
  value,
  detail,
  loading,
  unavailable,
  onClick,
}: {
  icon: LucideIcon;
  label: string;
  value?: number;
  detail: ReactNode;
  loading: boolean;
  unavailable: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`${HOME_CARD_CLASS} group flex min-h-[108px] min-w-0 cursor-pointer items-center px-4 py-4 text-left transition hover:border-[rgba(22,24,35,0.11)]`}
      onClick={onClick}
    >
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[9px] bg-[#f5f5f6] text-[#161823]">
        <Icon size={20} strokeWidth={1.9} />
      </span>
      <span className="ml-3 min-w-0 flex-1">
        <span className="block text-xs text-[rgba(22,24,35,0.48)]">{label}</span>
        <span className="mt-1 flex min-h-8 items-center">
          <DataState unavailable={unavailable} loading={loading}>
            <strong className="text-[25px] font-[650] leading-8 tracking-[-0.5px] text-[#161823]">{formatCount(value)}</strong>
          </DataState>
        </span>
        <span className="mt-0.5 block truncate text-[11px] text-[rgba(22,24,35,0.42)]">
          {loading || unavailable ? '来自 Yak Ops 实际接口' : detail}
        </span>
      </span>
      <ChevronRight size={15} className="ml-1 shrink-0 text-[rgba(22,24,35,0.2)]" />
    </button>
  );
}

function QuickActionCard({ action }: { action: QuickAction }) {
  const Icon = action.icon;
  return (
    <button
      type="button"
      className={`relative flex h-[66px] min-w-0 cursor-pointer items-center overflow-hidden rounded-[9px] border-0 px-4 text-left transition hover:-translate-y-px ${actionToneClasses[action.tone]}`}
      onClick={() => navigate(action.path)}
    >
      <span className="relative z-[1] flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-current text-white">
        <Icon className="text-white" size={20} strokeWidth={2.1} />
      </span>
      <span className="relative z-[1] ml-2.5 min-w-0">
        <strong className="block truncate text-sm font-[650] text-[#161823]">{action.title}</strong>
        <span className="mt-[3px] block truncate text-[11px] text-[rgba(22,24,35,0.5)]">{action.description}</span>
      </span>
    </button>
  );
}

function SmallValue({ label, value }: { label: string; value?: number }) {
  return (
    <div className="min-w-0 rounded-lg bg-[#f8f8f9] px-3 py-3">
      <div className="text-[11px] text-[rgba(22,24,35,0.42)]">{label}</div>
      <div className="mt-1 text-[15px] font-[650] text-[#161823]">{formatCount(value)}</div>
    </div>
  );
}

function OverviewValue({
  icon: Icon,
  label,
  value,
  loading,
  unavailable,
}: {
  icon: LucideIcon;
  label: string;
  value?: number;
  loading: boolean;
  unavailable: boolean;
}) {
  return (
    <div className="min-w-0 rounded-[10px] border border-[rgba(22,24,35,0.055)] bg-[#fafafa] px-4 py-4">
      <div className="flex items-center gap-2 text-[11px] text-[rgba(22,24,35,0.44)]">
        <Icon size={14} strokeWidth={1.9} />
        <span>{label}</span>
      </div>
      <div className="mt-2 flex min-h-7 items-center">
        <DataState loading={loading} unavailable={unavailable}>
          <strong className="text-[22px] font-[650] leading-7 tracking-[-0.4px] text-[#161823]">{formatCount(value)}</strong>
        </DataState>
      </div>
    </div>
  );
}

function RecentRunRow({ item }: { item: HomeRunItem }) {
  const status = getRunStatusMeta(item.status);
  return (
    <button
      type="button"
      className="group flex w-full min-w-0 cursor-pointer items-center gap-3 border-0 bg-transparent py-3 text-left first:pt-0 last:pb-0"
      onClick={() => navigate(item.path)}
    >
      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${item.type === 'batch' ? 'bg-[#fff1f3] text-[#fe2c55]' : 'bg-[#f3f0ff] text-[#7652ff]'}`}>
        {item.type === 'batch' ? <Database size={17} /> : <Workflow size={17} />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="truncate text-[12px] font-[650] text-[#161823]">{item.name}</strong>
          <span className="shrink-0 rounded bg-[#f5f5f6] px-1.5 py-0.5 text-[9px] text-[rgba(22,24,35,0.5)]">
            {item.type === 'batch' ? '离线同步' : '工作流'}
          </span>
          <span className={`shrink-0 rounded px-1.5 py-0.5 text-[9px] ${status.className}`}>{status.label}</span>
        </span>
        <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] text-[rgba(22,24,35,0.4)]">
          <span>{formatTime(item.startedAt)}</span>
          <span>耗时 {formatDuration(item.durationMillis)}</span>
        </span>
      </span>
      <ChevronRight size={14} className="shrink-0 text-[rgba(22,24,35,0.18)]" />
    </button>
  );
}

function ScheduleRow({ item }: { item: HomeScheduleItem }) {
  return (
    <button
      type="button"
      className="group flex w-full min-w-0 cursor-pointer items-center gap-3 border-0 bg-transparent py-3 text-left first:pt-0 last:pb-0"
      onClick={() => navigate(item.path)}
    >
      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${item.type === 'batch' ? 'bg-[#fff1f3] text-[#fe2c55]' : 'bg-[#f3f0ff] text-[#7652ff]'}`}>
        {item.type === 'batch' ? <Database size={17} /> : <Workflow size={17} />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <strong className="truncate text-[12px] font-[650] text-[#161823]">{item.name}</strong>
          <span className="shrink-0 rounded bg-[#f5f5f6] px-1.5 py-0.5 text-[9px] text-[rgba(22,24,35,0.5)]">
            {item.type === 'batch' ? '离线同步' : '工作流'}
          </span>
        </span>
        <span className="mt-1 flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-[10px] text-[rgba(22,24,35,0.4)]">
          <span className="font-medium text-[rgba(22,24,35,0.62)]">{formatPlanTime(item.nextRunAt)}</span>
          {item.cronExpression ? <span className="max-w-[180px] truncate font-mono">{item.cronExpression}</span> : null}
          {item.timezone ? <span>{item.timezone}</span> : null}
        </span>
      </span>
      <ChevronRight size={14} className="shrink-0 text-[rgba(22,24,35,0.18)]" />
    </button>
  );
}

function DeferredSection() {
  return (
    <section className={`${HOME_CARD_CLASS} mt-4 p-[22px]`}>
      <SectionHeader title="趋势与 SLA" />
      <div className="mt-4 flex min-h-[68px] items-center rounded-lg border border-dashed border-[rgba(22,24,35,0.09)] bg-[#fafafa] px-5">
        <Activity size={18} className="shrink-0 text-[rgba(22,24,35,0.36)]" />
        <div className="ml-3 min-w-0">
          <div className="text-[13px] font-[650] text-[rgba(22,24,35,0.72)]">等待统一统计口径</div>
          <div className="mt-1 text-[11px] text-[rgba(22,24,35,0.42)]">运行趋势、成功率、SLA 与吞吐量将在统一历史统计口径后接入。</div>
        </div>
      </div>
    </section>
  );
}

const HomePage = () => {
  const [overview, setOverview] = useState<HomeOverview>();
  const [loading, setLoading] = useState(true);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date>();
  const [loadError, setLoadError] = useState(false);

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const nextOverview = await fetchHomeOverview();
      setOverview(nextOverview);
      setLastUpdatedAt(new Date());
    } catch {
      setOverview({
        unavailable: ['dataSource', 'client', 'alarm', 'execution', 'schedule'],
      });
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  const unavailableSet = useMemo(
    () => new Set(overview?.unavailable ?? []),
    [overview?.unavailable],
  );

  const unavailableText = useMemo(() => {
    if (loadError) return '首页数据加载失败';
    if (!overview?.unavailable.length) return '';
    return `${overview.unavailable.map((key) => sourceLabels[key]).join('、')}数据暂不可用`;
  }, [loadError, overview?.unavailable]);

  const clientDetail = useMemo(() => {
    const client = overview?.client;
    if (!client) return '来自客户端统计接口';
    if (client.online !== undefined || client.offline !== undefined) {
      return `在线 ${formatCount(client.online)} / 离线 ${formatCount(client.offline)}`;
    }
    return '来自客户端统计接口';
  }, [overview?.client]);

  const scrollTo = (id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const dataSource = overview?.dataSource;
  const client = overview?.client;
  const alarm = overview?.alarm;
  const execution = overview?.execution;
  const schedule = overview?.schedule;
  const executionUnavailable = unavailableSet.has('execution');
  const scheduleUnavailable = unavailableSet.has('schedule');

  return (
    <div className="min-h-full bg-[#f7f8fa] p-4 font-sans text-[#161823] min-[1041px]:p-5">
      <section className="relative flex min-h-[122px] items-center overflow-hidden rounded-[10px] bg-[linear-gradient(100deg,rgba(255,255,255,0.96)_0%,rgba(247,247,255,0.88)_42%),linear-gradient(115deg,#f9fbff_0%,#e7e7ff_62%,#d8dbff_100%)] px-[30px] py-6">
        <div className="absolute -top-[180px] right-[45px] h-[470px] w-[470px] rounded-full border border-[rgba(126,133,255,0.12)]" />
        <div className="relative z-[1] flex h-[56px] w-[56px] shrink-0 items-center justify-center rounded-full bg-[#161823] text-white">
          <Sparkles size={24} strokeWidth={1.8} />
        </div>
        <div className="relative z-[1] ml-[18px] min-w-0">
          <h1 className="m-0 text-[18px] font-[650] tracking-[-0.3px] text-[#161823]">Yak Ops 一体化数据平台</h1>
          <p className="mb-0 mt-2 max-w-[760px] text-xs leading-5 text-[rgba(22,24,35,0.5)]">资源、运行、告警与后续调度统一汇总。</p>
          <div className="mt-2 flex items-center gap-2 text-[11px] text-[rgba(22,24,35,0.38)]">
            {lastUpdatedAt ? (
              <span>最近更新 {lastUpdatedAt.toLocaleTimeString('zh-CN', { hour12: false })}</span>
            ) : (
              <span>正在读取平台实时数据</span>
            )}
            {unavailableText ? <span className="rounded bg-[#fff1f3] px-1.5 py-0.5 text-[#d92d50]">{unavailableText}</span> : null}
          </div>
        </div>
        <div className="relative z-[1] ml-auto pl-5">
          <Button
            icon={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
            disabled={loading}
            onClick={() => void loadOverview()}
          >
            刷新
          </Button>
        </div>
      </section>

      <section className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <MetricCard
          icon={Database}
          label="数据源"
          value={dataSource?.total}
          detail={`正常 ${formatCount(dataSource?.connected)} / 异常 ${formatCount(dataSource?.disconnected)}`}
          loading={loading}
          unavailable={unavailableSet.has('dataSource')}
          onClick={() => navigate('/data-source')}
        />
        <MetricCard
          icon={Server}
          label="客户端"
          value={client?.total}
          detail={clientDetail}
          loading={loading}
          unavailable={unavailableSet.has('client')}
          onClick={() => navigate('/client')}
        />
        <MetricCard
          icon={Activity}
          label="今日运行"
          value={execution?.todayTotal}
          detail={`运行中 ${formatCount(execution?.running)} / 失败 ${formatCount(execution?.failed)}`}
          loading={loading}
          unavailable={executionUnavailable}
          onClick={() => scrollTo('home-runtime')}
        />
        <MetricCard
          icon={CalendarClock}
          label="今日计划"
          value={schedule?.today}
          detail={`24h 内 ${formatCount(schedule?.next24h)} / 已启用 ${formatCount(schedule?.enabled)}`}
          loading={loading}
          unavailable={scheduleUnavailable}
          onClick={() => scrollTo('home-schedule')}
        />
        <MetricCard
          icon={Bell}
          label="告警记录"
          value={alarm?.total}
          detail={alarm?.total ? '展示最近产生的告警记录' : '当前没有告警记录'}
          loading={loading}
          unavailable={unavailableSet.has('alarm')}
          onClick={() => navigate('/alarm')}
        />
      </section>

      <section className={`${HOME_CARD_CLASS} mt-4 px-[22px] pb-[22px] pt-5`}>
        <SectionHeader title="快速创建" />
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {quickActions.map((action) => <QuickActionCard action={action} key={action.title} />)}
        </div>
      </section>

      <div className="mt-4 grid grid-cols-1 gap-4 min-[1041px]:grid-cols-[minmax(0,1.6fr)_minmax(320px,0.8fr)]">
        <section className={`${HOME_CARD_CLASS} min-h-[270px] p-[22px]`}>
          <SectionHeader
            title="资源概览"
            extra={
              <button type="button" className="inline-flex items-center border-0 bg-transparent p-0 text-xs text-[rgba(22,24,35,0.5)] hover:text-[#161823]" onClick={() => navigate('/data-source')}>
                资源管理<ChevronRight size={14} />
              </button>
            }
          />
          <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-2">
            <button type="button" className="min-w-0 rounded-[10px] border border-[rgba(22,24,35,0.06)] bg-white p-4 text-left" onClick={() => navigate('/data-source')}>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#fff1f3] text-[#fe2c55]"><Database size={19} /></span>
                  <strong className="text-[13px] font-[650]">数据源</strong>
                </div>
                <DataState unavailable={unavailableSet.has('dataSource')} loading={loading}>
                  <strong className="text-xl font-[650]">{formatCount(dataSource?.total)}</strong>
                </DataState>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4 xl:grid-cols-2 2xl:grid-cols-4">
                <SmallValue label="连接正常" value={dataSource?.connected} />
                <SmallValue label="连接异常" value={dataSource?.disconnected} />
                <SmallValue label="状态未知" value={dataSource?.unknown} />
                <SmallValue label="环境数量" value={dataSource?.environmentCount} />
              </div>
            </button>

            <button type="button" className="min-w-0 rounded-[10px] border border-[rgba(22,24,35,0.06)] bg-white p-4 text-left" onClick={() => navigate('/client')}>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#f2f4f7] text-[#344054]"><Server size={19} /></span>
                  <strong className="text-[13px] font-[650]">客户端</strong>
                </div>
                <DataState unavailable={unavailableSet.has('client')} loading={loading}>
                  <strong className="text-xl font-[650]">{formatCount(client?.total)}</strong>
                </DataState>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-2">
                <SmallValue label="客户端总数" value={client?.total} />
                {client?.online !== undefined || client?.offline !== undefined ? (
                  <>
                    <SmallValue label="在线" value={client?.online} />
                    <SmallValue label="离线" value={client?.offline} />
                  </>
                ) : (
                  <div className="flex min-h-[60px] items-center rounded-lg bg-[#f8f8f9] px-3 text-[11px] leading-5 text-[rgba(22,24,35,0.42)]">当前接口未明确返回在线/离线拆分。</div>
                )}
              </div>
            </button>
          </div>
        </section>

        <aside className={`${HOME_CARD_CLASS} min-h-[270px] p-[22px]`}>
          <SectionHeader
            title="最近告警"
            extra={
              <button type="button" className="inline-flex items-center border-0 bg-transparent p-0 text-xs text-[rgba(22,24,35,0.5)] hover:text-[#161823]" onClick={() => navigate('/alarm')}>
                查看全部<ChevronRight size={14} />
              </button>
            }
          />
          <div className="mt-4">
            {loading ? (
              <Skeleton active paragraph={{ rows: 3 }} title={false} />
            ) : unavailableSet.has('alarm') ? (
              <div className="flex min-h-[150px] items-center justify-center rounded-lg bg-[#fafafa] text-xs text-[rgba(22,24,35,0.4)]">告警数据暂不可用</div>
            ) : alarm?.recent.length ? (
              <div className="divide-y divide-[rgba(22,24,35,0.06)]">
                {alarm.recent.map((item, index) => (
                  <button type="button" className="flex w-full min-w-0 items-center gap-3 border-0 bg-transparent py-3 text-left first:pt-0 last:pb-0" key={item.id ?? `${item.jobName}-${item.time}-${index}`} onClick={() => navigate('/alarm')}>
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#fff1f3] text-[#fe2c55]"><Bell size={16} /></span>
                    <span className="min-w-0 flex-1">
                      <strong className="block truncate text-[12px] font-[650] text-[#161823]">{item.jobName || '未命名任务'}</strong>
                      <span className="mt-1 flex items-center gap-2 text-[10px] text-[rgba(22,24,35,0.4)]"><span>{item.status || '状态未知'}</span><span>{formatTime(item.time)}</span></span>
                    </span>
                    <ChevronRight size={14} className="shrink-0 text-[rgba(22,24,35,0.2)]" />
                  </button>
                ))}
              </div>
            ) : (
              <div className="flex min-h-[150px] flex-col items-center justify-center rounded-lg bg-[#fafafa] text-center">
                <Bell size={24} className="text-[rgba(22,24,35,0.24)]" />
                <span className="mt-2 text-xs text-[rgba(22,24,35,0.42)]">当前没有告警记录</span>
              </div>
            )}
          </div>
        </aside>
      </div>

      <div id="home-runtime" className="mt-4 grid scroll-mt-4 grid-cols-1 gap-4 min-[1100px]:grid-cols-[minmax(330px,0.82fr)_minmax(0,1.18fr)]">
        <section className={`${HOME_CARD_CLASS} min-h-[286px] p-[22px]`}>
          <SectionHeader
            title="运行概览"
            extra={
              <div className="flex items-center gap-3">
                <button type="button" className="border-0 bg-transparent p-0 text-xs text-[rgba(22,24,35,0.5)] hover:text-[#161823]" onClick={() => navigate('/sync/batch-link-up')}>离线同步</button>
                <button type="button" className="border-0 bg-transparent p-0 text-xs text-[rgba(22,24,35,0.5)] hover:text-[#161823]" onClick={() => navigate('/workflow/instances')}>工作流实例</button>
              </div>
            }
          />
          <div className="mt-5 grid grid-cols-2 gap-3">
            <OverviewValue icon={Clock3} label="今日运行" value={execution?.todayTotal} loading={loading} unavailable={executionUnavailable} />
            <OverviewValue icon={Activity} label="当前运行" value={execution?.running} loading={loading} unavailable={executionUnavailable} />
            <OverviewValue icon={CheckCircle2} label="今日成功" value={execution?.success} loading={loading} unavailable={executionUnavailable} />
            <OverviewValue icon={XCircle} label="今日失败" value={execution?.failed} loading={loading} unavailable={executionUnavailable} />
          </div>
          {!loading && !executionUnavailable && execution ? (
            <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-[10px] text-[rgba(22,24,35,0.38)]">
              <span>离线实例 {formatCount(execution.batchObserved)} 条</span>
              <span>工作流实例 {formatCount(execution.workflowObserved)} 条</span>
              {execution.limited ? <span className="text-[#b54708]">离线统计基于最近 200 条实例</span> : null}
            </div>
          ) : null}
        </section>

        <section className={`${HOME_CARD_CLASS} min-h-[286px] p-[22px]`}>
          <SectionHeader title="最近运行" />
          <div className="mt-4">
            {loading ? (
              <Skeleton active paragraph={{ rows: 4 }} title={false} />
            ) : executionUnavailable ? (
              <div className="flex min-h-[190px] items-center justify-center rounded-lg bg-[#fafafa] text-xs text-[rgba(22,24,35,0.4)]">运行实例数据暂不可用</div>
            ) : execution?.recent.length ? (
              <div className="divide-y divide-[rgba(22,24,35,0.06)]">
                {execution.recent.map((item) => <RecentRunRow item={item} key={`${item.type}-${item.id}`} />)}
              </div>
            ) : (
              <div className="flex min-h-[190px] items-center justify-center rounded-lg bg-[#fafafa] text-xs text-[rgba(22,24,35,0.42)]">暂无运行实例</div>
            )}
          </div>
        </section>
      </div>

      <div id="home-schedule" className="mt-4 grid scroll-mt-4 grid-cols-1 gap-4 min-[1100px]:grid-cols-[minmax(330px,0.82fr)_minmax(0,1.18fr)]">
        <section className={`${HOME_CARD_CLASS} min-h-[286px] p-[22px]`}>
          <SectionHeader
            title="调度概览"
            extra={
              <button type="button" className="inline-flex items-center border-0 bg-transparent p-0 text-xs text-[rgba(22,24,35,0.5)] hover:text-[#161823]" onClick={() => navigate('/workflow/schedules')}>
                调度管理<ChevronRight size={14} />
              </button>
            }
          />
          <div className="mt-5 grid grid-cols-2 gap-3">
            <OverviewValue icon={CalendarClock} label="已配置" value={schedule?.total} loading={loading} unavailable={scheduleUnavailable} />
            <OverviewValue icon={PlayCircle} label="已启用" value={schedule?.enabled} loading={loading} unavailable={scheduleUnavailable} />
            <OverviewValue icon={PauseCircle} label="已暂停" value={schedule?.paused} loading={loading} unavailable={scheduleUnavailable} />
            <OverviewValue icon={Clock3} label="24h 内" value={schedule?.next24h} loading={loading} unavailable={scheduleUnavailable} />
          </div>
          {!loading && !scheduleUnavailable && schedule ? (
            <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-[10px] text-[rgba(22,24,35,0.38)]">
              <span>离线调度 {formatCount(schedule.batchObserved)} 个</span>
              <span>工作流调度 {formatCount(schedule.workflowObserved)} 个</span>
              {schedule.limited ? <span className="text-[#b54708]">离线调度统计基于最近 200 个任务定义</span> : null}
            </div>
          ) : null}
        </section>

        <section className={`${HOME_CARD_CLASS} min-h-[286px] p-[22px]`}>
          <SectionHeader title="近期计划" description="仅展示接口明确返回下次执行时间且当前已启用的计划。" />
          <div className="mt-4">
            {loading ? (
              <Skeleton active paragraph={{ rows: 4 }} title={false} />
            ) : scheduleUnavailable ? (
              <div className="flex min-h-[190px] items-center justify-center rounded-lg bg-[#fafafa] text-xs text-[rgba(22,24,35,0.4)]">调度计划数据暂不可用</div>
            ) : schedule?.upcoming.length ? (
              <div className="divide-y divide-[rgba(22,24,35,0.06)]">
                {schedule.upcoming.map((item) => <ScheduleRow item={item} key={`${item.type}-${item.id}`} />)}
              </div>
            ) : (
              <div className="flex min-h-[190px] flex-col items-center justify-center rounded-lg bg-[#fafafa] text-center">
                <CalendarClock size={24} className="text-[rgba(22,24,35,0.24)]" />
                <span className="mt-2 text-xs text-[rgba(22,24,35,0.42)]">暂无明确的后续执行计划</span>
              </div>
            )}
          </div>
        </section>
      </div>

      <DeferredSection />
    </div>
  );
};

export default HomePage;
