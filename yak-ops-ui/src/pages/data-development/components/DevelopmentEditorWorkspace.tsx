import { Button } from 'antd';
import { Boxes, Database, FileCode2, Plus, RefreshCw, Sparkles } from 'lucide-react';
import { Component, type ReactNode, useEffect, useMemo, useState } from 'react';

import { isDevelopmentTaskNode } from '../node-model';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentResourceNode,
} from '../types';
import DevelopmentWorkbench from './workbench/DevelopmentWorkbench';

interface DevelopmentEditorWorkspaceProps {
  nodes: DevelopmentResourceNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
  onNodesChanged?: () => void | Promise<void>;
}

interface ResourceEditorBoundaryProps {
  resourceKey: DevelopmentId;
  children: ReactNode;
}

interface ResourceEditorBoundaryState {
  error?: string;
}

class ResourceEditorBoundary extends Component<ResourceEditorBoundaryProps, ResourceEditorBoundaryState> {
  state: ResourceEditorBoundaryState = {};

  static getDerivedStateFromError(error: unknown): ResourceEditorBoundaryState {
    return { error: error instanceof Error ? error.message : '资源编辑器发生未知错误' };
  }

  componentDidUpdate(previous: ResourceEditorBoundaryProps) {
    if (previous.resourceKey !== this.props.resourceKey && this.state.error) {
      this.setState({ error: undefined });
    }
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white px-6">
        <div className="max-w-[520px] text-center">
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-[#f5f5f6] text-[#98a2b3]"><Boxes size={18} /></div>
          <div className="mt-3 text-[14px] font-semibold text-[#344054]">资源编辑器加载异常</div>
          <div className="mt-1 text-[12px] leading-5 text-[#98a2b3]">{this.state.error}</div>
          <Button className="mt-4" size="small" icon={<RefreshCw size={13} />} onClick={() => this.setState({ error: undefined })}>重新渲染</Button>
        </div>
      </div>
    );
  }
}

function EmptyDevelopmentWorkspace() {
  return (
    <main className="flex min-w-0 flex-1 items-center justify-center overflow-hidden bg-white">
      <div className="w-[430px] text-center">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-[#f7f3ff] text-[#8b5cf6]">
          <Sparkles size={22} />
        </div>
        <h2 className="mt-5 text-[18px] font-semibold text-[#161823]">开始你的数据开发</h2>
        <p className="mt-2 text-[13px] leading-6 text-[#667085]">
          SQL、Python、Shell、数据集都可以在统一工作台中创建、编辑和调试。
        </p>

        <div className="mt-6 flex justify-center gap-3">
          <button className="flex h-9 items-center gap-2 rounded-lg bg-[#fe2c55] px-4 text-[13px] font-medium text-white hover:opacity-90">
            <Plus size={15} /> 创建 SQL
          </button>
          <button className="flex h-9 items-center gap-2 rounded-lg border border-[#e4e7ec] px-4 text-[13px] text-[#344054] hover:bg-[#f9fafb]">
            <Database size={15} /> 创建数据集
          </button>
        </div>

        <div className="mt-8 grid grid-cols-3 gap-3 text-left">
          {[
            ['SQL 查询', '编写 SQL 并执行', FileCode2],
            ['Python 脚本', '处理数据任务', Sparkles],
            ['数据集', '构建数据模型', Database],
          ].map(([title, desc, Icon]) => (
            <div key={String(title)} className="rounded-xl border border-[#eaecf0] p-3 hover:border-[#d0d5dd]">
              <Icon size={16} className="text-[#667085]" />
              <div className="mt-2 text-[12px] font-medium text-[#344054]">{title}</div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">{desc}</div>
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}

export default function DevelopmentEditorWorkspace({ nodes, directories, selectedNodeId, onNodeFocus, onNodesChanged }: DevelopmentEditorWorkspaceProps) {
  const [focusedNodeId, setFocusedNodeId] = useState<DevelopmentId | undefined>(selectedNodeId);

  useEffect(() => {
    if (selectedNodeId && nodes.some((node) => node.id === selectedNodeId)) setFocusedNodeId(selectedNodeId);
  }, [nodes, selectedNodeId]);

  const effectiveNodeId = selectedNodeId && nodes.some((node) => node.id === selectedNodeId) ? selectedNodeId : focusedNodeId;
  const selectedResource = useMemo(() => nodes.find((node) => node.id === effectiveNodeId), [effectiveNodeId, nodes]);
  const workbenchNodes = useMemo(() => nodes.filter((node) => isDevelopmentTaskNode(node) || node.type === 'DATA_SERVICE' || node.type === 'DATASET'), [nodes]);

  const handleNodeFocus = (nodeId?: DevelopmentId) => {
    setFocusedNodeId(nodeId);
    onNodeFocus(nodeId);
  };

  if (!selectedResource) return <EmptyDevelopmentWorkspace />;

  if (isDevelopmentTaskNode(selectedResource) || selectedResource.type === 'DATA_SERVICE' || selectedResource.type === 'DATASET') {
    return (
      <ResourceEditorBoundary resourceKey={selectedResource.id}>
        <DevelopmentWorkbench nodes={workbenchNodes} directories={directories} selectedNodeId={selectedResource.id} onNodeFocus={handleNodeFocus} onNodesChanged={onNodesChanged} />
      </ResourceEditorBoundary>
    );
  }

  return null;
}
