import { history } from '@umijs/max';
import { Button, Skeleton, Tooltip } from 'antd';
import {
  Activity,
  Bell,
  Cable,
  ChevronRight,
  Database,
  RefreshCw,
  Server,
  Sparkles,
  Workflow,
  type LucideIcon,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import type {
  HomeDataSourceKey,
  HomeOverview,
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
  'rounded-[10px] border border-[rgba(22,24,35,0.045)] bg-white shadow-[0_2px_12px_rgba(31,35,41,0.025)]';

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
};

const navigate = (path: string) => history.push(path);

const formatCount = (value?: number) =>
  typeof value === 'number' ? value.toLocaleString('zh-CN') : '--';

const formatTime = (value?: string) => {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 19);
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
        <h2 className="m-0 text-lg font-[650] tracking-[-0.2px] text-[#161823]">
          {title}
        </h2>
        {description ? (
          <p className="mb-0 mt-1 text-xs leading-5 text-[rgba(22,24,35,0.46)]">
            {description}
          </p>
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
  if (loading) {
    return <Skeleton.Input active size="small" style={{ width: 72 }} />;
  }

  if (unavailable) {
    return (
      <Tooltip title="当前接口未能返回有效数据">
        <span className="text-[13px] font-medium text-[rgba(22,24,35,0.38)]">
          暂不可用
        </span>
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
      className={`${HOME_CARD_CLASS} group flex min-h-[112px] min-w-0 cursor-pointer items-center border-[rgba(22,24,35,0.045)] px-5 py-4 text-left transition duration-150 hover:-translate-y-px hover:border-[rgba(22,24,35,0.08)] hover:shadow-[0_8px_24px_rgba(22,24,35,0.055)]`}
      onClick={onClick}
    >
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[10px] bg-[#f5f5f6] text-[#161823] transition-colors group-hover:bg-[#f0f0f1]">
        <Icon size={22} strokeWidth={1.9} />
      </span>
      <span className="ml-4 min-w-0 flex-1">
        <span className="block text-xs text-[rgba(22,24,35,0.48)]">{label}</span>
        <span className="mt-1.5 flex min-h-8 items-center">
          <DataState unavailable={unavailable} loading={loading}>
            <strong className="text-[26px] font-[650] leading-8 tracking-[-0.5px] text-[#161823]">
              {formatCount(value)}
            </strong>
          </DataState>
        </span>
        <span className="mt-1 block truncate text-[11px] text-[rgba(22,24,35,0.42)]">
          {loading || unavailable ? '来自 Yak Ops 实际接口' : detail}
        </span>
      </span>
      <ChevronRight
        className="ml-2 shrink-0 text-[rgba(22,24,35,0.2)] transition group-hover:translate-x-0.5 group-hover:text-[rgba(22,24,35,0.48)]"
        size={16}
      />
    </button>
  );
}

function QuickActionCard({ action }: { action: QuickAction }) {
  const Icon = action.icon;

  return (
    <button
      type="button"
      className={`relative flex h-[66px] min-w-0 cursor-pointer items-center overflow-hidden rounded-[9px] border-0 px-4 text-left transition duration-150 hover:-translate-y-px hover:shadow-[0_7px_20px_rgba(22,24,35,0.08)] ${actionToneClasses[action.tone]}`}
      onClick={() => navigate(action.path)}
    >
      <span className="relative z-[1] flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-current text-white shadow-[0_4px_10px_rgba(22,24,35,0.09)]">
        <Icon className="text-white" size={20} strokeWidth={2.1} />
      </span>
      <span className="relative z-[1] ml-2.5 flex min-w-0 flex-col">
        <strong className="truncate text-sm font-[650] text-[#161823]">
          {action.title}
        </strong>
        <span className="mt-[3px] truncate text-[11px] text-[rgba(22,24,35,0.5)]">
          {action.description}
        </span>
      </span>
      <span className="absolute -right-2 -top-6 h-[92px] w-[68px] -rotate-[28deg] rounded-[22px] border-[12px] border-current opacity-[0.08]" />
    </button>
  );
}

function ResourceValue({
  label,
  value,
}: {
  label: string;
  value?: number;
}) {
  return (
    <div className="min-w-0 rounded-lg bg-[#f8f8f9] px-3 py-3">
      <div className="text-[11px] text-[rgba(22,24,35,0.42)]">{label}</div>
      <div className="mt-1 text-[15px] font-[650] text-[#161823]">
        {formatCount(value)}
      </div>
    </div>
  );
}

function DeferredSection({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <section className={`${HOME_CARD_CLASS} min-h-[174px] p-[22px]`}>
      <SectionHeader title={title} />
      <div className="mt-4 flex min-h-[94px] items-center rounded-lg border border-dashed border-[rgba(22,24,35,0.09)] bg-[#fafafa] px-5">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-[rgba(22,24,35,0.4)] shadow-[0_2px_8px_rgba(22,24,35,0.04)]">
          <Activity size={18} />
        </span>
        <div className="ml-3 min-w-0">
          <div className="text-[13px] font-[650] text-[rgba(22,24,35,0.74)]">
            等待真实数据接入
          </div>
          <div className="mt-1 text-[11px] leading-5 text-[rgba(22,24,35,0.42)]">
            {description}
          </div>
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
      setOverview({ unavailable: ['dataSource', 'client', 'alarm'] });
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

  const dataSource = overview?.dataSource;
  const client = overview?.client;
  const alarm = overview?.alarm;

  return (
    <div className="min-h-full bg-[#f7f8fa] p-4 font-sans text-[#161823] min-[1041px]:p-5">
      <section className="relative flex min-h-[126px] items-center overflow-hidden rounded-[10px] bg-[linear-gradient(100deg,rgba(255,255,255,0.96)_0%,rgba(247,247,255,0.88)_42%),linear-gradient(115deg,#f9fbff_0%,#e7e7ff_62%,#d8dbff_100%)] px-[30px] py-6">
        <div className="absolute -top-[180px] right-[45px] h-[470px] w-[470px] rounded-full border border-[rgba(126,133,255,0.14)] shadow-[inset_0_0_0_28px_rgba(255,255,255,0.08),inset_0_0_0_76px_rgba(255,255,255,0.07)]" />
        <div className="absolute -top-[116px] right-[230px] h-[280px] w-[280px] rounded-full border border-[rgba(126,133,255,0.12)]" />

        <div className="relative z-[1] flex h-[58px] w-[58px] shrink-0 items-center justify-center rounded-full border-2 border-white/90 bg-[#161823] text-white shadow-[0_5px_20px_rgba(53,63,110,0.12)]">
          <Sparkles size={25} strokeWidth={1.8} />
        </div>

        <div className="relative z-[1] ml-[18px] min-w-0">
          <h1 className="m-0 text-[18px] font-[650] tracking-[-0.3px] text-[#161823]">
            Yak Ops 一体化数据平台
          </h1>
          <p className="mb-0 mt-2 max-w-[720px] text-xs leading-5 text-[rgba(22,24,35,0.5)]">
            首页第一阶段仅展示已接入真实接口的数据；运行趋势、调度与复杂统计将在后续阶段逐步接入。
          </p>
          <div className="mt-2 flex items-center gap-2 text-[11px] text-[rgba(22,24,35,0.38)]">
            {lastUpdatedAt ? (
              <span>最近更新 {lastUpdatedAt.toLocaleTimeString('zh-CN', { hour12: false })}</span>
            ) : (
              <span>正在读取平台实时数据</span>
            )}
            {unavailableText ? (
              <span className="rounded bg-[#fff1f3] px-1.5 py-0.5 text-[#d92d50]">
                {unavailableText}
              </span>
            ) : null}
          </div>
        </div>

        <div className="relative z-[1] ml-auto pl-5">
          <Button
            icon={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
            loading={false}
            disabled={loading}
            onClick={() => void loadOverview()}
          >
            刷新
          </Button>
        </div>
      </section>

      <section className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
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
        <SectionHeader
          title="快速创建"
          description="保留高频入口，创建后进入各业务模块完成配置与运行。"
        />
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {quickActions.map((action) => (
            <QuickActionCard action={action} key={action.title} />
          ))}
        </div>
      </section>

      <div className="mt-4 grid grid-cols-1 gap-4 min-[1041px]:grid-cols-[minmax(0,1.6fr)_minmax(320px,0.8fr)]">
        <section className={`${HOME_CARD_CLASS} min-h-[276px] p-[22px]`}>
          <SectionHeader
            title="资源概览"
            description="资源状态直接来自数据源与客户端现有接口，不再使用首页静态演示数据。"
            extra={
              <button
                type="button"
                className="inline-flex cursor-pointer items-center border-0 bg-transparent p-0 text-xs text-[rgba(22,24,35,0.5)] transition-colors hover:text-[#161823]"
                onClick={() => navigate('/data-source')}
              >
                资源管理
                <ChevronRight size={14} />
              </button>
            }
          />

          <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-2">
            <button
              type="button"
              className="min-w-0 cursor-pointer rounded-[10px] border border-[rgba(22,24,35,0.06)] bg-white p-4 text-left transition hover:border-[rgba(22,24,35,0.1)] hover:shadow-[0_6px_18px_rgba(22,24,35,0.04)]"
              onClick={() => navigate('/data-source')}
            >
              <div className="flex items-center justify-between gap-3">
                <div className="flex min-w-0 items-center gap-2.5">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#fff1f3] text-[#fe2c55]">
                    <Database size={19} />
                  </span>
                  <div className="min-w-0">
                    <strong className="block truncate text-[13px] font-[650]">数据源</strong>
                    <span className="mt-0.5 block text-[11px] text-[rgba(22,24,35,0.42)]">
                      连接状态与环境统计
                    </span>
                  </div>
                </div>
                <DataState unavailable={unavailableSet.has('dataSource')} loading={loading}>
                  <strong className="text-xl font-[650]">{formatCount(dataSource?.total)}</strong>
                </DataState>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4 xl:grid-cols-2 2xl:grid-cols-4">
                <ResourceValue label="连接正常" value={dataSource?.connected} />
                <ResourceValue label="连接异常" value={dataSource?.disconnected} />
                <ResourceValue label="状态未知" value={dataSource?.unknown} />
                <ResourceValue label="环境数量" value={dataSource?.environmentCount} />
              </div>
            </button>

            <button
              type="button"
              className="min-w-0 cursor-pointer rounded-[10px] border border-[rgba(22,24,35,0.06)] bg-white p-4 text-left transition hover:border-[rgba(22,24,35,0.1)] hover:shadow-[0_6px_18px_rgba(22,24,35,0.04)]"
              onClick={() => navigate('/client')}
            >
              <div className="flex items-center justify-between gap-3">
                <div className="flex min-w-0 items-center gap-2.5">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#f2f4f7] text-[#344054]">
                    <Server size={19} />
                  </span>
                  <div className="min-w-0">
                    <strong className="block truncate text-[13px] font-[650]">客户端</strong>
                    <span className="mt-0.5 block text-[11px] text-[rgba(22,24,35,0.42)]">
                      执行器服务实时统计
                    </span>
                  </div>
                </div>
                <DataState unavailable={unavailableSet.has('client')} loading={loading}>
                  <strong className="text-xl font-[650]">{formatCount(client?.total)}</strong>
                </DataState>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-2">
                <ResourceValue label="客户端总数" value={client?.total} />
                {client?.online !== undefined || client?.offline !== undefined ? (
                  <>
                    <ResourceValue label="在线" value={client?.online} />
                    <ResourceValue label="离线" value={client?.offline} />
                  </>
                ) : (
                  <div className="flex min-h-[60px] items-center rounded-lg bg-[#f8f8f9] px-3 text-[11px] leading-5 text-[rgba(22,24,35,0.42)]">
                    当前接口未明确返回在线/离线拆分，首页不做推算。
                  </div>
                )}
              </div>
            </button>
          </div>
        </section>

        <aside className={`${HOME_CARD_CLASS} min-h-[276px] p-[22px]`}>
          <SectionHeader
            title="最近告警"
            description="展示告警记录接口返回的最近数据，不将历史记录误标为待处理事项。"
            extra={
              <button
                type="button"
                className="inline-flex cursor-pointer items-center border-0 bg-transparent p-0 text-xs text-[rgba(22,24,35,0.5)] transition-colors hover:text-[#161823]"
                onClick={() => navigate('/alarm')}
              >
                查看全部
                <ChevronRight size={14} />
              </button>
            }
          />

          <div className="mt-4">
            {loading ? (
              <Skeleton active paragraph={{ rows: 3 }} title={false} />
            ) : unavailableSet.has('alarm') ? (
              <div className="flex min-h-[150px] items-center justify-center rounded-lg bg-[#fafafa] text-xs text-[rgba(22,24,35,0.4)]">
                告警数据暂不可用
              </div>
            ) : alarm?.recent.length ? (
              <div className="divide-y divide-[rgba(22,24,35,0.06)]">
                {alarm.recent.map((item, index) => (
                  <button
                    type="button"
                    className="flex w-full min-w-0 cursor-pointer items-center gap-3 border-0 bg-transparent py-3 text-left first:pt-0 last:pb-0"
                    key={item.id ?? `${item.jobName}-${item.time}-${index}`}
                    onClick={() => navigate('/alarm')}
                  >
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#fff1f3] text-[#fe2c55]">
                      <Bell size={16} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex min-w-0 items-center gap-2">
                        <strong className="truncate text-[12px] font-[650] text-[#161823]">
                          {item.jobName || '未命名任务'}
                        </strong>
                        {item.severity ? (
                          <span className="shrink-0 rounded bg-[#f5f5f6] px-1.5 py-0.5 text-[9px] text-[rgba(22,24,35,0.55)]">
                            {item.severity}
                          </span>
                        ) : null}
                      </span>
                      <span className="mt-1 flex items-center gap-2 text-[10px] text-[rgba(22,24,35,0.4)]">
                        <span>{item.status || '状态未知'}</span>
                        <span>{formatTime(item.time)}</span>
                      </span>
                    </span>
                    <ChevronRight size={14} className="shrink-0 text-[rgba(22,24,35,0.2)]" />
                  </button>
                ))}
              </div>
            ) : (
              <div className="flex min-h-[150px] flex-col items-center justify-center rounded-lg bg-[#fafafa] text-center">
                <Bell size={24} className="text-[rgba(22,24,35,0.24)]" />
                <span className="mt-2 text-xs text-[rgba(22,24,35,0.42)]">
                  当前没有告警记录
                </span>
              </div>
            )}
          </div>
        </aside>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
        <DeferredSection
          title="运行概览"
          description="第二阶段将统一离线同步与工作流运行实例，接入今日运行、运行中、成功与失败等真实统计。"
        />
        <DeferredSection
          title="调度与趋势"
          description="调度日历、趋势图和同比指标需要统一统计口径，暂不展示任何演示数据。"
        />
      </div>
    </div>
  );
};

export default HomePage;
