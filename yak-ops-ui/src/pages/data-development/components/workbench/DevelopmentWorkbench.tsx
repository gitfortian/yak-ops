import { API_SUCCESS_CODE } from '@/services/http/response';
import { Button, Modal, message } from 'antd';
import { TriangleAlert } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { getEditorDefinition } from '../../editors/registry';
import {
  getEditorSession,
  markEditorSessionSaved,
} from '../../editors/session/editorSessionStore';
import {
  hydrateDevelopmentTaskDraft,
  prepareDevelopmentTaskDefinition,
  restoreDevelopmentTaskOriginal,
} from '../../editors/taskPersistence';
import {
  getDevelopmentTaskDraft,
  publishDevelopmentTask,
  runDevelopmentTask,
  saveDevelopmentTaskDraft,
} from '../../service';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNode,
  DevelopmentTaskDraft,
  DevelopmentTaskRunResult,
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
  onNodesChanged?: () => void | Promise<void>;
}

interface PendingCloseRequest {
  nodeIds: DevelopmentId[];
  dirtyNodeIds: DevelopmentId[];
}

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const DevelopmentWorkbench = ({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
  onNodesChanged,
}: DevelopmentWorkbenchProps) => {
  const [openNodeIds, setOpenNodeIds] = useState<DevelopmentId[]>([]);
  const [activeNodeId, setActiveNodeId] = useState<DevelopmentId>();
  const [runPanelOpen, setRunPanelOpen] = useState(false);
  const [runResults, setRunResults] = useState<
    Partial<Record<DevelopmentId, DevelopmentTaskRunResult>>
  >({});
  const [runningNodeIds, setRunningNodeIds] = useState<DevelopmentId[]>([]);
  const [pendingClose, setPendingClose] = useState<PendingCloseRequest>();
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [closeSaving, setCloseSaving] = useState(false);
  const [versionsRefreshKey, setVersionsRefreshKey] = useState(0);

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
    const node = nodeMap.get(activeNodeId);
    if (!node) return;

    let active = true;
    getDevelopmentTaskDraft(node.id)
      .then((response) => {
        if (!active) return;
        const draft = responseData(response, '加载任务草稿失败');
        hydrateDevelopmentTaskDraft(node, draft);
      })
      .catch((error) => {
        if (!active) return;
        message.error(error instanceof Error ? error.message : '加载任务草稿失败');
      });

    return () => {
      active = false;
    };
  }, [activeNodeId, nodeMap]);

  const activeNode = activeNodeId ? nodeMap.get(activeNodeId) : undefined;
  const activeDirectory = activeNode?.directoryId
    ? directoryMap.get(activeNode.directoryId)
    : undefined;
  const activeRunning = activeNode
    ? runningNodeIds.includes(activeNode.id)
    : false;

  const focusNode = (nodeId: DevelopmentId) => {
    setActiveNodeId(nodeId);
    onNodeFocus(nodeId);
  };

  const persistDraft = async (nodeId: DevelopmentId): Promise<DevelopmentTaskDraft> => {
    const node = nodeMap.get(nodeId);
    if (!node) throw new Error(`节点不存在：${nodeId}`);

    const definition = prepareDevelopmentTaskDefinition(node);
    const session = getEditorSession(nodeId);
    const draft = responseData(
      await saveDevelopmentTaskDraft(nodeId, {
        ...definition,
        baseRevision: session?.draftRevision || 0,
      }),
      '保存草稿失败',
    );

    markEditorSessionSaved(nodeId, draft.draftRevision);
    await onNodesChanged?.();
    return draft;
  };

  const saveActiveDraft = async () => {
    if (!activeNode) return;
    setSaving(true);
    try {
      const draft = await persistDraft(activeNode.id);
      message.success(`草稿已保存 · Draft #${draft.draftRevision}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存草稿失败');
    } finally {
      setSaving(false);
    }
  };

  const runActiveTask = async () => {
    if (!activeNode || runningNodeIds.includes(activeNode.id)) return;
    const node = activeNode;
    const startedAt = Date.now();
    setRunPanelOpen(true);
    setRunningNodeIds((current) => [...current, node.id]);
    setRunResults((current) => ({
      ...current,
      [node.id]: {
        status: 'RUNNING',
        message: '',
        durationMs: 0,
        output: {},
      },
    }));

    try {
      const definition = prepareDevelopmentTaskDefinition(node);
      const result = responseData(
        await runDevelopmentTask(node.id, definition),
        '运行任务失败',
      );
      setRunResults((current) => ({ ...current, [node.id]: result }));
      if (result.status === 'FAILED' || result.status === 'TIMEOUT') {
        message.error(result.message || 'SQL 执行失败');
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '运行任务失败';
      setRunResults((current) => ({
        ...current,
        [node.id]: {
          status: 'FAILED',
          message: errorMessage,
          durationMs: Math.max(0, Date.now() - startedAt),
          output: {},
        },
      }));
      message.error(errorMessage);
    } finally {
      setRunningNodeIds((current) => current.filter((nodeId) => nodeId !== node.id));
    }
  };

  const publishActiveTask = async () => {
    if (!activeNode) return;
    setPublishing(true);
    try {
      prepareDevelopmentTaskDefinition(activeNode);
      let session = getEditorSession(activeNode.id);
      if (!session || session.dirty || !session.draftRevision) {
        await persistDraft(activeNode.id);
        session = getEditorSession(activeNode.id);
      }
      if (!session?.draftRevision) {
        throw new Error('发布前请先保存草稿');
      }

      const published = responseData(
        await publishDevelopmentTask(activeNode.id, session.draftRevision),
        '发布任务失败',
      );
      setVersionsRefreshKey((current) => current + 1);
      message.success(`已发布 v${published.revisionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布任务失败');
    } finally {
      setPublishing(false);
    }
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

  const resolvePendingClose = async (save: boolean) => {
    if (!pendingClose) return;

    if (save) {
      setCloseSaving(true);
      try {
        for (const nodeId of pendingClose.dirtyNodeIds) {
          if (getEditorSession(nodeId)?.dirty) {
            await persistDraft(nodeId);
          }
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '保存草稿失败');
        setCloseSaving(false);
        return;
      }
      setCloseSaving(false);
    } else {
      pendingClose.dirtyNodeIds.forEach((nodeId) => {
        const node = nodeMap.get(nodeId);
        if (node) restoreDevelopmentTaskOriginal(node);
      });
    }

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
        onRun={() => void runActiveTask()}
        onSave={() => void saveActiveDraft()}
        onPublish={() => void publishActiveTask()}
        running={activeRunning}
        saving={saving}
        publishing={publishing}
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
            versionsRefreshKey={versionsRefreshKey}
          />
        </div>

        <RunResultPanel
          open={runPanelOpen}
          node={activeNode}
          directory={activeDirectory}
          definition={definition}
          result={runResults[activeNode.id]}
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
        closable={!closeSaving}
        onCancel={() => {
          if (!closeSaving) setPendingClose(undefined);
        }}
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
              <Button
                type="primary"
                loading={closeSaving}
                onClick={() => void resolvePendingClose(true)}
              >
                {pendingDirtyCount > 1 ? '保存全部' : '保存'}
              </Button>
              <Button
                disabled={closeSaving}
                onClick={() => void resolvePendingClose(false)}
              >
                {pendingDirtyCount > 1 ? '全部不保存' : '不保存'}
              </Button>
              <Button
                disabled={closeSaving}
                onClick={() => setPendingClose(undefined)}
              >
                取消
              </Button>
            </div>
          </div>
        </div>
      </Modal>
    </main>
  );
};

export default DevelopmentWorkbench;
