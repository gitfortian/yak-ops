import { Popover, Tooltip } from 'antd';
import {
  CirclePlus,
  Hand,
  History,
  Maximize2,
  MousePointer2,
  Redo2,
  StickyNote,
  Undo2,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNodesInitialized, useReactFlow, useViewport } from 'reactflow';
import WorkflowNodeIcon from './node/icons/WorkflowNodeIcon';
import WorkflowTaskPicker from './WorkflowTaskPicker';
import type { WorkflowCanvasTaskOption } from './types';
import type { WorkflowCanvasHistoryEntry } from './useWorkflowCanvasHistory';

export type WorkflowCanvasMode = 'pointer' | 'hand';

interface WorkflowCanvasToolsProps<T> {
  mode: WorkflowCanvasMode;
  locked: boolean;
  taskOptions: WorkflowCanvasTaskOption[];
  historyEntries: Array<WorkflowCanvasHistoryEntry<T>>;
  currentHistoryIndex: number;
  canUndo: boolean;
  canRedo: boolean;
  onModeChange: (mode: WorkflowCanvasMode) => void;
  onAddTask: (taskId: string) => void;
  onAddNote: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onJumpToHistory: (index: number) => void;
  onClearHistory: () => void;
}

interface CandidatePointer {
  x: number;
  y: number;
}

const iconButtonClass = (active = false) => [
  'flex h-8 w-8 items-center justify-center rounded-md border-0 transition-colors',
  active
    ? 'bg-[rgba(254,44,85,.08)] text-[#fe2c55]'
    : 'bg-transparent text-[#667085] hover:bg-[#f2f4f7] hover:text-[#344054]',
].join(' ');

const disabledButtonClass = 'disabled:cursor-not-allowed disabled:text-[#c6c9cf] disabled:hover:bg-transparent';

const isEditableTarget = (target: EventTarget | null) => {
  const element = target as HTMLElement | null;
  if (!element) return false;
  const tagName = element.tagName?.toLowerCase();
  return tagName === 'input' || tagName === 'textarea' || tagName === 'select' || element.isContentEditable;
};

const WorkflowCanvasTools = <T,>({
  mode,
  locked,
  taskOptions,
  historyEntries,
  currentHistoryIndex,
  canUndo,
  canRedo,
  onModeChange,
  onAddTask,
  onAddNote,
  onUndo,
  onRedo,
  onJumpToHistory,
  onClearHistory,
}: WorkflowCanvasToolsProps<T>) => {
  const [historyOpen, setHistoryOpen] = useState(false);
  const [candidateTaskId, setCandidateTaskId] = useState<string>();
  const [candidatePointer, setCandidatePointer] = useState<CandidatePointer>();
  const lastPointerRef = useRef<CandidatePointer>({ x: 0, y: 0 });
  const reactFlow = useReactFlow();
  const { zoom } = useViewport();
  const nodesInitialized = useNodesInitialized();
  const initialFitDoneRef = useRef(false);

  const candidateTask = taskOptions.find((task) => task.id === candidateTaskId);

  const cancelCandidate = useCallback(() => {
    setCandidateTaskId(undefined);
    setCandidatePointer(undefined);
  }, []);

  const beginCandidate = useCallback((taskId: string) => {
    if (locked) return;
    setHistoryOpen(false);
    onModeChange('pointer');
    setCandidateTaskId(taskId);
    setCandidatePointer(lastPointerRef.current.x || lastPointerRef.current.y
      ? { ...lastPointerRef.current }
      : undefined);
  }, [locked, onModeChange]);

  useEffect(() => {
    if (!nodesInitialized || initialFitDoneRef.current) return;
    initialFitDoneRef.current = true;

    const frame = window.requestAnimationFrame(() => {
      void reactFlow.fitView({ padding: 0.18, maxZoom: 0.9, duration: 0 });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [nodesInitialized, reactFlow]);

  useEffect(() => {
    const rememberPointer = (event: PointerEvent) => {
      lastPointerRef.current = { x: event.clientX, y: event.clientY };
    };
    window.addEventListener('pointermove', rememberPointer, { passive: true });
    return () => window.removeEventListener('pointermove', rememberPointer);
  }, []);

  useEffect(() => {
    if (!candidateTaskId) return;

    const previousCursor = document.body.style.cursor;
    document.body.style.cursor = 'crosshair';

    const handlePointerMove = (event: PointerEvent) => {
      const next = { x: event.clientX, y: event.clientY };
      lastPointerRef.current = next;
      setCandidatePointer(next);
    };

    const handleClick = (event: MouseEvent) => {
      if (event.button !== 0) return;
      const target = event.target instanceof Element
        ? event.target
        : document.elementFromPoint(event.clientX, event.clientY);
      const flowRoot = target?.closest('.react-flow');
      if (!flowRoot || !candidateTask) return;

      event.preventDefault();
      event.stopPropagation();

      try {
        const dataTransfer = new DataTransfer();
        dataTransfer.setData('application/yak-workflow-task', JSON.stringify({
          id: candidateTask.id,
          name: candidateTask.label,
          type: candidateTask.taskType || 'SYNC',
        }));
        dataTransfer.effectAllowed = 'move';

        flowRoot.dispatchEvent(new DragEvent('drop', {
          bubbles: true,
          cancelable: true,
          clientX: event.clientX,
          clientY: event.clientY,
          dataTransfer,
        }));
      } catch {
        // Older browser fallback: preserve the previous add behavior instead of losing the action.
        onAddTask(candidateTask.id);
      }

      cancelCandidate();
    };

    const handleContextMenu = (event: MouseEvent) => {
      event.preventDefault();
      cancelCandidate();
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      cancelCandidate();
    };

    window.addEventListener('pointermove', handlePointerMove, { passive: true });
    window.addEventListener('click', handleClick, true);
    window.addEventListener('contextmenu', handleContextMenu, true);
    window.addEventListener('keydown', handleKeyDown, true);

    return () => {
      document.body.style.cursor = previousCursor;
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('click', handleClick, true);
      window.removeEventListener('contextmenu', handleContextMenu, true);
      window.removeEventListener('keydown', handleKeyDown, true);
    };
  }, [candidateTask, candidateTaskId, cancelCandidate, onAddTask]);

  useEffect(() => {
    if (!locked) return;
    cancelCandidate();
  }, [cancelCandidate, locked]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (locked || candidateTaskId || isEditableTarget(event.target)) return;

      const modifier = event.metaKey || event.ctrlKey;
      if (modifier && event.key.toLowerCase() === 'z') {
        event.preventDefault();
        if (event.shiftKey) onRedo();
        else onUndo();
        return;
      }
      if (modifier && event.key.toLowerCase() === 'y') {
        event.preventDefault();
        onRedo();
        return;
      }
      if (!modifier && !event.altKey && !event.shiftKey) {
        if (event.key.toLowerCase() === 'v') onModeChange('pointer');
        if (event.key.toLowerCase() === 'h') onModeChange('hand');
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [candidateTaskId, locked, onModeChange, onRedo, onUndo]);

  const historyContent = (
    <div className="w-[320px] overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_36px_rgba(22,24,35,.14)]">
      <div className="flex h-11 items-center justify-between px-3.5">
        <div className="text-[14px] font-medium text-[#344054]">变更历史</div>
        <button
          type="button"
          aria-label="关闭变更历史"
          className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#667085] hover:bg-[#f2f4f7]"
          onClick={() => setHistoryOpen(false)}
        >
          <X size={15} />
        </button>
      </div>

      <div className="max-h-[360px] overflow-y-auto px-2 pb-2">
        {historyEntries.length <= 1 ? (
          <div className="py-10 text-center text-[12px] text-[#98a2b3]">暂无变更记录</div>
        ) : (
          [...historyEntries]
            .map((entry, index) => ({ entry, index }))
            .reverse()
            .map(({ entry, index }) => {
              const diff = index - currentHistoryIndex;
              const stepText = diff === 0
                ? '当前状态'
                : diff < 0
                  ? `${Math.abs(diff)} 步后退`
                  : `${diff} 步前进`;

              return (
                <button
                  key={entry.id}
                  type="button"
                  className={[
                    'mb-0.5 flex w-full items-center rounded-lg border-0 px-2.5 py-2 text-left transition-colors',
                    diff === 0 ? 'bg-[#f2f4f7]' : 'bg-transparent hover:bg-[#f7f8fa]',
                  ].join(' ')}
                  onClick={() => {
                    onJumpToHistory(index);
                    setHistoryOpen(false);
                  }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[12px] font-medium text-[#475467]">{entry.label}</div>
                    <div className="mt-0.5 text-[10px] text-[#98a2b3]">{stepText}</div>
                  </div>
                </button>
              );
            })
        )}
      </div>

      {historyEntries.length > 1 ? (
        <div className="border-t border-[#f0f1f3] px-2 py-1.5">
          <button
            type="button"
            className="flex w-full rounded-lg border-0 bg-transparent px-2.5 py-2 text-left text-[12px] font-medium text-[#475467] hover:bg-[#f7f8fa]"
            onClick={() => {
              onClearHistory();
              setHistoryOpen(false);
            }}
          >
            清除历史记录
          </button>
        </div>
      ) : null}

      <div className="border-t border-[#f0f1f3] px-3.5 py-3 text-[10px] leading-[18px] text-[#98a2b3]">
        <div className="mb-1 font-medium text-[#667085]">提示</div>
        编辑历史仅保存在当前浏览器会话中，用于撤销、重做和快速回到之前的编辑状态。
      </div>
    </div>
  );

  return (
    <>
      <style>{`
        .react-flow__controls {
          display: none !important;
        }
        .react-flow__minimap {
          right: 12px !important;
          bottom: 12px !important;
          transition: right 180ms ease;
        }
        div:has(> aside) > .react-flow .react-flow__minimap {
          right: 424px !important;
        }
      `}</style>

      {candidateTask && candidatePointer ? (
        <div
          className="pointer-events-none fixed z-[1000] w-60"
          style={{
            left: candidatePointer.x,
            top: candidatePointer.y,
            transform: `scale(${zoom})`,
            transformOrigin: '0 0',
          }}
        >
          <div className="rounded-[15px] border border-[#d7d9de] bg-white px-3 py-3 shadow-[0_8px_24px_rgba(22,24,35,.14)] opacity-95">
            <div className="flex min-h-9 items-center gap-2.5">
              <WorkflowNodeIcon taskType={candidateTask.taskType} />
              <div className="min-w-0 flex-1 truncate text-[14px] font-semibold leading-5 text-[#161823]">
                {candidateTask.label}
              </div>
            </div>
          </div>
        </div>
      ) : null}

      <div className="pointer-events-auto absolute left-3 top-1/2 z-10 flex -translate-y-1/2 flex-col items-center rounded-lg border border-[#e4e7ec] bg-white p-0.5 shadow-[0_4px_14px_rgba(22,24,35,.08)]">
        <WorkflowTaskPicker
          options={taskOptions}
          disabled={locked || !taskOptions.length}
          placement="rightTop"
          onSelect={beginCandidate}
        >
          <span>
            <Tooltip title="新增节点" placement="right">
              <button
                type="button"
                aria-label="新增节点"
                disabled={locked || !taskOptions.length}
                className={`${iconButtonClass(Boolean(candidateTaskId))} ${disabledButtonClass}`}
              >
                <CirclePlus size={16} strokeWidth={1.9} />
              </button>
            </Tooltip>
          </span>
        </WorkflowTaskPicker>

        <Tooltip title="添加注释" placement="right">
          <button
            type="button"
            aria-label="添加注释"
            disabled={locked}
            className={`${iconButtonClass()} ${disabledButtonClass}`}
            onClick={onAddNote}
          >
            <StickyNote size={16} strokeWidth={1.9} />
          </button>
        </Tooltip>

        <div className="my-1 h-px w-5 bg-[#eceef1]" />

        <Tooltip title="选择模式（V）" placement="right">
          <button
            type="button"
            aria-label="选择模式"
            disabled={locked}
            className={`${iconButtonClass(mode === 'pointer')} ${disabledButtonClass}`}
            onClick={() => onModeChange('pointer')}
          >
            <MousePointer2 size={16} strokeWidth={1.9} />
          </button>
        </Tooltip>

        <Tooltip title="画布拖拽模式（H）" placement="right">
          <button
            type="button"
            aria-label="画布拖拽模式"
            disabled={locked}
            className={`${iconButtonClass(mode === 'hand')} ${disabledButtonClass}`}
            onClick={() => onModeChange('hand')}
          >
            <Hand size={16} strokeWidth={1.9} />
          </button>
        </Tooltip>

        <div className="my-1 h-px w-5 bg-[#eceef1]" />

        <Tooltip title="适应画布" placement="right">
          <button
            type="button"
            aria-label="适应画布"
            className={iconButtonClass()}
            onClick={() => void reactFlow.fitView({ padding: 0.18, maxZoom: 1, duration: 250 })}
          >
            <Maximize2 size={15} strokeWidth={1.9} />
          </button>
        </Tooltip>
      </div>

      <div className="pointer-events-auto absolute bottom-2 left-1/2 z-10 flex -translate-x-1/2 items-center rounded-lg border border-[#e4e7ec] bg-white p-0.5 shadow-[0_4px_14px_rgba(22,24,35,.08)]">
        <Tooltip title="撤销（Ctrl/Cmd + Z）">
          <button
            type="button"
            aria-label="撤销"
            disabled={locked || !canUndo}
            className={`${iconButtonClass()} ${disabledButtonClass}`}
            onClick={onUndo}
          >
            <Undo2 size={16} strokeWidth={1.9} />
          </button>
        </Tooltip>

        <Tooltip title="重做（Ctrl/Cmd + Shift + Z）">
          <button
            type="button"
            aria-label="重做"
            disabled={locked || !canRedo}
            className={`${iconButtonClass()} ${disabledButtonClass}`}
            onClick={onRedo}
          >
            <Redo2 size={16} strokeWidth={1.9} />
          </button>
        </Tooltip>

        <div className="mx-1 h-4 w-px bg-[#e4e7ec]" />

        <Popover
          open={historyOpen}
          onOpenChange={(open) => !locked && setHistoryOpen(open)}
          trigger="click"
          placement="top"
          arrow={false}
          content={historyContent}
          overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
        >
          <Tooltip title="变更历史">
            <button
              type="button"
              aria-label="变更历史"
              disabled={locked}
              className={`${iconButtonClass(historyOpen)} ${disabledButtonClass}`}
            >
              <History size={16} strokeWidth={1.9} />
            </button>
          </Tooltip>
        </Popover>
      </div>
    </>
  );
};

export default WorkflowCanvasTools;
