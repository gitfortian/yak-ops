import type { WorkflowTaskDefinition } from '@/services/workflow';
import { history } from '@umijs/max';
import { Input, Tooltip } from 'antd';
import {
  Boxes,
  ChevronLeft,
  Database,
  Search,
} from 'lucide-react';
import type { DragEvent } from 'react';
import { useMemo, useState } from 'react';
import WorkflowNodeIcon from './node/icons/WorkflowNodeIcon';

interface WorkflowTaskLibraryProps {
  tasks: WorkflowTaskDefinition[];
  loading: boolean;
  locked: boolean;
  onDragStart: (event: DragEvent<HTMLDivElement>, task: WorkflowTaskDefinition) => void;
}

type LibraryPanel = 'nodes' | 'resources';

const taskTypeLabel = (taskType?: string) => {
  if (!taskType || taskType === 'SYNC') return '数据同步';
  if (taskType === 'SQL') return 'SQL';
  return taskType;
};

const RailButton = ({
  active,
  label,
  icon,
  onClick,
}: {
  active?: boolean;
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
}) => (
  <Tooltip title={label} placement="right">
    <button
      type="button"
      aria-label={label}
      className={[
        'flex h-9 w-9 items-center justify-center rounded-lg border-0 transition-colors',
        active
          ? 'bg-[#f2f4f7] text-[#161823]'
          : 'bg-transparent text-[#98a2b3] hover:bg-[#f7f8fa] hover:text-[#475467]',
      ].join(' ')}
      onClick={onClick}
    >
      {icon}
    </button>
  </Tooltip>
);

const WorkflowTaskLibrary = ({
  tasks,
  loading,
  locked,
  onDragStart,
}: WorkflowTaskLibraryProps) => {
  const [activePanel, setActivePanel] = useState<LibraryPanel>('nodes');
  const [keyword, setKeyword] = useState('');

  const filteredTasks = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    if (!normalized) return tasks;
    return tasks.filter((task) =>
      task.name.toLowerCase().includes(normalized)
      || taskTypeLabel(task.type).toLowerCase().includes(normalized));
  }, [keyword, tasks]);

  return (
    <aside className="flex w-[280px] shrink-0 border-r border-[#e8eaee] bg-white">
      <div className="flex w-12 shrink-0 flex-col items-center border-r border-[#eef0f2] bg-[#fbfbfc] py-2">
        <Tooltip title="返回工作流列表" placement="right">
          <button
            type="button"
            aria-label="返回工作流列表"
            className="mb-2 flex h-9 w-9 items-center justify-center rounded-lg border-0 bg-transparent text-[#667085] transition-colors hover:bg-[#f2f4f7] hover:text-[#161823]"
            onClick={() => history.push('/workflow/definitions')}
          >
            <ChevronLeft size={17} strokeWidth={1.9} />
          </button>
        </Tooltip>

        <div className="mb-2 h-px w-6 bg-[#eceef1]" />

        <RailButton
          active={activePanel === 'nodes'}
          label="节点"
          icon={<Boxes size={17} strokeWidth={1.8} />}
          onClick={() => setActivePanel('nodes')}
        />
        <RailButton
          active={activePanel === 'resources'}
          label="资源"
          icon={<Database size={17} strokeWidth={1.8} />}
          onClick={() => setActivePanel('resources')}
        />
      </div>

      <div className="flex min-w-0 flex-1 flex-col bg-white">
        <div className="flex h-11 shrink-0 items-center px-4 text-[13px] font-semibold text-[#161823]">
          {activePanel === 'nodes' ? '节点' : '资源'}
        </div>

        {activePanel === 'nodes' ? (
          <>
            <div className="px-3 pb-3">
              <Input
                allowClear
                variant="filled"
                value={keyword}
                prefix={<Search size={13} className="text-[#98a2b3]" />}
                placeholder="搜索节点"
                className="!h-8 !rounded-lg !text-[12px]"
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-4">
              <div className="mb-2 px-1 text-[10px] font-medium uppercase tracking-[0.08em] text-[#98a2b3]">
                任务节点
              </div>

              {loading ? (
                <div className="space-y-2">
                  {[0, 1, 2].map((item) => (
                    <div key={item} className="h-12 animate-pulse rounded-lg bg-[#f5f6f7]" />
                  ))}
                </div>
              ) : filteredTasks.length ? (
                <div className="space-y-1.5">
                  {filteredTasks.map((task) => (
                    <div
                      key={task.id}
                      draggable={!locked}
                      className={[
                        'group flex min-h-12 items-center gap-2.5 rounded-lg border border-transparent px-2.5 py-2 transition-colors',
                        locked
                          ? 'cursor-not-allowed opacity-50'
                          : 'cursor-grab hover:border-[#e4e7ec] hover:bg-[#fafbfc] active:cursor-grabbing',
                      ].join(' ')}
                      onDragStart={(event) => !locked && onDragStart(event, task)}
                    >
                      <WorkflowNodeIcon taskType={task.type} size="sm" />
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-[12px] font-medium text-[#344054]">
                          {task.name}
                        </div>
                        <div className="mt-0.5 truncate text-[10px] text-[#98a2b3]">
                          {taskTypeLabel(task.type)}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="px-3 py-10 text-center text-[11px] text-[#98a2b3]">
                  暂无匹配节点
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="flex min-h-0 flex-1 flex-col px-3 pb-4">
            <div className="rounded-lg bg-[#f7f8fa] px-3 py-3 text-[11px] leading-5 text-[#667085]">
              资源面板用于集中展示工作流可引用的数据源、文件和运行资源。
            </div>
            <div className="flex flex-1 items-center justify-center text-[11px] text-[#b0b4bc]">
              暂无可用资源
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};

export default WorkflowTaskLibrary;
