import { Button } from 'antd';
import { Boxes, Database, Plus, RefreshCw, Sparkles } from 'lucide-react';
import { Component, type ReactNode, useEffect, useMemo, useState } from 'react';

import { isDevelopmentTaskNode } from '../node-model';
import type { DevelopmentDirectory, DevelopmentId, DevelopmentResourceNode } from '../types';
import DevelopmentWorkbench from './workbench/DevelopmentWorkbench';

interface DevelopmentEditorWorkspaceProps {
  nodes: DevelopmentResourceNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
  onNodesChanged?: () => void | Promise<void>;
}

class ResourceEditorBoundary extends Component<{ resourceKey: DevelopmentId; children: ReactNode }, { error?: string }> {
  state = {};
  static getDerivedStateFromError(error: unknown) {
    return { error: error instanceof Error ? error.message : '资源编辑器发生未知错误' };
  }
  componentDidUpdate(previous: { resourceKey: DevelopmentId }) {
    if (previous.resourceKey !== this.props.resourceKey && this.state.error) this.setState({ error: undefined });
  }
  render() {
    if (!this.state.error) return this.props.children;
    return <div className="flex flex-1 items-center justify-center bg-white"><div className="text-center"><Boxes className="mx-auto text-[#98a2b3]" /><div className="mt-3 text-sm">资源编辑器加载异常</div><div className="mt-2 text-xs text-[#98a2b3]">{this.state.error}</div><Button className="mt-4" size="small" icon={<RefreshCw size={13} />} onClick={() => this.setState({ error: undefined })}>重新渲染</Button></div></div>;
  }
}

function EmptyDevelopmentWorkspace() {
  return (
    <main className="flex min-w-0 flex-1 items-center justify-center bg-white">
      <div className="w-[400px] text-center">
        <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-2xl bg-[#fff1f4] text-[#fe2c55]"><Sparkles size={20} /></div>
        <h2 className="mt-5 text-[16px] font-semibold text-[#161823]">开始你的数据开发</h2>
        <p className="mt-2 text-[12px] leading-5 text-[#8b93a6]">SQL、Python、Shell、数据集都可以在统一工作台中创建、编辑和调试。</p>
        <div className="mt-6 flex justify-center gap-3">
          <button className="flex h-9 items-center gap-2 rounded-lg bg-[#fe2c55] px-4 text-sm text-white"><Plus size={14}/>创建 SQL</button>
          <button className="flex h-9 items-center gap-2 rounded-lg border border-[#e4e7ec] px-4 text-sm text-[#344054]"><Database size={14}/>创建数据集</button>
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
  const handleNodeFocus = (nodeId?: DevelopmentId) => { setFocusedNodeId(nodeId); onNodeFocus(nodeId); };

  if (!selectedResource) return <EmptyDevelopmentWorkspace />;
  if (isDevelopmentTaskNode(selectedResource) || selectedResource.type === 'DATA_SERVICE' || selectedResource.type === 'DATASET') {
    return <ResourceEditorBoundary resourceKey={selectedResource.id}><DevelopmentWorkbench nodes={workbenchNodes} directories={directories} selectedNodeId={selectedResource.id} onNodeFocus={handleNodeFocus} onNodesChanged={onNodesChanged} /></ResourceEditorBoundary>;
  }
  return null;
}
