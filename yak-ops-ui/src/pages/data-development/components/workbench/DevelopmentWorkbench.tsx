import { Button, Modal } from 'antd';
import { TriangleAlert } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { getEditorDefinition } from '../../editors/registry';
import {
  getEditorSession,
  markEditorSessionSaved,
  updateEditorSessionContent,
} from '../../editors/session/editorSessionStore';
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

interface PendingCloseRequest {
  nodeIds: DevelopmentId[];
  dirtyNodeIds: DevelopmentId[];
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
  const [pendingClose, setPendingClose] = useState<PendingCloseRequest>();

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

  const closeNodes = (nodeIds: DevelopmentId[]) => {
    if (!nodeIds.length) return;

    const closeSet = new Set(nodeIds);
    const currentIndex = activeNodeId ? openNodeIds.indexOf(activeNodeId) : -1;
    const next = openNodeIds.filter((id) => !closeSet.has(id));
    setOpenNodeIds(next);

    if (!activeNodeId || !closeSet.has(activeNodeId)) return;

    const nextActiveId =
      next[Math.min(Math.max(currentIndex, 0), next.length - 1)] ||
      next[next.length - 1];
    setActiveNodeId(nextActiveId);
    onNodeFocus(nextActiveId);
    if (!nextActiveId) setRunPanelOpen(false);
  };

  const requestCloseNodes = (nodeIds: DevelopmentId[]) => {
    const targetNodeIds = openNodeIds.filter((nodeId) => nodeIds.includes(nodeId));
    if (!targetNodeIds.length) return;

    const dirtyNodeIds = targetNodeIds.filter(
      (nodeId) => getEditorSession(nodeId)?.dirty,
    );

    if (!dirtyNodeIds.length) {
      closeNodes(targetNodeIds);
      return;
    }

    setPendingClose({
      nodeIds: targetNodeIds,
      dirtyNodeIds,
    });
  };

  const resolvePendingClose = (save: boolean) => {
    if (!pendingClose) return;

    pendingClose.dirtyNodeIds.forEach((nodeId) => {
      const session = getEditorSession(nodeId);
      if (!session?.dirty) return;

      if (save) {
        markEditorSessionSaved(nodeId);
        return;
      }

      updateEditorSessionContent(nodeId, session.originalContent);
    });

    const nodeIds = pendingClose.nodeIds;
    setPendingClose(undefined);
    closeNodes(nodeIds);
  };

  const handleTabAction = (action: EditorTabAction) => {
    if (!activeNodeId) return;
    const activeIndex = openNodeIds.indexOf(activeNodeId);
    if (activeIndex < 0) return;

    if (action === 'close-current') {
      requestCloseNodes([activeNodeId]);
      return;
    }
    if (action === 'close-all') {
      requestCloseNodes(openNodeIds);
      return;
    }
    if (action === 'close-others') {
      requestCloseNodes(openNodeIds.filter((nodeId) => nodeId !== activeNodeId));
      return;
    }
    if (action === 'close-left') {
      requestCloseNodes(openNodeIds.slice(0, activeIndex));
      return;
    }
    if (action === 'close-right') {
      requestCloseNodes(openNodeIds.slice(activeIndex + 1));
    }
  };

  const pendingDirtyNodes = pendingClose?.dirtyNodeIds
    .map((nodeId) => nodeMap.get(nodeId))
    .filter((node): node is DevelopmentNode => Boolean(node));
  const pendingDirtyCount = pendingDirtyNodes?.length || 0;
  const pendingDirtyName = pendingDirtyNodes?.[0]?.name || '当前编辑器';

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
        onClose={(nodeId) => requestCloseNodes([nodeId])}
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

      <Modal
        open={Boolean(pendingClose)}
        title={null}
        footer={null}
        width={520}
        centered
        maskClosable={false}
        onCancel={() => setPendingClose(undefined)}
      >
        <div className="flex gap-4 px-1 py-2">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center text-[#f79009]">
            <TriangleAlert size={36} strokeWidth={1.7} />
          </div>

          <div className="min-w-0 flex-1 pt-1">
            <div className="text-[16px] font-medium leading-6 text-[#1f2937]">
              {pendingDirtyCount <= 1 ? (
                <>
                  是否要保存对 <span className="font-semibold">{pendingDirtyName}</span>{' '}
                  的更改？
                </>
              ) : (
                <>是否要保存 {pendingDirtyCount} 个已修改编辑器的更改？</>
              )}
            </div>

            <div className="mt-4 text-[13px] leading-5 text-[#475467]">
              如果不保存，{pendingDirtyCount <= 1 ? '你的更改' : '这些更改'}将丢失。
            </div>

            {pendingDirtyCount > 1 ? (
              <div
                className="mt-2 max-w-[360px] truncate text-[12px] text-[#98a2b3]"
                title={pendingDirtyNodes?.map((node) => node.name).join('、')}
              >
                {pendingDirtyNodes?.map((node) => node.name).join('、')}
              </div>
            ) : null}

            <div className="mt-6 flex justify-end gap-2">
              <Button type="primary" onClick={() => resolvePendingClose(true)}>
                {pendingDirtyCount > 1 ? '保存全部' : '保存'}
              </Button>
              <Button onClick={() => resolvePendingClose(false)}>
                {pendingDirtyCount > 1 ? '全部不保存' : '不保存'}
              </Button>
              <Button onClick={() => setPendingClose(undefined)}>取消</Button>
            </div>
          </div>
        </div>
      </Modal>
    </main>
  );
};

export default DevelopmentWorkbench;
