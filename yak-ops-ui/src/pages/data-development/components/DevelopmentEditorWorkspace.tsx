import { Button, Dropdown, Tooltip, message } from 'antd';
import {
  Check,
  Code2,
  MoreHorizontal,
  Play,
  RefreshCw,
  Rocket,
  Save,
  Share2,
  Square,
  TerminalSquare,
  X,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNode,
} from '../types';

interface DevelopmentEditorWorkspaceProps {
  nodes: DevelopmentNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
}

type RightPanelTab =
  | 'properties'
  | 'run-config'
  | 'schedule-config'
  | 'versions';

type EditorTabAction =
  | 'close-current'
  | 'close-others'
  | 'close-left'
  | 'close-right'
  | 'close-all';

const nodeTypeLabel: Record<string, string> = {
  SQL: 'SQL',
  SHELL: 'Shell',
  HTTP: 'HTTP',
  PYTHON: 'Python',
};

const rightPanelItems: Array<{ key: RightPanelTab; label: string }> = [
  { key: 'properties', label: '属性' },
  { key: 'run-config', label: '运行配置' },
  { key: 'schedule-config', label: '调度配置' },
  { key: 'versions', label: '版本' },
];

const DEFAULT_RIGHT_PANEL_WIDTH = 380;
const MIN_RIGHT_PANEL_WIDTH = 280;
const MAX_RIGHT_PANEL_WIDTH = 640;
const RIGHT_PANEL_WIDTH_STORAGE_KEY = 'yak-data-development.right-panel-width';

const DEFAULT_BOTTOM_PANEL_HEIGHT = 280;
const MIN_BOTTOM_PANEL_HEIGHT = 160;
const MAX_BOTTOM_PANEL_HEIGHT = 520;
const BOTTOM_PANEL_HEIGHT_STORAGE_KEY =
  'yak-data-development.bottom-panel-height';

const clampRightPanelWidth = (value: number) =>
  Math.min(MAX_RIGHT_PANEL_WIDTH, Math.max(MIN_RIGHT_PANEL_WIDTH, value));

const clampBottomPanelHeight = (value: number) =>
  Math.min(MAX_BOTTOM_PANEL_HEIGHT, Math.max(MIN_BOTTOM_PANEL_HEIGHT, value));

const initialRightPanelWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_RIGHT_PANEL_WIDTH;
  const stored = Number(
    window.localStorage.getItem(RIGHT_PANEL_WIDTH_STORAGE_KEY),
  );
  return Number.isFinite(stored) && stored > 0
    ? clampRightPanelWidth(stored)
    : DEFAULT_RIGHT_PANEL_WIDTH;
};

const initialBottomPanelHeight = () => {
  if (typeof window === 'undefined') return DEFAULT_BOTTOM_PANEL_HEIGHT;
  const stored = Number(
    window.localStorage.getItem(BOTTOM_PANEL_HEIGHT_STORAGE_KEY),
  );
  return Number.isFinite(stored) && stored > 0
    ? clampBottomPanelHeight(stored)
    : DEFAULT_BOTTOM_PANEL_HEIGHT;
};

const actionPlaceholder = (label: string) => {
  message.info(`${label}能力将在后续编辑器阶段接入`);
};

const NodeTypeIcon = ({ type, size = 14 }: { type: string; size?: number }) =>
  type === 'SHELL' ? (
    <TerminalSquare size={size} strokeWidth={1.8} />
  ) : (
    <Code2 size={size} strokeWidth={1.8} />
  );

const nodeIconClassName = (type: string) =>
  type === 'SHELL' ? 'text-[#6172f3]' : 'text-[#f79009]';

const DevelopmentEditorWorkspace = ({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
}: DevelopmentEditorWorkspaceProps) => {
  const [openNodeIds, setOpenNodeIds] = useState<DevelopmentId[]>([]);
  const [activeNodeId, setActiveNodeId] = useState<DevelopmentId>();
  const [rightPanelTab, setRightPanelTab] = useState<RightPanelTab>();
  const [rightPanelWidth, setRightPanelWidth] = useState(initialRightPanelWidth);
  const [rightPanelResizing, setRightPanelResizing] = useState(false);
  const [bottomPanelOpen, setBottomPanelOpen] = useState(false);
  const [bottomPanelHeight, setBottomPanelHeight] = useState(
    initialBottomPanelHeight,
  );
  const [bottomPanelResizing, setBottomPanelResizing] = useState(false);
  const tabRefs = useRef(new Map<DevelopmentId, HTMLDivElement>());

  const nodeMap = useMemo(
    () => new Map(nodes.map((node) => [node.id, node])),
    [nodes],
  );
  const directoryMap = useMemo(
    () => new Map(directories.map((directory) => [directory.id, directory])),
    [directories],
  );

  useEffect(() => {
    if (!selectedNodeId || !nodeMap.has(selectedNodeId)) return;
    setOpenNodeIds((current) =>
      current.includes(selectedNodeId) ? current : [...current, selectedNodeId],
    );
    setActiveNodeId(selectedNodeId);
  }, [nodeMap, selectedNodeId]);

  useEffect(() => {
    setOpenNodeIds((current) => current.filter((nodeId) => nodeMap.has(nodeId)));
    setActiveNodeId((current) =>
      current && nodeMap.has(current) ? current : undefined,
    );
  }, [nodeMap]);

  useEffect(() => {
    if (!activeNodeId) return;
    const frame = window.requestAnimationFrame(() => {
      tabRefs.current.get(activeNodeId)?.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
        inline: 'nearest',
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [activeNodeId]);

  const activeNode = activeNodeId ? nodeMap.get(activeNodeId) : undefined;
  const activeDirectory = activeNode?.directoryId
    ? directoryMap.get(activeNode.directoryId)
    : undefined;

  const focusNode = (nodeId: DevelopmentId) => {
    setActiveNodeId(nodeId);
    onNodeFocus(nodeId);
  };

  const closeNode = (nodeId: DevelopmentId) => {
    const currentIndex = openNodeIds.indexOf(nodeId);
    const next = openNodeIds.filter((id) => id !== nodeId);
    setOpenNodeIds(next);

    if (activeNodeId !== nodeId) return;
    const nextActiveId =
      next[Math.min(currentIndex, next.length - 1)] || next[next.length - 1];
    setActiveNodeId(nextActiveId);
    onNodeFocus(nextActiveId);
  };

  const closeAllNodes = () => {
    setOpenNodeIds([]);
    setActiveNodeId(undefined);
    onNodeFocus(undefined);
  };

  const handleTabAction = (action: EditorTabAction) => {
    if (!activeNodeId) return;
    const activeIndex = openNodeIds.indexOf(activeNodeId);
    if (activeIndex < 0) return;

    if (action === 'close-current') {
      closeNode(activeNodeId);
      return;
    }
    if (action === 'close-all') {
      closeAllNodes();
      return;
    }
    if (action === 'close-others') {
      setOpenNodeIds([activeNodeId]);
      return;
    }
    if (action === 'close-left') {
      setOpenNodeIds(openNodeIds.slice(activeIndex));
      return;
    }
    if (action === 'close-right') {
      setOpenNodeIds(openNodeIds.slice(0, activeIndex + 1));
    }
  };

  const toggleRightPanel = (tab: RightPanelTab) => {
    setRightPanelTab((current) => (current === tab ? undefined : tab));
  };

  const handleRightPanelResizeStart = (
    event: ReactPointerEvent<HTMLDivElement>,
  ) => {
    if (!rightPanelTab) return;
    event.preventDefault();

    const startX = event.clientX;
    const startWidth = rightPanelWidth;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    setRightPanelResizing(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setRightPanelWidth(
        clampRightPanelWidth(startWidth + startX - moveEvent.clientX),
      );
    };

    const finish = (upEvent: PointerEvent) => {
      const width = clampRightPanelWidth(
        startWidth + startX - upEvent.clientX,
      );
      setRightPanelWidth(width);
      setRightPanelResizing(false);
      window.localStorage.setItem(RIGHT_PANEL_WIDTH_STORAGE_KEY, String(width));
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

  const handleBottomPanelResizeStart = (
    event: ReactPointerEvent<HTMLDivElement>,
  ) => {
    if (!bottomPanelOpen) return;
    event.preventDefault();

    const startY = event.clientY;
    const startHeight = bottomPanelHeight;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    setBottomPanelResizing(true);
    document.body.style.cursor = 'row-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setBottomPanelHeight(
        clampBottomPanelHeight(startHeight + startY - moveEvent.clientY),
      );
    };

    const finish = (upEvent: PointerEvent) => {
      const height = clampBottomPanelHeight(
        startHeight + startY - upEvent.clientY,
      );
      setBottomPanelHeight(height);
      setBottomPanelResizing(false);
      window.localStorage.setItem(
        BOTTOM_PANEL_HEIGHT_STORAGE_KEY,
        String(height),
      );
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

  const editorMenuItems = useMemo(
    () => [
      {
        key: 'opened-editors',
        label: `已打开的编辑器（${openNodeIds.length}）`,
        children: openNodeIds.map((nodeId) => {
          const node = nodeMap.get(nodeId);
          const active = nodeId === activeNodeId;
          return {
            key: `focus:${nodeId}`,
            icon: node ? (
              <span className={nodeIconClassName(node.type)}>
                <NodeTypeIcon type={node.type} size={13} />
              </span>
            ) : undefined,
            label: (
              <div className="flex min-w-[190px] items-center justify-between gap-3">
                <span className="max-w-[220px] truncate">
                  {node?.name || nodeId}
                </span>
                {active ? (
                  <Check size={13} className="shrink-0 text-[#667085]" />
                ) : null}
              </div>
            ),
          };
        }),
      },
      { type: 'divider' as const },
      { key: 'close-current', label: '关闭当前编辑器' },
      {
        key: 'close-others',
        label: '关闭其他编辑器',
        disabled: openNodeIds.length <= 1,
      },
      {
        key: 'close-left',
        label: '关闭左侧编辑器',
        disabled: !activeNodeId || openNodeIds.indexOf(activeNodeId) <= 0,
      },
      {
        key: 'close-right',
        label: '关闭右侧编辑器',
        disabled:
          !activeNodeId ||
          openNodeIds.indexOf(activeNodeId) >= openNodeIds.length - 1,
      },
      { key: 'close-all', label: '全部关闭' },
    ],
    [activeNodeId, nodeMap, openNodeIds],
  );

  const renderRightPanelContent = () => {
    if (!rightPanelTab || !activeNode) return null;

    if (rightPanelTab === 'properties') {
      return (
        <dl className="m-0 grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-4 text-[12px] leading-5">
          <dt className="text-[#667085]">名称：</dt>
          <dd className="m-0 break-all text-[#344054]">{activeNode.name}</dd>

          <dt className="text-[#667085]">类型：</dt>
          <dd className="m-0 text-[#344054]">
            {nodeTypeLabel[activeNode.type] || activeNode.type}
          </dd>

          <dt className="text-[#667085]">ID：</dt>
          <dd className="m-0 break-all font-mono text-[11px] text-[#98a2b3]">
            {activeNode.id}
          </dd>

          <dt className="text-[#667085]">所属目录：</dt>
          <dd className="m-0 break-all text-[#344054]">
            {activeDirectory?.path || '/'}
          </dd>

          <dt className="text-[#667085]">配置状态：</dt>
          <dd className="m-0 text-[#344054]">
            {activeNode.configured ? '已配置' : '待配置'}
          </dd>
        </dl>
      );
    }

    if (rightPanelTab === 'run-config') {
      return (
        <div className="text-[12px] leading-6 text-[#667085]">
          <div className="font-medium text-[#344054]">运行配置</div>
          <div className="mt-2">
            运行参数、资源配置和执行环境将在后续编辑器阶段接入。
          </div>
        </div>
      );
    }

    if (rightPanelTab === 'schedule-config') {
      return (
        <div className="text-[12px] leading-6 text-[#667085]">
          <div className="font-medium text-[#344054]">调度配置</div>
          <div className="mt-2">
            调度周期、依赖关系和生效时间将在后续阶段接入。
          </div>
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

  if (!openNodeIds.length || !activeNode) {
    return (
      <main className="flex min-w-0 flex-1 items-center justify-center overflow-hidden bg-white">
        <div className="text-center">
          <div className="text-[14px] font-medium text-[#667085]">
            选择左侧开发节点
          </div>
          <div className="mt-1 text-[12px] text-[#98a2b3]">
            点击 SQL 或 Shell 节点后进入编辑工作区
          </div>
        </div>
      </main>
    );
  }

  const activeRightPanel = rightPanelItems.find(
    (item) => item.key === rightPanelTab,
  );

  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-9 shrink-0 border-b border-[#e5e7eb] bg-[#f5f5f6]">
        <div className="min-w-0 flex-1 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <div className="flex h-9 min-w-max items-stretch gap-1 px-1">
            {openNodeIds.map((nodeId) => {
              const node = nodeMap.get(nodeId);
              if (!node) return null;
              const active = nodeId === activeNodeId;

              return (
                <div
                  key={nodeId}
                  ref={(element) => {
                    if (element) tabRefs.current.set(nodeId, element);
                    else tabRefs.current.delete(nodeId);
                  }}
                  onAuxClick={(event) => {
                    if (event.button === 1) closeNode(nodeId);
                  }}
                  className={[
                    'group relative flex h-9 min-w-[132px] max-w-[240px] flex-none items-center border-r border-[#eaecf0] transition-colors',
                    active
                      ? 'bg-white text-[#344054] shadow-[inset_0_-2px_0_rgba(254,44,85,1)]'
                      : 'bg-[#f5f5f6] text-[#667085] hover:bg-[#eeeeef] hover:text-[#344054]',
                  ].join(' ')}
                >
                  <button
                    type="button"
                    title={node.name}
                    aria-current={active ? 'page' : undefined}
                    onClick={() => focusNode(nodeId)}
                    className="flex h-full min-w-0 flex-1 items-center gap-2 bg-transparent pl-2.5 pr-1 text-left outline-none"
                  >
                    <span
                      className={[
                        'flex h-5 w-5 shrink-0 items-center justify-center rounded-sm bg-white/80',
                        nodeIconClassName(node.type),
                      ].join(' ')}
                    >
                      <NodeTypeIcon type={node.type} size={13} />
                    </span>
                    <span
                      className={[
                        'min-w-0 flex-1 truncate text-[12px] leading-5',
                        active
                          ? 'font-medium text-[#344054]'
                          : 'font-normal',
                      ].join(' ')}
                    >
                      {node.name}
                    </span>
                  </button>

                  <button
                    type="button"
                    aria-label={`关闭 ${node.name}`}
                    title="关闭"
                    onClick={() => closeNode(nodeId)}
                    className={[
                      'mr-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-[3px] text-[#98a2b3] transition-all',
                      active
                        ? 'opacity-100 hover:bg-[#f2f4f7] hover:text-[#475467]'
                        : 'opacity-0 group-hover:opacity-100 hover:bg-[#e4e7ec] hover:text-[#475467]',
                    ].join(' ')}
                  >
                    <X size={13} strokeWidth={1.8} />
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        <div className="flex h-9 w-10 shrink-0 items-center justify-center border-l border-[#e5e7eb] bg-[#f5f5f6]">
          <Dropdown
            trigger={['click']}
            placement="bottomRight"
            menu={{
              items: editorMenuItems,
              onClick: ({ key }) => {
                if (key.startsWith('focus:')) {
                  focusNode(key.substring('focus:'.length));
                  return;
                }
                handleTabAction(key as EditorTabAction);
              },
            }}
          >
            <Tooltip title="编辑器操作" placement="bottomRight">
              <button
                type="button"
                aria-label="编辑器操作"
                className="flex h-7 w-7 items-center justify-center rounded-[4px] text-[#667085] transition-colors hover:bg-white hover:text-[#344054]"
              >
                <MoreHorizontal size={17} strokeWidth={1.8} />
              </button>
            </Tooltip>
          </Dropdown>
        </div>
      </div>

      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-3">
        <div className="flex items-center gap-1">
          <Button
            type="text"
            size="small"
            icon={<Play size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => setBottomPanelOpen(true)}
          >
            运行
          </Button>
          <Button
            type="text"
            size="small"
            icon={<Square size={13} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => actionPlaceholder('停止')}
          >
            停止
          </Button>
          <Button
            type="text"
            size="small"
            icon={<Save size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => actionPlaceholder('保存')}
          >
            保存
          </Button>
          <Button
            type="text"
            size="small"
            icon={<RefreshCw size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => actionPlaceholder('刷新')}
          >
            刷新
          </Button>
          <Button
            type="text"
            size="small"
            icon={<Rocket size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => actionPlaceholder('发布')}
          >
            发布
          </Button>
          <Button
            type="text"
            size="small"
            icon={<Share2 size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => actionPlaceholder('分享')}
          >
            分享
          </Button>
        </div>

        <div className="truncate pl-4 text-[11px] text-[#98a2b3]">
          {activeDirectory?.path || '/'} / {activeNode.name}
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-0 flex-1 overflow-hidden">
          <section className="flex min-w-0 flex-1 items-center justify-center overflow-auto bg-white">
            <div className="text-center">
              <div className="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-[#f5f5f6] text-[#667085]">
                <NodeTypeIcon type={activeNode.type} size={18} />
              </div>
              <div className="mt-3 text-[15px] font-semibold text-[#344054]">
                {nodeTypeLabel[activeNode.type] || activeNode.type} 编辑器区域
              </div>
              <div className="mt-1 text-[12px] text-[#98a2b3]">
                当前节点：{activeNode.name}
              </div>
              <div className="mt-3 text-[12px] text-[#b0b7c3]">
                编辑器内容将在下一阶段接入
              </div>
            </div>
          </section>

          <aside className="flex shrink-0 bg-white">
            <div
              className={[
                'relative h-full shrink-0',
                rightPanelResizing
                  ? 'transition-none'
                  : 'transition-[width] duration-200 ease-out',
              ].join(' ')}
              style={{ width: rightPanelTab ? rightPanelWidth : 0 }}
            >
              {rightPanelTab ? (
                <div
                  role="separator"
                  aria-label="调整右侧面板宽度"
                  aria-orientation="vertical"
                  onPointerDown={handleRightPanelResizeStart}
                  className="group absolute inset-y-0 left-0 z-30 w-3 -translate-x-1/2 cursor-col-resize touch-none"
                >
                  <div
                    className={[
                      'pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#e5e7eb]',
                      'transition-[width,background-color] duration-150',
                      'group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)]',
                      'group-active:w-[2px] group-active:bg-[rgba(254,44,85,1)]',
                    ].join(' ')}
                  />
                </div>
              ) : null}

              <div className="h-full overflow-hidden">
                <div
                  className="flex h-full flex-col bg-white"
                  style={{ width: rightPanelWidth }}
                >
                  <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
                    <span className="text-[13px] font-semibold text-[#30323b]">
                      {activeRightPanel?.label}
                    </span>

                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        title="刷新"
                        aria-label={`刷新${activeRightPanel?.label || '侧边栏'}`}
                        onClick={() =>
                          actionPlaceholder(
                            `${activeRightPanel?.label || ''}刷新`,
                          )
                        }
                        className="flex h-7 items-center gap-1 rounded-[3px] px-2 text-[11px] text-[#475467] transition-colors hover:bg-[#f5f5f6]"
                      >
                        <RefreshCw size={13} strokeWidth={1.8} />
                        刷新
                      </button>
                      <button
                        type="button"
                        title="关闭"
                        aria-label="关闭右侧面板"
                        onClick={() => setRightPanelTab(undefined)}
                        className="flex h-7 w-7 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
                      >
                        <X size={14} strokeWidth={1.8} />
                      </button>
                    </div>
                  </div>

                  <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5">
                    {renderRightPanelContent()}
                  </div>
                </div>
              </div>
            </div>

            <div className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
              {rightPanelItems.map((item, index) => {
                const active = rightPanelTab === item.key;

                return (
                  <button
                    key={item.key}
                    type="button"
                    title={item.label}
                    aria-label={`${active ? '收起' : '展开'}${item.label}`}
                    aria-expanded={active}
                    onClick={() => toggleRightPanel(item.key)}
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
        </div>

        <div
          className={[
            'relative shrink-0 bg-white',
            bottomPanelResizing
              ? 'transition-none'
              : 'transition-[height] duration-200 ease-out',
          ].join(' ')}
          style={{ height: bottomPanelOpen ? bottomPanelHeight : 0 }}
        >
          {bottomPanelOpen ? (
            <>
              <div
                role="separator"
                aria-label="调整运行结果面板高度"
                aria-orientation="horizontal"
                onPointerDown={handleBottomPanelResizeStart}
                className="group absolute inset-x-0 top-0 z-40 h-3 -translate-y-1/2 cursor-row-resize touch-none"
              >
                <div
                  className={[
                    'pointer-events-none absolute inset-x-0 top-1/2 h-px -translate-y-1/2 bg-[#e5e7eb]',
                    'transition-[height,background-color] duration-150',
                    'group-hover:h-[2px] group-hover:bg-[rgba(254,44,85,.55)]',
                    'group-active:h-[2px] group-active:bg-[rgba(254,44,85,1)]',
                  ].join(' ')}
                />
              </div>

              <div className="flex h-full flex-col overflow-hidden bg-white">
                <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="shrink-0 text-[12px] font-medium text-[#344054]">
                      运行结果
                    </span>
                    <span className="truncate text-[11px] text-[#98a2b3]">
                      当前节点：{activeNode.name}
                    </span>
                  </div>

                  <button
                    type="button"
                    title="关闭"
                    aria-label="关闭运行结果面板"
                    onClick={() => setBottomPanelOpen(false)}
                    className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
                  >
                    <X size={14} strokeWidth={1.8} />
                  </button>
                </div>

                <div className="flex min-h-0 flex-1 items-center justify-center overflow-auto bg-white">
                  <div className="text-center">
                    <div className="text-[13px] font-medium text-[#475467]">
                      运行结果区域
                    </div>
                    <div className="mt-1 text-[11px] text-[#98a2b3]">
                      执行日志和结果内容将在后续阶段接入
                    </div>
                  </div>
                </div>
              </div>
            </>
          ) : null}
        </div>
      </div>
    </main>
  );
};

export default DevelopmentEditorWorkspace;
