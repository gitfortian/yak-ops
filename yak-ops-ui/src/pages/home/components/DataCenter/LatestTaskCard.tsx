import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { HomeLatestTask } from '@/services/home';
import { history } from '@umijs/max';
import {
  ArrowRightLeft,
  Clock3,
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

interface TaskTheme {
  cover: string;
  iconBox: string;
  icon: string;
  tag: string;
}

const TASK_THEMES: Record<HomeLatestTask['taskType'], TaskTheme> = {
  OFFLINE_SYNC: {
    cover:
      'bg-[linear-gradient(145deg,#f1f3ff_0%,#e9edff_48%,#dfe5ff_100%)]',
    iconBox: 'bg-white/80',
    icon: 'text-[#6675f5]',
    tag: 'bg-[#6675f5] text-white',
  },

  WORKFLOW: {
    cover:
      'bg-[linear-gradient(145deg,#fff8e6_0%,#fff1c7_48%,#ffe7a3_100%)]',
    iconBox: 'bg-white/80',
    icon: 'text-[#d99b00]',
    tag: 'bg-[#e9aa10] text-white',
  },

  DATA_QUALITY: {
    cover:
      'bg-[linear-gradient(145deg,#eefaf4_0%,#e1f6eb_48%,#d2efdf_100%)]',
    iconBox: 'bg-white/80',
    icon: 'text-[#249a67]',
    tag: 'bg-[#2ba36e] text-white',
  },
};

function TaskTypeIcon({
  taskType,
  size = 32,
}: {
  taskType: HomeLatestTask['taskType'];
  size?: number;
}) {
  if (taskType === 'OFFLINE_SYNC') {
    return <ArrowRightLeft size={size} strokeWidth={1.65} />;
  }

  if (taskType === 'WORKFLOW') {
    return <Workflow size={size} strokeWidth={1.65} />;
  }

  return <ShieldCheck size={size} strokeWidth={1.65} />;
}

function StatusDot({ status }: { status?: string }) {
  const label = statusLabel(status);

  let dotClassName = 'bg-[#9ca3af]';

  if (label === '成功') {
    dotClassName = 'bg-[#34b27b]';
  }

  if (label === '失败') {
    dotClassName = 'bg-[#ef5361]';
  }

  if (label === '运行中') {
    dotClassName = 'bg-[#4f7df3]';
  }

  if (label === '已取消') {
    dotClassName = 'bg-[#90949c]';
  }

  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className={`h-1.5 w-1.5 shrink-0 rounded-full ${dotClassName}`}
      />
      <span>{label}</span>
    </span>
  );
}

function LatestTaskContent({ task }: { task: HomeLatestTask }) {
  const theme = TASK_THEMES[task.taskType];

  const handleClick = () => {
    if (!task.detailPath) {
      return;
    }

    history.push(task.detailPath);
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      className="
        group
        relative
        h-[240px]
        w-full
        overflow-hidden
        rounded-[8px]
        border
        border-[#eaecf0]
        text-left
        transition-all
        duration-200
        hover:border-[#dfe3e8]
        hover:shadow-[0_5px_16px_rgba(31,35,41,0.08)]
        focus-visible:outline-none
        focus-visible:ring-2
        focus-visible:ring-[#dbe5ff]
        lg:w-[176px]
      "
    >
      {/* 封面背景 */}
      <div className={`absolute inset-0 ${theme.cover}`} />

      {/* 装饰圆 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          -right-10
          top-8
          h-[150px]
          w-[150px]
          rounded-full
          border
          border-white/35
        "
      />

      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          -right-2
          top-[54px]
          h-[100px]
          w-[100px]
          rounded-full
          border
          border-white/45
        "
      />

      {/* 顶部 */}
      <div className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-3 pt-3">
        <span
          className={`
            inline-flex
            h-5
            items-center
            rounded-[4px]
            px-1.5
            text-[10px]
            font-medium
            ${theme.tag}
          `}
        >
          {taskTypeLabel(task.taskType)}
        </span>

        <span
          className="
            inline-flex
            h-5
            items-center
            rounded-full
            bg-white/80
            px-2
            text-[10px]
            font-medium
            text-[#60646d]
            backdrop-blur-sm
          "
        >
          <StatusDot status={task.status} />
        </span>
      </div>

      {/* 中间任务视觉 */}
      <div className="absolute inset-x-0 top-[44px] flex justify-center">
        <div
          className={`
            flex
            h-[72px]
            w-[72px]
            items-center
            justify-center
            rounded-[22px]
            shadow-[0_8px_24px_rgba(31,35,41,0.06)]
            backdrop-blur-sm
            ${theme.iconBox}
            ${theme.icon}
            transition-transform
            duration-300
            group-hover:-translate-y-0.5
            group-hover:scale-[1.02]
          `}
        >
          <TaskTypeIcon taskType={task.taskType} size={34} />
        </div>
      </div>

      {/* 底部渐变遮罩，参考抖音作品封面 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          inset-x-0
          bottom-0
          h-[126px]
          bg-[linear-gradient(180deg,rgba(30,32,38,0)_0%,rgba(30,32,38,0.18)_26%,rgba(28,30,36,0.86)_100%)]
        "
      />

      {/* 任务信息 */}
      <div className="absolute inset-x-0 bottom-0 z-10 px-3 pb-3">
        <div
          className="
            line-clamp-2
            min-h-[36px]
            text-[13px]
            font-semibold
            leading-[18px]
            text-white
          "
          title={task.taskName}
        >
          {task.taskName}
        </div>

        <div className="mt-1 flex items-center gap-1 text-[9px] text-white/65">
          <span className="truncate">ID {task.taskId}</span>

          <span className="shrink-0">·</span>

          <span className="flex shrink-0 items-center gap-1">
            <Clock3 size={9} strokeWidth={1.8} />
            {formatDuration(task.durationMs)}
          </span>
        </div>

        <div className="mt-2.5 border-t border-white/15 pt-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-white/70">运行次数</span>

              <span className="text-[11px] font-semibold text-white">
                {task.runCount}
              </span>
            </div>

            <div className="flex items-center justify-between">
              <span className="text-[10px] text-white/70">异常</span>

              <span
                className={`text-[11px] font-semibold ${
                  task.exceptionCount > 0
                    ? 'text-[#ff8d96]'
                    : 'text-white'
                }`}
              >
                {task.exceptionCount}
              </span>
            </div>
          </div>
        </div>
      </div>
    </button>
  );
}

function TaskLoadingCard() {
  return (
    <div
      className="
        flex
        h-[240px]
        w-full
        items-center
        justify-center
        rounded-[8px]
        bg-[#f7f8fa]
        lg:w-[176px]
      "
    >
      <span className="text-[11px] text-[#9ca0a8]">任务数据加载中...</span>
    </div>
  );
}

function TaskFailedCard() {
  return (
    <div
      className="
        flex
        h-[240px]
        w-full
        items-center
        justify-center
        rounded-[8px]
        bg-[#f7f8fa]
        px-4
        text-center
        lg:w-[176px]
      "
    >
      <span className="text-[11px] text-[#9ca0a8]">任务数据加载失败</span>
    </div>
  );
}

function TaskEmptyCard() {
  return (
    <div
      className="
        flex
        h-[240px]
        w-full
        items-center
        justify-center
        rounded-[8px]
        bg-[#f7f8fa]
        lg:w-[176px]
      "
    >
      <YakOpsEmpty
        width={136}
        height={92}
        title="暂无运行任务"
        showCaption
      />
    </div>
  );
}

export function LatestTaskCard({
  task,
  loading,
  failed,
}: LatestTaskCardProps) {
  return (
    <aside
      className="
        w-full
        shrink-0
        self-start
        lg:w-[200px]
        lg:border-r
        lg:border-[#eef0f2]
        lg:pr-[23px]
      "
    >
      {/* 和抖音一样：标题直接压在卡片上方，不再搞复杂 header */}
      <div className="mb-2 flex h-[20px] items-center justify-between">
        <span className="text-[13px] font-semibold leading-5 text-[#33363f]">
          最新任务
        </span>
      </div>

      {task ? (
        <LatestTaskContent task={task} />
      ) : loading ? (
        <TaskLoadingCard />
      ) : failed ? (
        <TaskFailedCard />
      ) : (
        <TaskEmptyCard />
      )}
    </aside>
  );
}