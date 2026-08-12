import { LoaderCircle, X } from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useState } from 'react';

import type { DevelopmentEditorDefinition } from '../../editors/types';
import type {
  DevelopmentDirectory,
  DevelopmentNode,
  DevelopmentTaskRunResult,
} from '../../types';

interface RunResultPanelProps {
  open: boolean;
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
  result?: DevelopmentTaskRunResult;
  onClose: () => void;
}

const DEFAULT_HEIGHT = 280;
const MIN_HEIGHT = 160;
const MAX_HEIGHT = 520;
const HEIGHT_STORAGE_KEY = 'yak-data-development.bottom-panel-height';

const clampHeight = (value: number) =>
  Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, value));

const initialHeight = () => {
  if (typeof window === 'undefined') return DEFAULT_HEIGHT;
  const stored = Number(window.localStorage.getItem(HEIGHT_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampHeight(stored)
    : DEFAULT_HEIGHT;
};

const statusText = (result?: DevelopmentTaskRunResult) => {
  if (!result) return undefined;
  if (result.status === 'RUNNING') return '运行中';
  if (result.status === 'SUCCESS') return `完成 · ${result.durationMs} ms`;
  if (result.status === 'CANCELLED') return '已取消';
  if (result.status === 'TIMEOUT') return `超时 · ${result.durationMs} ms`;
  if (result.status === 'FAILED') return `失败 · ${result.durationMs} ms`;
  return result.status;
};

const RunResultPanel = ({
  open,
  node,
  directory,
  definition,
  result,
  onClose,
}: RunResultPanelProps) => {
  const [height, setHeight] = useState(initialHeight);
  const [resizing, setResizing] = useState(false);

  const handleResizeStart = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!open) return;
    event.preventDefault();

    const startY = event.clientY;
    const startHeight = height;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    setResizing(true);
    document.body.style.cursor = 'row-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setHeight(clampHeight(startHeight + startY - moveEvent.clientY));
    };

    const finish = (upEvent: PointerEvent) => {
      const nextHeight = clampHeight(startHeight + startY - upEvent.clientY);
      setHeight(nextHeight);
      setResizing(false);
      window.localStorage.setItem(HEIGHT_STORAGE_KEY, String(nextHeight));
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', resize);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };

    window.addEventListener('pointermove', resize);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
  };

  const Result = definition.RunResult;

  return (
    <div
      className={[
        'relative shrink-0 bg-white',
        resizing ? 'transition-none' : 'transition-[height] duration-200 ease-out',
      ].join(' ')}
      style={{ height: open ? height : 0 }}
    >
      {open ? (
        <>
          <div
            role="separator"
            aria-label="调整运行结果面板高度"
            aria-orientation="horizontal"
            onPointerDown={handleResizeStart}
            className="group absolute inset-x-0 top-0 z-40 h-3 -translate-y-1/2 cursor-row-resize touch-none"
          >
            <div className="pointer-events-none absolute inset-x-0 top-1/2 h-px -translate-y-1/2 bg-[#e5e7eb] transition-[height,background-color] duration-150 group-hover:h-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:h-[2px] group-active:bg-[rgba(254,44,85,1)]" />
          </div>

          <div className="flex h-full flex-col overflow-hidden bg-white">
            <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-3">
              <div className="flex min-w-0 items-center gap-3">
                <span className="shrink-0 text-[12px] font-medium text-[#344054]">
                  运行结果
                </span>
                <span className="truncate text-[11px] text-[#98a2b3]">
                  当前节点：{node.name}
                </span>
                {result?.status === 'RUNNING' ? (
                  <span className="inline-flex shrink-0 items-center gap-1 text-[11px] text-[#667085]">
                    <LoaderCircle size={12} className="animate-spin" />
                    运行中
                  </span>
                ) : statusText(result) ? (
                  <span className="shrink-0 text-[11px] text-[#667085]">
                    {statusText(result)}
                  </span>
                ) : null}
              </div>
              <button
                type="button"
                title="关闭"
                aria-label="关闭运行结果面板"
                onClick={onClose}
                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
              >
                <X size={14} strokeWidth={1.8} />
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-hidden bg-white">
              {Result ? (
                <Result node={node} directory={directory} result={result} />
              ) : (
                <div className="flex h-full items-center justify-center text-center">
                  <div>
                    <div className="text-[13px] font-medium text-[#475467]">
                      运行结果区域
                    </div>
                    <div className="mt-1 text-[11px] text-[#98a2b3]">
                      当前节点类型暂未接入执行结果渲染
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
};

export default RunResultPanel;
