import { history } from '@umijs/max';
import {
  AlertTriangle,
  ArrowRightLeft,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Code2,
  History,
  ShieldCheck,
  Workflow,
} from 'lucide-react';
import { type ReactNode, useEffect, useMemo, useState } from 'react';
import {
  readRecentVisits,
  RECENT_VISITS_CHANGED_EVENT,
  type RecentVisit,
} from '@/utils/recent-visits';
import { homeDataCenterApi, type HomeRecentTask } from './service';

const FAILED_STATUSES = new Set(['FAILED', 'ERROR', 'LOST', 'TIMED_OUT']);

const taskMeta: Record<string, { label: string; icon: ReactNode }> = {
  OFFLINE_SYNC: {
    label: '离线同步',
    icon: <ArrowRightLeft size={15} strokeWidth={1.9} />,
  },
  WORKFLOW: {
    label: '工作流',
    icon: <Workflow size={15} strokeWidth={1.9} />,
  },
  DATA_QUALITY: {
    label: '数据质量',
    icon: <ShieldCheck size={15} strokeWidth={1.9} />,
  },
};

const visitIcon = (path: string) => {
  if (path.startsWith('/workflow')) {
    return <Workflow size={15} strokeWidth={1.9} />;
  }
  if (path.startsWith('/sync')) {
    return <ArrowRightLeft size={15} strokeWidth={1.9} />;
  }
  if (path.startsWith('/data-quality')) {
    return <ShieldCheck size={15} strokeWidth={1.9} />;
  }
  return <Code2 size={15} strokeWidth={1.9} />;
};

const relativeTime = (value: string | number) => {
  const timestamp = typeof value === 'number' ? value : new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return '-';
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}小时前`;
  const days = Math.floor(hours / 24);
  return days < 30 ? `${days}天前` : new Date(timestamp).toLocaleDateString();
};

function PanelHeader({
  title,
  extra,
}: {
  title: string;
  extra?: ReactNode;
}) {
  return (
    <header className="flex h-8 items-start justify-between gap-4">
      <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
        {title}
      </h2>
      {extra}
    </header>
  );
}

function EmptyPanel({ healthy = false }: { healthy?: boolean }) {
  return (
    <div className="flex min-h-[178px] flex-col items-center justify-center text-center">
      <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#f4f7f5] text-[#4e8d68]">
        {healthy ? (
          <CheckCircle2 size={19} strokeWidth={1.8} />
        ) : (
          <History size={19} strokeWidth={1.8} />
        )}
      </span>
      <strong className="mt-3 text-[13px] font-semibold text-[#464a53]">
        {healthy ? '当前没有待处理异常' : '暂无最近访问'}
      </strong>
      <span className="mt-1 text-[11px] leading-5 text-[#9a9ea6]">
        {healthy ? '平台任务运行正常' : '打开业务页面后会自动记录在这里'}
      </span>
    </div>
  );
}

function AttentionList({ items }: { items: HomeRecentTask[] }) {
  if (items.length === 0) return <EmptyPanel healthy />;
  return (
    <div className="mt-3 divide-y divide-[#f0f1f3]">
      {items.map((item) => {
        const meta = taskMeta[item.taskType] || {
          label: '任务',
          icon: <Clock3 size={15} strokeWidth={1.9} />,
        };
        return (
          <button
            key={`${item.taskType}-${item.taskId}`}
            type="button"
            onClick={() => item.detailPath && history.push(item.detailPath)}
            className="group flex w-full items-center gap-3 border-0 bg-transparent py-3 text-left"
          >
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#fff2f4] text-[#e24a65]">
              {meta.icon}
            </span>
            <span className="min-w-0 flex-1">
              <span className="flex items-center gap-2">
                <strong className="truncate text-[13px] font-semibold text-[#343842] transition-colors group-hover:text-[#161823]">
                  {item.taskName}
                </strong>
                <span className="shrink-0 rounded bg-[#f5f6f8] px-1.5 py-0.5 text-[10px] text-[#858a93]">
                  {meta.label}
                </span>
              </span>
              <span className="mt-1 block text-[11px] text-[#969ba4]">
                最近一次运行失败 · {relativeTime(item.lastRunTime)}
              </span>
            </span>
            <span className="flex shrink-0 items-center gap-1 text-[11px] font-medium text-[#d64a63]">
              <AlertTriangle size={13} strokeWidth={1.9} />
              待处理
            </span>
            <ChevronRight
              size={14}
              strokeWidth={1.8}
              className="shrink-0 text-[#b2b6bd] transition-transform group-hover:translate-x-0.5"
            />
          </button>
        );
      })}
    </div>
  );
}

function RecentVisitList({ items }: { items: RecentVisit[] }) {
  if (items.length === 0) return <EmptyPanel />;
  return (
    <div className="mt-3 divide-y divide-[#f0f1f3]">
      {items.slice(0, 5).map((item) => (
        <button
          key={item.path}
          type="button"
          onClick={() => history.push(item.path)}
          className="group flex w-full items-center gap-3 border-0 bg-transparent py-3 text-left"
        >
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#f3f5f8] text-[#68707d]">
            {visitIcon(item.path)}
          </span>
          <span className="min-w-0 flex-1">
            <strong className="block truncate text-[13px] font-semibold text-[#3a3e47] transition-colors group-hover:text-[#161823]">
              {item.title}
            </strong>
            <span className="mt-1 block truncate text-[10px] text-[#a0a4ac]">
              {item.path}
            </span>
          </span>
          <span className="shrink-0 text-[10px] text-[#9a9ea6]">
            {relativeTime(item.visitedAt)}
          </span>
          <ChevronRight
            size={14}
            strokeWidth={1.8}
            className="shrink-0 text-[#b2b6bd] transition-transform group-hover:translate-x-0.5"
          />
        </button>
      ))}
    </div>
  );
}

export default function HomeWorkbench() {
  const [recentTasks, setRecentTasks] = useState<HomeRecentTask[]>([]);
  const [recentVisits, setRecentVisits] = useState<RecentVisit[]>(() =>
    readRecentVisits(),
  );

  useEffect(() => {
    let active = true;
    homeDataCenterApi.recent()
      .then((response) => {
        if (active) setRecentTasks(response.data?.items || []);
      })
      .catch(() => {
        if (active) setRecentTasks([]);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const refresh = () => setRecentVisits(readRecentVisits());
    window.addEventListener(RECENT_VISITS_CHANGED_EVENT, refresh);
    return () =>
      window.removeEventListener(RECENT_VISITS_CHANGED_EVENT, refresh);
  }, []);

  const attentionItems = useMemo(
    () =>
      recentTasks
        .filter((item) => FAILED_STATUSES.has(item.lastStatus?.toUpperCase()))
        .slice(0, 5),
    [recentTasks],
  );

  return (
    <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_410px]">
      <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-4 pt-5">
        <PanelHeader
          title="待处理事项"
          extra={
            <span className="mt-1 text-[11px] text-[#969ba4]">
              {attentionItems.length > 0
                ? `${attentionItems.length} 项需要关注`
                : '运行状态正常'}
            </span>
          }
        />
        <AttentionList items={attentionItems} />
      </section>

      <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-4 pt-5">
        <PanelHeader title="最近访问" />
        <RecentVisitList items={recentVisits} />
      </section>
    </div>
  );
}
