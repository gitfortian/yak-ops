import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { HomeLatestTask } from '@/services/home';
import { history } from '@umijs/max';
import {
  ArrowRightLeft,
  ChevronRight,
  Clock,
  Database,
  ShieldCheck,
  Workflow,
} from 'lucide-react';

import {
  formatDuration,
  statusLabel,
  taskTypeLabel,
} from '../../utils/homeDataCenter';

interface LatestTaskCardProps {
  task?: HomeLatestTask;
  loading: boolean;
  failed: boolean;
}

type TaskTheme = {
  accent: string;
  iconBox: string;
  icon: string;
  tag: string;
};

const TASK_THEMES: Record<HomeLatestTask['taskType'], TaskTheme> = {
  OFFLINE_SYNC: {
    accent: 'bg-[linear-gradient(90deg,#6675f5_0%,#94a8ff_100%)]',
    iconBox: 'bg-[#eef1ff]',
    icon: 'text-[#6572ed]',
    tag: 'bg-[#f1f3ff] text-[#5d68dc]',
  },
  WORKFLOW: {
    accent: 'bg-[linear-gradient(90deg,#ffb900_0%,#ffd85a_100%)]',
    iconBox: 'bg-[#fff7dc]',
    icon: 'text-[#d49600]',
    tag: 'bg-[#fff8e2] text-[#aa7600]',
  },
  DATA_QUALITY: {
    accent: 'bg-[linear-gradient(90deg,#23a66b_0%,#66ce9b_100%)]',
    iconBox: 'bg-[#eaf8f1]',
    icon: 'text-[#259767]',
    tag: 'bg-[#edf9f3] text-[#23855b]',
  },
};

function TaskTypeIcon({ taskType }: { taskType: HomeLatestTask['taskType'] }) {
  if (taskType === 'OFFLINE_SYNC') {
    return <ArrowRightLeft size={20} strokeWidth={1.9} />;
  }
  if (taskType === 'WORKFLOW') {
    return <Workflow size={20} strokeWidth={1.9} />;
  }
  if (taskType === 'DATA_QUALITY') {
    return <ShieldCheck size={20} strokeWidth={1.9} />;
  }
  return <Database size={20} strokeWidth={1.9} />;
}

function statusBadgeClassName(status?: string) {
  const label = statusLabel(status);
  if (label === '成功') return 'bg-[#ebf8f1] text-[#218557]';
  if (label === '失败') return 'bg-[#fff0f1] text-[#dc4250]';
  if (label === '运行中') return 'bg-[#eef4ff] text-[#4775d1]';
  if (label === '已取消') return 'bg-[#f2f3f5] text-[#747983]';
  return 'bg-[#f4f5f7] text-[#747983]';
}

function LatestTaskContent({ task }: { task: HomeLatestTask }) {
  const theme = TASK_THEMES[task.taskType];
  const status = statusLabel(task.status);

  return (
    <button
      type="button"
      onClick={() => {
        if (task.detailPath) history.push(task.detailPath);
      }}
      className="group relative flex h-[280px] w-full flex-col overflow-hidden rounded-[16px] border border-[#e8ebef] bg-white p-4 text-left shadow-[0_4px_14px_rgba(31,35,41,0.045),0_1px_2px_rgba(31,35,41,0.025)] transition-[border-color,box-shadow,transform] duration-[240ms] ease-out hover:-translate-y-px hover:border-[#dde2e8] hover:shadow-[0_9px_22px_rgba(31,35,41,0.07),0_1px_2px_rgba(31,35,41,0.025)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-200/70"
    >
      <span
        aria-hidden="true"
        className={`pointer-events-none absolute inset-x-0 top-0 h-[3px] ${theme.accent}`}
      />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -right-8 -top-10 h-32 w-32 rounded-full border border-[#eef0f4]"
      />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -right-3 -top-5 h-24 w-24 rounded-full border border-[#f1f2f5]"
      />

      <div className="relative flex items-start justify-between gap-2">
        <div
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] ${theme.iconBox} ${theme.icon}`}
        >
          <TaskTypeIcon taskType={task.taskType} />
        </div>
        <span
          className={`inline-flex max-w-[92px] items-center gap-1 rounded-full px-2 py-1 text-[10px] font-medium leading-none ${statusBadgeClassName(task.status)}`}
        >
          <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-current opacity-80" />
          <span className="truncate">{status}</span>
        </span>
      </div>

      <div className="relative mt-3 min-w-0">
        <span
          className={`inline-flex rounded-[6px] px-2 py-1 text-[10px] font-medium leading-none ${theme.tag}`}
        >
          {taskTypeLabel(task.taskType)}
        </span>
        <div className="mt-2 truncate text-[14px] font-semibold leading-5 text-[#2b2e37]">
          {task.taskName}
        </div>
        <div className="mt-1 truncate text-[10px] leading-4 text-[#9a9ea7]">
          任务 ID · {task.taskId}
        </div>
      </div>

      <div className="relative mt-3 grid grid-cols-2 gap-2">
        <div className="rounded-[10px] bg-[#f7f8fa] px-3 py-2.5">
          <div className="text-[10px] leading-4 text-[#92969f]">运行次数</div>
          <div className="mt-0.5 text-[17px] font-semibold leading-6 text-[#31343c]">
            {task.runCount}
            <span className="ml-0.5 text-[10px] font-normal text-[#a0a4ac]">
              次
            </span>
          </div>
        </div>
        <div className="rounded-[10px] bg-[#f7f8fa] px-3 py-2.5">
          <div className="text-[10px] leading-4 text-[#92969f]">异常</div>
          <div
            className={`mt-0.5 text-[17px] font-semibold leading-6 ${
              task.exceptionCount > 0 ? 'text-[#df4b58]' : 'text-[#31343c]'
            }`}
          >
            {task.exceptionCount}
            <span className="ml-0.5 text-[10px] font-normal text-[#a0a4ac]">
              次
            </span>
          </div>
        </div>
      </div>

      <div className="relative mt-2.5 flex items-center justify-between rounded-[10px] border border-[#eef0f3] bg-[#fbfbfc] px-3 py-2">
        <span className="flex items-center gap-1.5 text-[10px] text-[#858a93]">
          <Clock size={12} strokeWidth={1.8} />
          本次耗时
        </span>
        <strong className="text-[11px] font-semibold text-[#4c5059]">
          {formatDuration(task.durationMs)}
        </strong>
      </div>

      <div className="relative mt-auto flex items-center justify-between border-t border-[#eef0f3] pt-3 text-[11px] font-medium text-[#666b75] transition-colors group-hover:text-[#2f333b]">
        <span>查看任务详情</span>
        <ChevronRight
          size={14}
          strokeWidth={1.8}
          className="transition-transform duration-200 group-hover:translate-x-0.5"
        />
      </div>
    </button>
  );
}

export function LatestTaskCard({
  task,
  loading,
  failed,
}: LatestTaskCardProps) {
  return (
    <aside className="w-full shrink-0 self-start lg:h-[310px] lg:w-[238px] lg:border-r lg:border-[#edf0f3] lg:pr-6">
      <div className="mb-2 flex h-5 items-center justify-between gap-2">
        <span className="text-[13px] font-semibold leading-5 text-[#353842]">
          最新任务
        </span>
        <span className="text-[10px] font-normal text-[#a1a5ad]">
          最近一次执行
        </span>
      </div>

      {task ? (
        <LatestTaskContent task={task} />
      ) : loading || failed ? (
        <div className="flex h-[280px] w-full items-center justify-center rounded-[16px] border border-[#e8ebef] bg-[#fafbfc] text-[11px] text-[#9da1a8]">
          {loading ? '任务数据加载中...' : '任务数据加载失败'}
        </div>
      ) : (
        <div className="flex h-[280px] w-full items-center justify-center rounded-[16px] border border-[#e8ebef] bg-[#fafbfc]">
          <YakOpsEmpty
            width={144}
            height={96}
            title="暂无运行任务"
            showCaption
          />
        </div>
      )}
    </aside>
  );
}
