import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { HomeLatestTask } from '@/services/home';
import { history } from '@umijs/max';
import { Copy, Database } from 'lucide-react';

import {
  formatCardDuration,
  taskTypeLabel,
} from '../../utils/homeDataCenter';

interface LatestTaskCardProps {
  task?: HomeLatestTask;
  loading: boolean;
  failed: boolean;
}

export function LatestTaskCard({
  task,
  loading,
  failed,
}: LatestTaskCardProps) {
  return (
    <aside className="w-full shrink-0 lg:w-[220px] lg:border-r lg:border-[#edf0f3] lg:pr-5">
      <div className="mb-2 text-[13px] font-semibold leading-5 text-[#353842]">
        最新任务
      </div>

      {task ? (
        <button
          type="button"
          onClick={() => {
            if (task.detailPath) history.push(task.detailPath);
          }}
          className="group relative h-[266px] w-full overflow-hidden rounded-[10px] border border-[#e8ebef] bg-[linear-gradient(150deg,#6c737d_0%,#9197a0_48%,#c0c4ca_100%)] text-left text-white transition-[border-color,transform] duration-200 hover:-translate-y-px hover:border-[#dfe3e8] lg:w-[198px]"
        >
          <div className="pointer-events-none absolute inset-0 opacity-[0.18] [background-image:linear-gradient(rgba(255,255,255,.28)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.22)_1px,transparent_1px)] [background-size:22px_22px]" />
          <div className="pointer-events-none absolute -right-9 top-10 h-32 w-32 rounded-full border border-white/20" />
          <div className="pointer-events-none absolute -right-2 top-16 h-24 w-24 rounded-full border border-white/20" />

          <div className="absolute left-4 top-3 z-10">
            <div className="text-[12px] font-semibold text-white/95">
              {taskTypeLabel(task.taskType)}
            </div>
            <div className="mt-0.5 text-[11px] text-white/80">
              {formatCardDuration(task.durationMs)}
            </div>
          </div>

          <Copy
            size={14}
            strokeWidth={1.8}
            className="absolute right-3 top-3 z-10 text-white/90"
          />

          <div className="absolute inset-x-0 top-[50px] z-10 flex justify-center">
            <div className="relative flex h-[122px] w-[122px] items-center justify-center">
              <span className="absolute h-[112px] w-[112px] rounded-full border border-white/25" />
              <span className="absolute h-[82px] w-[82px] rounded-full border border-white/18" />
              <span className="absolute h-[52px] w-[52px] rounded-full bg-white/12 backdrop-blur-[2px]" />
              <Database
                size={52}
                strokeWidth={1.15}
                className="relative text-white/95"
              />
            </div>
          </div>

          <div className="absolute inset-x-0 bottom-[70px] z-10 px-4">
            <div className="truncate text-[12px] font-medium text-white/95">
              {task.taskName}
            </div>
          </div>

          <div className="absolute inset-x-0 bottom-0 z-10 h-[70px] bg-black/20 px-4 backdrop-blur-[12px]">
            <div className="flex h-1/2 items-center justify-between border-b border-white/12">
              <span className="text-[11px] text-white/78">运行次数</span>
              <strong className="text-[12px] font-semibold">{task.runCount}</strong>
            </div>
            <div className="flex h-1/2 items-center justify-between">
              <span className="text-[11px] text-white/78">异常</span>
              <strong className="text-[12px] font-semibold">
                {task.exceptionCount}
              </strong>
            </div>
          </div>
        </button>
      ) : loading || failed ? (
        <div className="flex h-[266px] w-full items-center justify-center rounded-[10px] border border-[#e8ebef] bg-[#fafbfc] text-[11px] text-[#9da1a8] lg:w-[198px]">
          {loading ? '任务数据加载中...' : '任务数据加载失败'}
        </div>
      ) : (
        <div className="flex h-[266px] w-full items-center justify-center rounded-[10px] border border-[#e8ebef] bg-[#fafbfc] lg:w-[198px]">
          <YakOpsEmpty
            width={150}
            height={100}
            title="暂无运行任务"
            showCaption
          />
        </div>
      )}
    </aside>
  );
}
