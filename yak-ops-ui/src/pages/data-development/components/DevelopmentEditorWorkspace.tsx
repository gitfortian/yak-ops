import { Button, Segmented } from 'antd';
import { GitFork, PanelTop } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { isDevelopmentTaskNode } from '../node-model';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentResourceNode,
} from '../types';
import DevelopmentDagCanvas from './dag/DevelopmentDagCanvas';
import DevelopmentWorkbench from './workbench/DevelopmentWorkbench';

interface DevelopmentEditorWorkspaceProps {
  nodes: DevelopmentResourceNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
  onNodesChanged?: () => void | Promise<void>;
}

type WorkspaceView = 'dag' | 'editor';

export default function DevelopmentEditorWorkspace({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
  onNodesChanged,
}: DevelopmentEditorWorkspaceProps) {
  const [view, setView] = useState<WorkspaceView>('dag');
  const selectedResource = useMemo(
    () => nodes.find((node) => node.id === selectedNodeId),
    [nodes, selectedNodeId],
  );
  const taskNodes = useMemo(() => nodes.filter(isDevelopmentTaskNode), [nodes]);
  const selectedTaskId = selectedResource && isDevelopmentTaskNode(selectedResource)
    ? selectedResource.id
    : undefined;

  useEffect(() => {
    if (!selectedResource) return;
    setView(isDevelopmentTaskNode(selectedResource) ? 'editor' : 'dag');
  }, [selectedResource]);

  const openEditor = (nodeId: DevelopmentId) => {
    onNodeFocus(nodeId);
    setView('editor');
  };

  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e4e7ec] bg-[#fafafa] px-2.5">
        <Segmented
          size="small"
          value={view}
          options={[
            {
              label: (
                <span className="inline-flex items-center gap-1.5">
                  <GitFork size={12} /> DAG 画布
                </span>
              ),
              value: 'dag',
            },
            {
              label: (
                <span className="inline-flex items-center gap-1.5">
                  <PanelTop size={12} /> 节点编辑
                </span>
              ),
              value: 'editor',
              disabled: !selectedTaskId,
            },
          ]}
          onChange={(value) => setView(value as WorkspaceView)}
        />
        {view === 'editor' && selectedTaskId ? (
          <Button
            type="text"
            size="small"
            icon={<GitFork size={12} />}
            onClick={() => setView('dag')}
            className="!text-[11px] !text-[#667085]"
          >
            返回 DAG
          </Button>
        ) : null}
      </div>

      {view === 'dag' ? (
        <DevelopmentDagCanvas
          resources={nodes}
          directories={directories}
          selectedNodeId={selectedNodeId}
          onNodeOpen={openEditor}
          onResourcesChanged={onNodesChanged}
        />
      ) : (
        <DevelopmentWorkbench
          nodes={taskNodes}
          directories={directories}
          selectedNodeId={selectedTaskId}
          onNodeFocus={onNodeFocus}
          onNodesChanged={onNodesChanged}
        />
      )}
    </main>
  );
}
