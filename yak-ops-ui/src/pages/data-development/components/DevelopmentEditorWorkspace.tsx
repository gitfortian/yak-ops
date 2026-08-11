import { Button, Tooltip, message } from 'antd';
import {
  Code2,
  History,
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
import { useEffect, useMemo, useState } from 'react';

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
      <div className="flex h-10 shrink-0 items-end overflow-x-auto border-b border-[#e5e7eb] bg-[#fafafa] px-1.5 pt-1">
        {openNodeIds.map((nodeId) => {
          const node = nodeMap.get(nodeId);
          if (!node) return null;
          const active = nodeId === activeNodeId;

          return (
            <div
              key={nodeId}
              className={[
                'group flex h-9 min-w-[150px] max-w-[230px] items-center border-x border-t transition-colors',
                active
                  ? 'relative -mb-px border-[#dfe3e8] bg-white text-[#161823]'
                  : 'border-transparent bg-transparent text-[#667085] hover:bg-[#f5f5f5] hover:text-[#344054]',
              ].join(' ')}
            >
              <button
                type="button"
                onClick={() => focusNode(nodeId)}
                className="flex h-full min-w-0 flex-1 items-center gap-2 bg-transparent px-3 text-left"
              >
                <span className="shrink-0 text-[#98a2b3]">
                  <NodeTypeIcon type={node.type} size={13} />
                </span>
                <span className="min-w-0 flex-1 truncate text-[12px] font-medium">
                  {node.name}
                </span>
              </button>
              <button
                type="button"
                aria-label={`关闭 ${node.name}`}
                onClick={() => closeNode(nodeId)}
                className="mr-1 flex h-5 w-5 shrink-0 items-center justify-center rounded-sm text-[#98a2b3] opacity-0 transition group-hover:opacity-100 hover:bg-[#eceff3] hover:text-[#475467]"
              >
                <X size={12} strokeWidth={1.8} />
              </button>
            </div>
          );
        })}
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
