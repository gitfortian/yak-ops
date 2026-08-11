import { useEffect, useMemo, useState } from 'react';

import { getEditorDefinition } from '../../editors/registry';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNode,
} from '../../types';
import EditorHost from './EditorHost';
import EditorTabs, { type EditorTabAction } from './EditorTabs';
import EditorToolbar from './EditorToolbar';
import RightPanel from './RightPanel';
import RunResultPanel from './RunResultPanel';

interface DevelopmentWorkbenchProps {
  nodes: DevelopmentNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
}

const DevelopmentWorkbench = ({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
}: DevelopmentWorkbenchProps) => {
  const [openNodeIds, setOpenNodeIds] = useState<DevelopmentId[]>([]);
  const [activeNodeId, setActiveNodeId] = useState<DevelopmentId>();
  const [runPanelOpen, setRunPanelOpen] = useState(false);

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
    if (!nextActiveId) setRunPanelOpen(false);
  };

  const closeAllNodes = () => {
    setOpenNodeIds([]);
    setActiveNodeId(undefined);
    setRunPanelOpen(false);
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

  const definition = getEditorDefinition(activeNode.type);

  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <EditorTabs
        nodeMap={nodeMap}
        openNodeIds={openNodeIds}
        activeNodeId={activeNodeId}
        onFocus={focusNode}
        onClose={closeNode}
        onAction={handleTabAction}
      />

      <EditorToolbar
        node={activeNode}
        directory={activeDirectory}
        definition={definition}
        onRun={() => setRunPanelOpen(true)}
      />

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex min-h-0 flex-1 overflow-hidden">
          <EditorHost
            node={activeNode}
            directory={activeDirectory}
            definition={definition}
          />
          <RightPanel
            node={activeNode}
            directory={activeDirectory}
            definition={definition}
          />
        </div>

        <RunResultPanel
          open={runPanelOpen}
          node={activeNode}
          directory={activeDirectory}
          definition={definition}
          onClose={() => setRunPanelOpen(false)}
        />
      </div>
    </main>
  );
};

export default DevelopmentWorkbench;
