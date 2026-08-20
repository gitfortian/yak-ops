import { Button } from 'antd';
import { Boxes, RefreshCw } from 'lucide-react';
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

/** Prevents one resource editor from blanking the whole Data Development workspace. */
class ResourceEditorBoundary extends Component<
  ResourceEditorBoundaryProps,
  ResourceEditorBoundaryState
> {
  state: ResourceEditorBoundaryState = {};

  static getDerivedStateFromError(error: unknown): ResourceEditorBoundaryState {
    return {
      error: error instanceof Error ? error.message : '资源编辑器发生未知错误',
    };
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
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-[#f5f5f6] text-[#98a2b3]">
            <Boxes size={18} />
          </div>
          <div className="mt-3 text-[14px] font-semibold text-[#344054]">资源编辑器加载异常</div>
          <div className="mt-1 text-[12px] leading-5 text-[#98a2b3]">{this.state.error}</div>
          <Button
            className="mt-4"
            size="small"
            icon={<RefreshCw size={13} />}
            onClick={() => this.setState({ error: undefined })}
          >
            重新渲染
          </Button>
        </div>
      </div>
    );
  }
}

export default function DevelopmentEditorWorkspace({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
  onNodesChanged,
}: DevelopmentEditorWorkspaceProps) {
  const [focusedNodeId, setFocusedNodeId] = useState<DevelopmentId | undefined>(selectedNodeId);

  useEffect(() => {
    if (!selectedNodeId || !nodes.some((node) => node.id === selectedNodeId)) return;
    setFocusedNodeId(selectedNodeId);
  }, [nodes, selectedNodeId]);

  // Directory selection only changes the tree context. Keep the last focused
  // development node so the workbench (and its open tabs) stays mounted.
  const effectiveNodeId = selectedNodeId && nodes.some((node) => node.id === selectedNodeId)
    ? selectedNodeId
    : focusedNodeId;
  const selectedResource = useMemo(
    () => nodes.find((node) => node.id === effectiveNodeId),
    [effectiveNodeId, nodes],
  );
  const workbenchNodes = useMemo(
    () => nodes.filter((node) =>
      isDevelopmentTaskNode(node)
      || node.type === 'DATA_SERVICE'
      || node.type === 'DATASET'),
    [nodes],
  );

  const handleNodeFocus = (nodeId?: DevelopmentId) => {
    setFocusedNodeId(nodeId);
    onNodeFocus(nodeId);
  };

  if (!selectedResource) {
    return (
      <main className="flex min-w-0 flex-1 items-center justify-center overflow-hidden bg-white">
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-[#f5f5f6] text-[#98a2b3]">
            <Boxes size={19} />
          </div>
          <div className="text-[13px] font-medium text-[#475467]">选择一个开发节点</div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            SQL、Shell、数据集和数据服务都在统一开发工作台中打开。
          </div>
        </div>
      </main>
    );
  }

  if (
    isDevelopmentTaskNode(selectedResource)
    || selectedResource.type === 'DATA_SERVICE'
    || selectedResource.type === 'DATASET'
  ) {
    return (
      <ResourceEditorBoundary resourceKey={selectedResource.id}>
        <DevelopmentWorkbench
          nodes={workbenchNodes}
          directories={directories}
          selectedNodeId={selectedResource.id}
          onNodeFocus={handleNodeFocus}
          onNodesChanged={onNodesChanged}
        />
      </ResourceEditorBoundary>
    );
  }

  return null;
}
