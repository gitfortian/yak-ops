import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { HomeRecentTask } from '@/services/home';
import { history } from '@umijs/max';
import { Clock3, Database } from 'lucide-react';

import {
  formatDuration,
  formatRunTime,
  statusClassName,
  statusLabel,
} from '../../utils/homeDataCenter';

interface RecentTasksPanelProps {
  items: HomeRecentTask[];
  loading: boolean;
  failed: boolean;
}

export function RecentTasksPanel({
  items,
  loading,
  failed,
}: RecentTasksPanelProps) {
  if (loading || failed) {
    return (
      <div className="flex min-h-[263px] items-center justify-center text-[12px] text-[#9da1a8]">
        {loading ? '近期任务加载中...' : '近期任务加载失败'}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="flex min-h-[263px] items-center justify-center">
        <YakOpsEmpty
          width={160}
          height={108}
          title="暂无近期任务"
          showCaption
        />
      </div>
    );
  }

  return (
    <div className="min-h-[263px] pt-2">
      {items.map((item) => (
        <button
          key={`${item.taskType}-${item.taskId}`}
          type="button"
          onClick={() => {
            if (item.detailPath) history.push(item.detailPath);
          }}
          className="group grid w-full grid-cols-[minmax(230px,1.5fr)_repeat(4,minmax(72px,.55fr))_150px] items-center gap-3 rounded-[8px] px-3 py-3 text-left transition-colors hover:bg-[#f7f8fa]"
        >
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#edf4ff] text-[#5b8cff]">
              <Database size={16} strokeWidth={1.9} />
            </div>
            <div className="min-w-0">
              <div className="truncate text-[12px] font-medium text-[#363a43]">
                {item.taskName}
              </div>
              <div className="mt-1 flex items-center gap-1 text-[11px] text-[#969aa3]">
                <Clock3 size={11} strokeWidth={1.8} />
                {formatRunTime(item.lastRunTime)}
              </div>
            </div>
          </div>

          {[
            ['运行', String(item.runCount)],
            ['成功', String(item.successCount)],
            ['失败', String(item.failedCount)],
            ['耗时', formatDuration(item.lastDurationMs)],
          ].map(([label, value]) => (
            <div key={label} className="min-w-0">
              <span className="text-[11px] text-[#969aa3]">{label}</span>
              <strong className="ml-2 text-[12px] font-semibold text-[#3b3f48]">
                {value}
              </strong>
            </div>
          ))}

          <div className="flex items-center justify-end gap-4">
            <span
              className={`text-[11px] font-medium ${statusClassName(item.lastStatus)}`}
            >
              {statusLabel(item.lastStatus)}
            </span>
            <span className="text-[12px] font-semibold text-[#323640] transition-colors group-hover:text-[#5b8cff]">
              查看详情
            </span>
          </div>
        </button>
      ))}
    </div>
  );
}
