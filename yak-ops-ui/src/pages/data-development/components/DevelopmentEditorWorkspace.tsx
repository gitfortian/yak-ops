import { Button, Dropdown, Tooltip, message } from 'antd';
import {
  Check,
  Code2,
  History,
  MoreHorizontal,
  Play,
  RefreshCw,
  Rocket,
  Save,
  Settings2,
  Share2,
  Square,
  TerminalSquare,
  X,
} from 'lucide-react';
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

type RightPanelTab = 'properties' | 'versions';
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
  const [rightPanelTab, setRightPanelTab] =
    useState<RightPanelTab>('properties');
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
                <span className="max-w-[220px] truncate">{node?.name || nodeId}</span>
                {active ? <Check size={13} className="shrink-0 text-[#667085]" /> : null}
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
                        active ? 'font-medium text-[#344054]' : 'font-normal',
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
            onClick={() => actionPlaceholder('运行')}
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

        <aside className="flex shrink-0 border-l border-[#e5e7eb] bg-white">
          <div className="w-[268px] overflow-y-auto px-4 py-4">
            {rightPanelTab === 'properties' ? (
              <div>
                <div className="mb-4 flex items-center gap-2 text-[13px] font-semibold text-[#344054]">
                  <Settings2 size={14} strokeWidth={1.8} />
                  节点属性
                </div>
                <dl className="m-0 space-y-3 text-[12px]">
                  <div>
                    <dt className="text-[#98a2b3]">节点名称</dt>
                    <dd className="m-0 mt-1 break-all text-[#475467]">
                      {activeNode.name}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[#98a2b3]">节点类型</dt>
                    <dd className="m-0 mt-1 text-[#475467]">
                      {nodeTypeLabel[activeNode.type] || activeNode.type}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[#98a2b3]">节点 ID</dt>
                    <dd className="m-0 mt-1 break-all font-mono text-[11px] text-[#667085]">
                      {activeNode.id}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[#98a2b3]">所属目录</dt>
                    <dd className="m-0 mt-1 break-all text-[#475467]">
                      {activeDirectory?.path || '/'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-[#98a2b3]">配置状态</dt>
                    <dd className="m-0 mt-1 text-[#475467]">
                      {activeNode.configured ? '已配置' : '待配置'}
                    </dd>
                  </div>
                </dl>
              </div>
            ) : (
              <div>
                <div className="mb-4 flex items-center gap-2 text-[13px] font-semibold text-[#344054]">
                  <History size={14} strokeWidth={1.8} />
                  版本记录
                </div>
                <div className="border-b border-[#f0f1f3] py-3 first:pt-0">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-[12px] font-medium text-[#475467]">
                      当前草稿
                    </span>
                    <span className="text-[10px] text-[#98a2b3]">v1</span>
                  </div>
                  <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
                    版本管理能力将在后续阶段接入
                  </div>
                </div>
              </div>
            )}
          </div>

          <div className="flex w-10 shrink-0 flex-col items-center border-l border-[#e5e7eb] bg-[#fafafa] py-2">
            <Tooltip title="属性" placement="left">
              <button
                type="button"
                onClick={() => setRightPanelTab('properties')}
                className={[
                  'mb-1 flex min-h-[58px] w-8 items-center justify-center rounded-sm px-1 text-[11px] transition-colors',
                  rightPanelTab === 'properties'
                    ? 'bg-white font-medium text-[#344054] shadow-[0_1px_3px_rgba(16,24,40,0.08)]'
                    : 'text-[#98a2b3] hover:bg-white hover:text-[#667085]',
                ].join(' ')}
                style={{ writingMode: 'vertical-rl' }}
              >
                属性
              </button>
            </Tooltip>
            <Tooltip title="版本" placement="left">
              <button
                type="button"
                onClick={() => setRightPanelTab('versions')}
                className={[
                  'flex min-h-[58px] w-8 items-center justify-center rounded-sm px-1 text-[11px] transition-colors',
                  rightPanelTab === 'versions'
                    ? 'bg-white font-medium text-[#344054] shadow-[0_1px_3px_rgba(16,24,40,0.08)]'
                    : 'text-[#98a2b3] hover:bg-white hover:text-[#667085]',
                ].join(' ')}
                style={{ writingMode: 'vertical-rl' }}
              >
                版本
              </button>
            </Tooltip>
          </div>
        </aside>
      </div>
    </main>
  );
};

export default DevelopmentEditorWorkspace;
