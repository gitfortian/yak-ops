import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { HomeLatestTask } from '@/services/home';
import { history } from '@umijs/max';
import { ChevronRight, Layers2 } from 'lucide-react';

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

interface TaskMetricRowProps {
  label: string;
  value: React.ReactNode;
  danger?: boolean;
}

function TaskMetricRow({
  label,
  value,
  danger = false,
}: TaskMetricRowProps) {
  return (
    <div className="flex h-6 items-center justify-between">
      <span className="text-[11px] font-medium text-white/90">
        {label}
      </span>

      <span
        className={`
          max-w-[84px]
          truncate
          text-[11px]
          font-semibold
          ${
            danger
              ? 'text-[#ff8993]'
              : 'text-white'
          }
        `}
        title={typeof value === 'string' ? value : undefined}
      >
        {value}
      </span>
    </div>
  );
}

function LatestTaskContent({
  task,
}: {
  task: HomeLatestTask;
}) {
  const status = statusLabel(task.status);

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
      title={task.taskName}
      className="
        group
        relative
        h-[262px]
        w-full
        overflow-hidden
        rounded-[8px]
        bg-[#8f9092]
        text-left
        outline-none
        transition-shadow
        duration-200
        hover:shadow-[0_6px_20px_rgba(31,35,41,0.12)]
        focus-visible:ring-2
        focus-visible:ring-[#d9e4ff]
        lg:w-[193px]
      "
    >
      {/* ==================== 封面 ==================== */}

      <div
        className="
          absolute
          inset-0
          overflow-hidden
          transition-[filter,transform]
          duration-300
          ease-out
          group-hover:scale-[1.045]
          group-hover:blur-[8px]
          group-focus:scale-[1.045]
          group-focus:blur-[8px]
        "
      >
        {/* 灰色作品封面背景 */}
        <div
          className="
            absolute
            inset-0
            bg-[linear-gradient(180deg,#858688_0%,#a4a5a7_48%,#8d8e90_100%)]
          "
        />

        {/* 一些很淡的背景光斑，让模糊后更像视频里的作品封面 */}
        <div
          aria-hidden="true"
          className="
            absolute
            -left-8
            top-[74px]
            h-[90px]
            w-[90px]
            rounded-full
            bg-white/20
            blur-2xl
          "
        />

        <div
          aria-hidden="true"
          className="
            absolute
            -right-8
            top-[42px]
            h-[120px]
            w-[120px]
            rounded-full
            bg-white/15
            blur-2xl
          "
        />

        {/* Yak Ops 插画 */}
        <div
          className="
            absolute
            inset-x-0
            top-[44px]
            flex
            h-[136px]
            items-end
            justify-center
            overflow-hidden
          "
        >
          <div
            className="
              translate-y-[7px]
              transition-transform
              duration-300
              ease-out
              group-hover:scale-[1.02]
            "
          >
            <YakOpsEmpty
              width={170}
              height={132}
              title={task.taskName}
            />
          </div>
        </div>
      </div>

      {/* hover 后整体压暗一点 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          inset-0
          z-[2]
          bg-black/0
          transition-colors
          duration-300
          group-hover:bg-black/[0.08]
          group-focus:bg-black/[0.08]
        "
      />

      {/* ==================== 顶部作品信息 ==================== */}

      <div
        className="
          absolute
          inset-x-0
          top-0
          z-20
          flex
          items-start
          justify-between
          px-3
          pt-3
        "
      >
        <div className="min-w-0 pr-2 text-white">
          {/* 对应视频里的“1张” */}
          <div
            className="
              truncate
              text-[11px]
              font-semibold
              leading-[16px]
            "
          >
            {taskTypeLabel(task.taskType)}
          </div>

          {/* 对应视频里的“00:00” */}
          <div
            className="
              mt-[1px]
              text-[12px]
              font-semibold
              leading-[16px]
            "
          >
            {formatDuration(task.durationMs)}
          </div>

          {/* 对应视频第三行 */}
          <div
            className="
              mt-[1px]
              max-w-[110px]
              truncate
              text-[10px]
              font-medium
              leading-[14px]
              text-white/90
            "
            title={task.taskName}
          >
            {task.taskName}
          </div>
        </div>

        {/* 视频右上角小图标 */}
        <div
          className="
            mt-[1px]
            flex
            h-[20px]
            w-[20px]
            shrink-0
            items-center
            justify-center
            text-white
          "
        >
          <Layers2
            size={15}
            strokeWidth={2.4}
          />
        </div>
      </div>

      {/* ==================== 底部信息区域 ==================== */}

      <div
        className="
          absolute
          inset-x-0
          bottom-0
          z-10
          h-[72px]
          overflow-hidden
          transition-[height]
          duration-300
          ease-out
          group-hover:h-[150px]
          group-focus:h-[150px]
        "
      >
        {/* 和视频一致：越靠底部越深 */}
        <div
          aria-hidden="true"
          className="
            pointer-events-none
            absolute
            inset-0
            bg-[linear-gradient(180deg,rgba(45,46,48,0)_0%,rgba(45,46,48,0.36)_18%,rgba(44,45,47,0.78)_100%)]
          "
        />

        <div
          className="
            relative
            z-10
            px-3
            pt-[10px]
          "
        >
          {/* 默认状态就能看到这两项 */}
          <TaskMetricRow
            label="运行次数"
            value={task.runCount}
          />

          <TaskMetricRow
            label="异常次数"
            value={task.exceptionCount}
            danger={task.exceptionCount > 0}
          />

          {/* Hover 后露出来 */}
          <div
            className="
              opacity-0
              transition-opacity
              delay-0
              duration-150
              group-hover:opacity-100
              group-focus:opacity-100
            "
          >
            <TaskMetricRow
              label="任务状态"
              value={status}
              danger={status === '失败'}
            />

            <TaskMetricRow
              label="任务 ID"
              value={String(task.taskId)}
            />

            <div
              className="
                mt-[4px]
                flex
                h-[24px]
                items-center
                gap-[2px]
                text-[11px]
                font-semibold
                text-white
              "
            >
              <span>查看详情</span>

              <ChevronRight
                size={14}
                strokeWidth={2}
                className="
                  transition-transform
                  duration-200
                  group-hover:translate-x-[2px]
                "
              />
            </div>
          </div>
        </div>
      </div>

      {/* 很轻的内部描边 */}
      <div
        aria-hidden="true"
        className="
          pointer-events-none
          absolute
          inset-0
          z-30
          rounded-[8px]
          ring-1
          ring-inset
          ring-black/[0.04]
        "
      />
    </button>
  );
}

function TaskLoadingCard() {
  return (
    <div
      className="
        flex
        h-[262px]
        w-full
        items-center
        justify-center
        overflow-hidden
        rounded-[8px]
        bg-[#f4f5f6]
        lg:w-[176px]
      "
    >
      <div className="flex flex-col items-center gap-2">
        <div
          className="
            h-5
            w-5
            animate-spin
            rounded-full
            border-2
            border-[#d9dce1]
            border-t-[#8b8f97]
          "
        />

        <span className="text-[11px] text-[#8b8f97]">
          加载中...
        </span>
      </div>
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
      <span className="text-[11px] text-[#9ca0a8]">
        任务数据加载失败
      </span>
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
        overflow-hidden
        rounded-[8px]
        bg-[#f7f8fa]
        lg:w-[176px]
      "
    >
      <YakOpsEmpty
        width={136}
        height={100}
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
        lg:w-[218px]
        lg:border-r
        lg:border-[#eef0f2]
        lg:pr-[23px]
      "
    >
      <div className="mb-2 flex h-5 items-center">
        <span
          className="
            text-[13px]
            font-semibold
            leading-5
            text-[#272a31]
          "
        >
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