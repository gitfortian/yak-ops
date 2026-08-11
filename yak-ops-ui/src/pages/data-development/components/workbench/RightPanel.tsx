import { message } from 'antd';
import { RefreshCw, X } from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useMemo, useState } from 'react';

import type {
  DevelopmentEditorDefinition,
  DevelopmentEditorPanelKey,
} from '../../editors/types';
import type { DevelopmentDirectory, DevelopmentNode } from '../../types';

interface RightPanelProps {
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
}

const DEFAULT_WIDTH = 380;
const MIN_WIDTH = 280;
const MAX_WIDTH = 640;
const WIDTH_STORAGE_KEY = 'yak-data-development.right-panel-width';

const clampWidth = (value: number) =>
  Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, value));

const initialWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_WIDTH;
  const stored = Number(window.localStorage.getItem(WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampWidth(stored)
    : DEFAULT_WIDTH;
};

const allItems: Array<{
  key: DevelopmentEditorPanelKey;
  label: string;
  capability: keyof DevelopmentEditorDefinition['capabilities'];
}> = [
  { key: 'properties', label: '属性', capability: 'properties' },
  { key: 'run-config', label: '运行配置', capability: 'runConfig' },
  { key: 'schedule-config', label: '调度配置', capability: 'scheduleConfig' },
  { key: 'versions', label: '版本', capability: 'versions' },
];

const RightPanel = ({ node, directory, definition }: RightPanelProps) => {
  const [activeTab, setActiveTab] = useState<DevelopmentEditorPanelKey>();
  const [width, setWidth] = useState(initialWidth);
  const [resizing, setResizing] = useState(false);

  const items = useMemo(
    () =>
      allItems.filter((item) => Boolean(definition.capabilities[item.capability])),
    [definition],
  );
  const activeItem = items.find((item) => item.key === activeTab);

  const handleResizeStart = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!activeTab) return;
    event.preventDefault();

    const startX = event.clientX;
    const startWidth = width;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    setResizing(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setWidth(clampWidth(startWidth + startX - moveEvent.clientX));
    };

    const finish = (upEvent: PointerEvent) => {
      const nextWidth = clampWidth(startWidth + startX - upEvent.clientX);
      setWidth(nextWidth);
      setResizing(false);
      window.localStorage.setItem(WIDTH_STORAGE_KEY, String(nextWidth));
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

  const renderContent = () => {
    if (!activeTab) return null;

    const CustomPanel = definition.panels?.[activeTab];
    if (CustomPanel) {
      return <CustomPanel node={node} directory={directory} />;
    }

    if (activeTab === 'properties') {
      return (
        <dl className="m-0 grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-4 text-[12px] leading-5">
          <dt className="text-[#667085]">名称：</dt>
          <dd className="m-0 break-all text-[#344054]">{node.name}</dd>
          <dt className="text-[#667085]">类型：</dt>
          <dd className="m-0 text-[#344054]">{definition.label}</dd>
          <dt className="text-[#667085]">ID：</dt>
          <dd className="m-0 break-all font-mono text-[11px] text-[#98a2b3]">
            {node.id}
          </dd>
          <dt className="text-[#667085]">所属目录：</dt>
          <dd className="m-0 break-all text-[#344054]">
            {directory?.path || '/'}
          </dd>
          <dt className="text-[#667085]">配置状态：</dt>
          <dd className="m-0 text-[#344054]">
            {node.configured ? '已配置' : '待配置'}
          </dd>
        </dl>
      );
    }

    if (activeTab === 'run-config') {
      return (
        <div className="text-[12px] leading-6 text-[#667085]">
          <div className="font-medium text-[#344054]">运行配置</div>
          <div className="mt-2">运行参数和执行环境将在后续阶段接入。</div>
        </div>
      );
    }

    if (activeTab === 'schedule-config') {
      return (
        <div className="text-[12px] leading-6 text-[#667085]">
          <div className="font-medium text-[#344054]">调度配置</div>
          <div className="mt-2">调度周期、依赖关系和生效时间将在后续阶段接入。</div>
        </div>
      );
    }

    return (
      <div className="border-b border-[#f0f1f3] pb-3 text-[12px]">
        <div className="flex items-center justify-between gap-3">
          <span className="font-medium text-[#475467]">当前草稿</span>
          <span className="text-[10px] text-[#98a2b3]">v1</span>
        </div>
        <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
          版本管理能力将在后续阶段接入
        </div>
      </div>
    );
  };

  return (
    <aside className="flex shrink-0 bg-white">
      <div
        className={[
          'relative h-full shrink-0',
          resizing ? 'transition-none' : 'transition-[width] duration-200 ease-out',
        ].join(' ')}
        style={{ width: activeTab ? width : 0 }}
      >
        {activeTab ? (
          <div
            role="separator"
            aria-label="调整右侧面板宽度"
            aria-orientation="vertical"
            onPointerDown={handleResizeStart}
            className="group absolute inset-y-0 left-0 z-30 w-3 -translate-x-1/2 cursor-col-resize touch-none"
          >
            <div className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#e5e7eb] transition-[width,background-color] duration-150 group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:w-[2px] group-active:bg-[rgba(254,44,85,1)]" />
          </div>
        ) : null}

        <div className="h-full overflow-hidden">
          <div className="flex h-full flex-col bg-white" style={{ width }}>
            <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
              <span className="text-[13px] font-semibold text-[#30323b]">
                {activeItem?.label}
              </span>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  title="刷新"
                  onClick={() => message.info(`${activeItem?.label || ''}刷新能力将在后续阶段接入`)}
                  className="flex h-7 items-center gap-1 rounded-[3px] px-2 text-[11px] text-[#475467] transition-colors hover:bg-[#f5f5f6]"
                >
                  <RefreshCw size={13} strokeWidth={1.8} />
                  刷新
                </button>
                <button
                  type="button"
                  title="关闭"
                  aria-label="关闭右侧面板"
                  onClick={() => setActiveTab(undefined)}
                  className="flex h-7 w-7 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
                >
                  <X size={14} strokeWidth={1.8} />
                </button>
              </div>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5">
              {renderContent()}
            </div>
          </div>
        </div>
      </div>

      <div className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
        {items.map((item, index) => {
          const active = activeTab === item.key;
          return (
            <button
              key={item.key}
              type="button"
              title={item.label}
              aria-label={`${active ? '收起' : '展开'}${item.label}`}
              aria-expanded={active}
              onClick={() => setActiveTab((current) => (current === item.key ? undefined : item.key))}
              className={[
                'relative flex min-h-[72px] w-9 shrink-0 items-center justify-center border-b border-[#e5e7eb] py-3 text-[12px] leading-5 transition-[color,background-color,opacity]',
                '[writing-mode:vertical-rl] [letter-spacing:3px]',
                index === 0 ? 'border-t' : '',
                active
                  ? 'text-[#245bdb] opacity-100 before:absolute before:inset-y-0 before:left-0 before:w-px before:bg-[#245bdb]'
                  : 'text-[#475467] opacity-70 hover:bg-[#f7f8fa] hover:text-[#344054] hover:opacity-100',
              ].join(' ')}
            >
              {item.label}
            </button>
          );
        })}
      </div>
    </aside>
  );
};

export default RightPanel;
