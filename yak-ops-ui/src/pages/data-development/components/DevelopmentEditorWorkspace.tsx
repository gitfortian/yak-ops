import { Boxes } from 'lucide-react';
import { useMemo } from 'react';

import { isDevelopmentTaskNode } from '../node-model';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentResourceNode,
} from '../types';
import DataServiceNodeEditor from './data-service/DataServiceNodeEditor';
import DatasetNodeEditor from './dataset/DatasetNodeEditor';
import DevelopmentWorkbench from './workbench/DevelopmentWorkbench';

interface DevelopmentEditorWorkspaceProps {
  nodes: DevelopmentResourceNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
  onNodesChanged?: () => void | Promise<void>;
}

export default function DevelopmentEditorWorkspace({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
  onNodesChanged,
}: DevelopmentEditorWorkspaceProps) {
  const selectedResource = useMemo(
    () => nodes.find((node) => node.id === selectedNodeId),
    [nodes, selectedNodeId],
  );
  const taskNodes = useMemo(() => nodes.filter(isDevelopmentTaskNode), [nodes]);

  if (!selectedResource) {
    return (
      <main className="flex min-w-0 flex-1 items-center justify-center overflow-hidden bg-white">
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-[#f5f5f6] text-[#98a2b3]">
            <Boxes size={19} />
          </div>
          <div className="text-[13px] font-medium text-[#475467]">选择一个开发节点</div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            SQL、Shell、数据集和数据服务都是独立节点；执行关系请在工作流模块配置。
          </div>
        </div>
      </main>
    );
  }

  if (selectedResource.type === 'DATASET') {
    return (
      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
        <DatasetNodeEditor node={selectedResource} onSaved={onNodesChanged} />
      </main>
    );
  }

  if (selectedResource.type === 'DATA_SERVICE') {
    return (
      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
        <DataServiceNodeEditor node={selectedResource} onSaved={onNodesChanged} />
      </main>
    );
  }

  if (isDevelopmentTaskNode(selectedResource)) {
    return (
      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
        <DevelopmentWorkbench
          nodes={taskNodes}
          directories={directories}
          selectedNodeId={selectedResource.id}
          onNodeFocus={onNodeFocus}
          onNodesChanged={onNodesChanged}
        />
      </main>
    );
  }

  return null;
}
